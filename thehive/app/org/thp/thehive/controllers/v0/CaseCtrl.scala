package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{RichSeq, _}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputCase, InputTask}
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.{JsArray, JsNumber, JsObject}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class CaseCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    tagSrv: TagSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    @Named("with-thehive-schema") implicit val db: Database
) extends QueryableCtrl
    with CaseRenderer {

  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "case"
  override val publicProperties: List[PublicProperty[_, _]] = properties.`case`
  override val initialQuery: Query =
    Query.init[Traversal.V[Case]]("listCase", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).cases)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[Case]](
    "getCase",
    FieldsParser[IdOrName],
    (param, graph, authContext) => caseSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Case], IteratorOutput](
    "page",
    FieldsParser[OutputParam], {
      case (OutputParam(from, to, withStats, _), caseSteps, authContext) =>
        caseSteps
          .richPage(from, to, withTotal = true) {
            case c if withStats =>
              c.richCaseWithCustomRenderer(caseStatsRenderer(authContext))(authContext)
            case c =>
              c.richCase(authContext).domainMap(_ -> JsObject.empty)
          }
    }
  )
  override val outputQuery: Query = Query.outputWithContext[RichCase, Traversal.V[Case]]((caseSteps, authContext) => caseSteps.richCase(authContext))
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Case], Traversal.V[Observable]]("observables", (caseSteps, authContext) => caseSteps.observables(authContext)),
    Query[Traversal.V[Case], Traversal.V[Task]]("tasks", (caseSteps, authContext) => caseSteps.tasks(authContext))
  )

  def create: Action[AnyContent] =
    entrypoint("create case")
      .extract("case", FieldsParser[InputCase])
      .extract("tasks", FieldsParser[InputTask].sequence.on("tasks"))
      .extract("caseTemplate", FieldsParser[String].optional.on("template"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String] = request.body("caseTemplate")
        val inputCase: InputCase             = request.body("case")
        val inputTasks: Seq[InputTask]       = request.body("tasks")
        val customFields                     = inputCase.customFields.map(c => (c.name, c.value, c.order))
        for {
          organisation <- userSrv
            .current
            .organisations(Permissions.manageCase)
            .get(request.organisation)
            .orFail(AuthorizationError("Operation not permitted"))
          caseTemplate <- caseTemplateName.map(caseTemplateSrv.get(_).visible.richCaseTemplate.getOrFail("CaseTemplate")).flip
          user         <- inputCase.user.map(userSrv.get(_).visible.getOrFail("User")).flip
          tags         <- inputCase.tags.toTry(tagSrv.getOrCreate)
          tasks        <- inputTasks.toTry(t => t.owner.map(userSrv.getOrFail).flip.map(owner => t.toTask -> owner))
          richCase <- caseSrv.create(
            caseTemplate.fold(inputCase)(inputCase.withCaseTemplate).toCase,
            user,
            organisation,
            tags.toSet,
            customFields,
            caseTemplate,
            tasks
          )
        } yield Results.Created(richCase.toJson)
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("get case")
      .extract("stats", FieldsParser.boolean.optional.on("nstats"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val c = caseSrv
          .get(caseIdOrNumber)
          .visible
        val stats: Option[Boolean] = request.body("stats")
        if (stats.contains(true)) {
          c.richCaseWithCustomRenderer(caseStatsRenderer(request))
            .getOrFail("Case")
            .map {
              case (richCase, stats) => Results.Ok(richCase.toJson.as[JsObject] + ("stats" -> stats))
            }
        } else {
          c.richCase
            .getOrFail("Case")
            .map(richCase => Results.Ok(richCase.toJson))
        }
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("update case")
      .extract("case", FieldsParser.update("case", properties.`case`))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("case")
        caseSrv
          .update(
            _.get(caseIdOrNumber)
              .can(Permissions.manageCase),
            propertyUpdaters
          )
          .flatMap {
            case (caseSteps, _) =>
              caseSteps
                .richCase
                .getOrFail("Case")
                .map(richCase => Results.Ok(richCase.toJson))
          }
      }

  def bulkUpdate: Action[AnyContent] =
    entrypoint("update case")
      .extract("case", FieldsParser.update("case", properties.`case`))
      .extract("idsOrNumbers", FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("case")
        val idsOrNumbers: Seq[String]              = request.body("idsOrNumbers")
        idsOrNumbers
          .toTry { caseIdOrNumber =>
            caseSrv
              .update(
                _.get(caseIdOrNumber).can(Permissions.manageCase),
                propertyUpdaters
              )
              .flatMap {
                case (caseSteps, _) =>
                  caseSteps
                    .richCase
                    .getOrFail("Case")
              }
          }
          .map(richCases => Results.Ok(richCases.toJson))
      }

  def delete(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("delete case")
      .authTransaction(db) { implicit request => implicit graph =>
        caseSrv
          .get(caseIdOrNumber)
          .can(Permissions.manageCase)
          .update(_.status, CaseStatus.Deleted)
          .getOrFail("Case")
          .map(_ => Results.NoContent)
      }

  def realDelete(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("delete case")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          c <- caseSrv
            .get(caseIdOrNumber)
            .can(Permissions.manageCase)
            .getOrFail("Case")
          _ <- caseSrv.remove(c)
        } yield Results.NoContent
      }

  def merge(caseIdsOrNumbers: String): Action[AnyContent] =
    entrypoint("merge cases")
      .authTransaction(db) { implicit request => implicit graph =>
        caseIdsOrNumbers
          .split(',')
          .toSeq
          .toTry(
            caseSrv
              .get(_)
              .visible
              .getOrFail("Case")
          )
          .map { cases =>
            val mergedCase = caseSrv.merge(cases)
            Results.Ok(mergedCase.toJson)
          }
      }

  def linkedCases(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("case link")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val relatedCases = caseSrv
          .get(caseIdOrNumber)
          .visible
          .linkedCases
          .map {
            case (c, o) =>
              c.toJson.as[JsObject] +
                ("linkedWith" -> o.toJson) +
                ("linksCount" -> JsNumber(o.size))
          }

        Success(Results.Ok(JsArray(relatedCases)))
      }
}
