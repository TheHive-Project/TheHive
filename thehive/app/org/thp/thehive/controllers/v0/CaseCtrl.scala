package org.thp.thehive.controllers.v0

import scala.util.{Failure, Success, Try}

import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.{Database, Entity, ResultWithTotalSize}
import org.thp.scalligraph.query.Query
import org.thp.scalligraph.{AuthorizationError, RichSeq}
import org.thp.thehive.dto.v0.InputCase
import org.thp.thehive.models.{RichCaseTemplate, User}
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
    val queryExecutor: TheHiveQueryExecutor)
    extends QueryCtrl
    with CaseConversion {

  lazy val logger = Logger(getClass)

  def create: Action[AnyContent] =
    entryPoint("create case")
      .extract('case, FieldsParser[InputCase])
      .extract('caseTemplate, FieldsParser[String].optional.on("caseTemplate"))
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val caseTemplateName: Option[String] = request.body('caseTemplate)
          for {
            caseTemplate ← caseTemplateName
              .fold[Try[Option[RichCaseTemplate]]](Success(None)) { templateName ⇒
                caseTemplateSrv
                  .get(templateName)
                  .available
                  .richCaseTemplate
                  .getOrFail()
                  .map(Some.apply)
              }
            inputCase: InputCase = request.body('case)
            case0                = fromInputCase(inputCase, caseTemplate)
            user         ← inputCase.user.fold[Try[Option[User with Entity]]](Success(None))(u ⇒ userSrv.getOrFail(u).map(Some.apply))
            organisation ← organisationSrv.getOrFail(request.organisation)
            customFields = inputCase.customFieldValue.map(fromInputCustomField).toMap
            richCase ← caseSrv.create(case0, user, organisation, customFields, caseTemplate)

            _ = caseTemplate.foreach { ct ⇒
              caseTemplateSrv.get(ct.caseTemplate).tasks.toList().foreach { task ⇒
                taskSrv.create(task, richCase.`case`)
              }
            }
          } yield Results.Created(richCase.toJson)
        }
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("get case")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
            caseSrv
              .get(caseIdOrNumber)
              .visible
              .richCase
              .getOrFail()
          }
          .map(richCase ⇒ Results.Ok(richCase.toJson))
      }

  def list: Action[AnyContent] =
    entryPoint("list case")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val cases = caseSrv.initSteps.visible.richCase
            .map(_.toJson)
            .toList()
          Success(Results.Ok(Json.toJson(cases)))
        }
      }

  def search: Action[AnyContent] =
    entryPoint("search case")
      .extract('query, searchParser("listCase"))
      .authenticated { implicit request ⇒
        val query: Query = request.body('query)
        val result = db.transaction { graph ⇒
          queryExecutor.execute(query, graph, request.authContext)
        }
        val resp = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case ResultWithTotalSize(_, size) ⇒ Success(resp.withHeaders("X-Total" → size.toString))
          case _                            ⇒ Success(resp)
        }
      }

  def stats: Action[AnyContent] =
    entryPoint("case stats")
      .extract('query, statsParser("listCase"))
      .authenticated { implicit request ⇒
        val queries: Seq[Query] = request.body('query)
        val results = queries
          .map { query ⇒
            db.transaction { graph ⇒
              queryExecutor.execute(query, graph, request.authContext).toJson
            }
          }
          .foldLeft(JsObject.empty) {
            case (acc, o: JsObject) ⇒ acc ++ o
            case (acc, r) ⇒
              logger.warn(s"Invalid stats result: $r")
              acc
          }
        Success(Results.Ok(results))
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("update case")
      .extract('case, UpdateFieldsParser[InputCase])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          if (caseSrv.isAvailable(caseIdOrNumber)) {
            caseSrv.update(caseIdOrNumber, caseProperties(db), request.body('case))
            Success(Results.NoContent)
          } else Failure(AuthorizationError(s"Case $caseIdOrNumber doesn't exist or permission is insufficient"))
        }
      }

  def delete(caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("delete case")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          if (caseSrv.isAvailable(caseIdOrNumber)) {
            caseSrv.update(caseIdOrNumber, "status", "deleted")
            Success(Results.NoContent)
          } else Failure(AuthorizationError(s"Case $caseIdOrNumber doesn't exist or permission is insufficient"))
        }
      }

  def merge(caseIdsOrNumbers: String): Action[AnyContent] =
    entryPoint("merge cases")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          caseIdsOrNumbers
            .split(',')
            .toSeq
            .toTry(
              caseSrv
                .get(_)
                .visible
                .getOrFail())
            .map { cases ⇒
              val mergedCase = caseSrv.merge(cases)
              Results.Ok(mergedCase.toJson)
            }
        }
      }

  def linkedCases(caseIdsOrNumber: String): Action[AnyContent] =
    entryPoint("case link")
      .authenticated { _ ⇒
        db.tryTransaction { implicit graph ⇒
          val relatedCases = caseSrv
            .get(caseIdsOrNumber)
            .linkedCases
            .richCase
            .map(_.toJson)
            .toList()
          Success(Results.Ok(Json.toJson(relatedCases)))
        }
      }
}
