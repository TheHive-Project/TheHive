package org.thp.thehive.controllers.v0

import scala.util.{Success, Try}

import play.api.Logger
import play.api.libs.json.{JsArray, JsNumber, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputCase, InputTask}
import org.thp.thehive.models._
import org.thp.thehive.services._

@Singleton
class CaseCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    taskSrv: TaskSrv,
    tagSrv: TagSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl
    with CaseRenderer {

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "case"
  override val publicProperties: List[PublicProperty[_, _]] = properties.`case` ::: metaProperties[CaseSteps]
  override val initialQuery: Query =
    Query.init[CaseSteps]("listCase", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).cases)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, CaseSteps](
    "getCase",
    FieldsParser[IdOrName],
    (param, graph, authContext) => caseSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, CaseSteps, PagedResult[(RichCase, JsObject)]](
    "page",
    FieldsParser[OutputParam], {
      case (OutputParam(from, to, withStats), caseSteps, authContext) =>
        caseSteps
          .richPage(from, to, withTotal = true) {
            case c if withStats =>
              c.richCaseWithCustomRenderer(caseStatsRenderer(authContext, db, caseSteps.graph))(authContext)
            case c =>
              c.richCase(authContext).map(_ -> JsObject.empty)
          }
    }
  )
  override val outputQuery: Query = Query.output[(RichCase, JsObject)]()
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[CaseSteps, List[RichCase]]("toList", (caseSteps, authContext) => caseSteps.richCase(authContext).toList),
    Query.withParam[IdOrName, CaseSteps, CaseSteps]("getCase", FieldsParser[IdOrName], (idOrName, caseSteps, _) => caseSteps.get(idOrName.idOrName)),
    Query[CaseSteps, ObservableSteps]("observables", (caseSteps, authContext) => caseSteps.observables(authContext)),
    Query[CaseSteps, TaskSteps]("tasks", (caseSteps, authContext) => caseSteps.tasks(authContext))
  )

  def create: Action[AnyContent] =
    entryPoint("create case")
      .extract("case", FieldsParser[InputCase])
      .extract("tasks", FieldsParser[InputTask].sequence.on("tasks"))
      .extract("caseTemplate", FieldsParser[String].optional.on("template"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String] = request.body("caseTemplate")
        val inputCase: InputCase             = request.body("case")
        val inputTasks: Seq[InputTask]       = request.body("tasks")
        for {
          caseTemplate <- caseTemplateName
            .fold[Try[Option[RichCaseTemplate]]](Success(None)) { templateName =>
              caseTemplateSrv
                .get(templateName)
                .visible
                .richCaseTemplate
                .getOrFail()
                .map(Some.apply)
            }
          customFields = inputCase.customFields.map(c => c.name -> c.value).toMap
          organisation <- userSrv.current.organisations(Permissions.manageCase).get(request.organisation).getOrFail()
          user         <- inputCase.user.fold[Try[Option[User with Entity]]](Success(None))(u => userSrv.getOrFail(u).map(Some.apply))
          tags         <- inputCase.tags.toTry(tagSrv.getOrCreate)
          richCase <- caseSrv.create(
            caseTemplate.fold(inputCase)(inputCase.withCaseTemplate).toCase,
            user,
            organisation,
            tags.toSet,
            customFields,
            caseTemplate,
            inputTasks.map(_.toTask)
          )
        } yield Results.Created(richCase.toJson)
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("get case")
      .extract("stats", FieldsParser.boolean.optional.on("nstats"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val c = caseSrv
          .get(caseIdOrNumber)
          .visible
        if (request.body("stats").contains(true)) {
          c.richCaseWithCustomRenderer(caseStatsRenderer(request, db, graph))
            .getOrFail()
            .map {
              case (richCase, stats) => Results.Ok(richCase.toJson.as[JsObject] + ("stats" -> stats))
            }
        } else {
          c.richCase
            .getOrFail()
            .map(richCase => Results.Ok(richCase.toJson))
        }
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("update case")
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
                .getOrFail()
                .map(richCase => Results.Ok(richCase.toJson))
          }
      }

  def bulkUpdate: Action[AnyContent] =
    entryPoint("update case")
      .extract("case", FieldsParser.update("case", properties.`case`))
      .extract("idsOrNumbers", FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("case")
        val idsOrNumbers: Seq[String]              = request.body("idsOrNumbers")
        val updatedCases = idsOrNumbers.map(
          caseIdOrNumber =>
            caseSrv
              .update(
                _.get(caseIdOrNumber).can(Permissions.manageCase),
                propertyUpdaters
              )
              .flatMap {
                case (caseSteps, _) =>
                  caseSteps
                    .richCase
                    .getOrFail()
              }
        )

        Try(updatedCases.map(_.get))
          .map(r => Results.Ok(Json.toJson(r.map(_.toJson))))
      }

  def delete(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("delete case")
      .authTransaction(db) { implicit request => implicit graph =>
        caseSrv
          .get(caseIdOrNumber)
          .can(Permissions.manageCase)
          .update("status" -> "deleted")
          .map(_ => Results.NoContent)
      }

  def realDelete(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("delete case")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          c <- caseSrv
            .get(caseIdOrNumber)
            .can(Permissions.manageCase)
            .getOrFail()
          _ <- caseSrv.cascadeRemove(c)
        } yield Results.NoContent
      }

  def merge(caseIdsOrNumbers: String): Action[AnyContent] =
    entryPoint("merge cases")
      .authTransaction(db) { implicit request => implicit graph =>
        caseIdsOrNumbers
          .split(',')
          .toSeq
          .toTry(
            caseSrv
              .get(_)
              .visible
              .getOrFail()
          )
          .map { cases =>
            val mergedCase = caseSrv.merge(cases)
            Results.Ok(mergedCase.toJson)
          }
      }

  def linkedCases(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("case link")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val relatedCases = caseSrv
          .get(caseIdOrNumber)
          .visible
          .linkedCases
          .map {
            case (c, o) =>
              c.toJson.as[JsObject] +
                ("linkedWith" -> JsArray(o.map(_.toJson))) +
                ("linksCount" -> JsNumber(o.size))
          }
          .toSeq
        Success(Results.Ok(JsArray(relatedCases)))
      }
}
