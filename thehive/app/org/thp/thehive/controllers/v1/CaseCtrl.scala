package org.thp.thehive.controllers.v1

import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.{NotFoundError, RichOptionTry, RichSeq}
import org.thp.thehive.dto.v1.{InputCase, OutputCase}
import org.thp.thehive.models.{Permissions, RichCase}
import org.thp.thehive.services._

@Singleton
class CaseCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    auditSrv: AuditSrv
) extends QueryableCtrl {
  import CaseConversion._
  import CustomFieldConversion._

  override val entityName: String                           = "case"
  override val publicProperties: List[PublicProperty[_, _]] = caseProperties(caseSrv, userSrv) ::: metaProperties[CaseSteps]
  override val initialQuery: Query =
    Query.init[CaseSteps]("listCase", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).cases)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, CaseSteps](
    "getCase",
    FieldsParser[IdOrName],
    (param, graph, authContext) => caseSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, CaseSteps, PagedResult[RichCase]](
    "page",
    FieldsParser[OutputParam], {
      case (OutputParam(from, to, withStats), caseSteps, authContext) =>
        caseSteps
          .richPage(from, to, withTotal = true) { c =>
            c.richCase(authContext).raw
//            case c if withStats =>
////              c.richCaseWithCustomRenderer(caseStatsRenderer(authContext, db, caseSteps.graph)).raw
//              c.richCase.raw.map(_ -> JsObject.empty) // TODO add stats
//            case c =>
//              c.richCase.raw.map(_ -> JsObject.empty)
          }
    }
  )
  override val outputQuery: Query = Query.output[RichCase, OutputCase]
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[CaseSteps, TaskSteps]("tasks", (caseSteps, authContext) => caseSteps.tasks(authContext)),
    Query[CaseSteps, List[RichCase]]("toList", (caseSteps, authContext) => caseSteps.richCase(authContext).toList)
  )

  def create: Action[AnyContent] =
    entryPoint("create case")
      .extract("case", FieldsParser[InputCase])
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String] = request.body("caseTemplate")
        val inputCase: InputCase             = request.body("case")
        for {
          organisation <- userSrv
            .current
            .organisations(Permissions.manageCase)
            .get(request.organisation)
            .getOrFail()
          caseTemplate <- caseTemplateName.map { templateName =>
            caseTemplateSrv
              .get(templateName)
              .visible
              .richCaseTemplate
              .getOrFail()
          }.flip
          case0 = fromInputCase(inputCase, caseTemplate)
          user <- inputCase
            .user
            .map { u =>
              userSrv
                .current
                .organisations
                .users(Permissions.manageCase)
                .get(u)
                .orFail(NotFoundError(s"User $u doesn't exist or permission is insufficient"))
            }
            .flip
          customFields = inputCase.customFieldValue.map(fromInputCustomField).toMap
          richCase <- caseSrv.create(case0, user, organisation, inputCase.tags, customFields, caseTemplate, Nil)
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

//  def list: Action[AnyContent] =
//    entryPoint("list case")
//      .authRoTransaction(db) { implicit request ⇒ implicit graph ⇒
//        val cases = userSrv.current.organisations.cases.richCase
//          .map(_.toJson)
//          .toList
//        Success(Results.Ok(Json.toJson(cases)))
//      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("update case")
      .extract("case", FieldsParser.update("case", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("case")
        caseSrv
          .update(_.get(caseIdOrNumber).can(Permissions.manageCase), propertyUpdaters)
          .map(_ => Results.NoContent)
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
}
