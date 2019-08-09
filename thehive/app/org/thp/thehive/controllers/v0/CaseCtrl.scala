package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.dto.v0.{InputCase, InputTask, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.{JsArray, JsNumber, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Success, Try}

@Singleton
class CaseCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl {
  import CaseConversion._
  import CustomFieldConversion._
  import ObservableConversion._
  import TaskConversion._

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "case"
  override val publicProperties: List[PublicProperty[_, _]] = caseProperties(caseSrv, userSrv) ::: metaProperties[CaseSteps]
  override val initialQuery: Query =
    Query.init[CaseSteps]("listCase", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).cases)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, CaseSteps, PagedResult[(RichCase, JsObject)]](
    "page",
    FieldsParser[OutputParam], {
      case (OutputParam(from, to, withStats), caseSteps, authContext) =>
        caseSteps
          .richPage(from, to, withTotal = true) {
            case c if withStats =>
              c.richCaseWithCustomRenderer(caseStatsRenderer(authContext, db, caseSteps.graph)).raw
            case c =>
              c.richCase.raw.map(_ -> JsObject.empty)
          }
    }
  )
  override val outputQuery: Query = Query.output[(RichCase, JsObject), OutputCase]

  def create: Action[AnyContent] =
    entryPoint("create case")
      .extract("case", FieldsParser[InputCase])
      .extract("tasks", FieldsParser[InputTask].sequence.on("tasks"))
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
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
          customFields = inputCase.customFieldValue.map(fromInputCustomField).toMap
          case0        = fromInputCase(inputCase, caseTemplate)
          organisation <- userSrv.current.organisations(Permissions.manageCase).get(request.organisation).getOrFail()
          user         <- inputCase.user.fold[Try[Option[User with Entity]]](Success(None))(u => userSrv.getOrFail(u).map(Some.apply))
          richCase     <- caseSrv.create(case0, user, organisation, inputCase.tags, customFields, caseTemplate, inputTasks.map(fromInputTask))
        } yield Results.Created(richCase.toJson)
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("get case")
      .authRoTransaction(db) { implicit request => implicit graph =>
        caseSrv
          .get(caseIdOrNumber)
          .visible
          .richCase
          .getOrFail()
          .map(richCase => Results.Ok(richCase.toJson))
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("update case")
      .extract("case", FieldsParser.update("case", caseProperties(caseSrv, userSrv)))
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
      .extract("case", FieldsParser.update("case", caseProperties(caseSrv, userSrv)))
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
