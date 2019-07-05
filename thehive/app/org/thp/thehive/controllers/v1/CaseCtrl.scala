package org.thp.thehive.controllers.v1

import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.{NotFoundError, RichOptionTry, RichSeq}
import org.thp.thehive.dto.v1.InputCase
import org.thp.thehive.models.Permissions
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
) extends CaseConversion {

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
//            organisation <- organisationSrv.getOrFail(request.organisation)
          customFieldsCaseTemplate = caseTemplate.fold(Map.empty[String, Option[Any]])(_.customFields.map(cf => cf.name -> cf.value).toMap)
          customFields             = customFieldsCaseTemplate ++ inputCase.customFieldValue.map(fromInputCustomField).toMap
          richCase <- caseSrv.create(case0, user, organisation, customFields, caseTemplate)

          _ = caseTemplate.map { ct =>
            caseTemplateSrv
              .get(ct.caseTemplate)
              .tasks
              .toList
              .toTry(task => taskSrv.create(task, richCase.`case`))
          }.flip
        } yield Results.Created(richCase.toJson)
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("get case")
      .authTransaction(db) { implicit request => implicit graph =>
        caseSrv
          .get(caseIdOrNumber)
          .visible
          .richCase
          .getOrFail()
          .map(richCase => Results.Ok(richCase.toJson))
      }

//  def list: Action[AnyContent] =
//    entryPoint("list case")
//      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
//        val cases = userSrv.current.organisations.cases.richCase
//          .map(_.toJson)
//          .toList
//        Success(Results.Ok(Json.toJson(cases)))
//      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("update case")
      .extract("case", FieldsParser.update("case", caseProperties))
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
