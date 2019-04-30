package org.thp.thehive.controllers.v0

import scala.util.{Success, Try}

import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{PropertyUpdater, Query}
import org.thp.thehive.dto.v0.InputAlert
import org.thp.thehive.models.{Permissions, RichCaseTemplate}
import org.thp.thehive.services.{AlertSrv, CaseTemplateSrv, UserSrv}

@Singleton
class AlertCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    alertSrv: AlertSrv,
    caseTemplateSrv: CaseTemplateSrv,
    userSrv: UserSrv,
    errorHandler: HttpErrorHandler,
    val queryExecutor: TheHiveQueryExecutor)
    extends QueryCtrl
    with CaseConversion
    with CustomFieldConversion
    with AlertConversion {

  lazy val logger = Logger(getClass)

  def create: Action[AnyContent] =
    entryPoint("create alert")
      .extract('alert, FieldsParser[InputAlert])
      .extract('caseTemplate, FieldsParser[String].optional.on("caseTemplate"))
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val caseTemplateName: Option[String] = request.body('caseTemplate)
          for {
            caseTemplate ← caseTemplateName.fold[Try[Option[RichCaseTemplate]]](Success(None)) { ct ⇒
              caseTemplateSrv
                .get(ct)
                .visible
                .richCaseTemplate
                .getOrFail()
                .map(Some(_))
            }
            inputAlert: InputAlert = request.body('alert)
            user         ← userSrv.getOrFail(request.userId)
            organisation ← userSrv.getOrganisation(user)
            customFields = inputAlert.customFieldValue.map(fromInputCustomField).toMap
            richAlert ← alertSrv.create(request.body('alert), organisation, customFields, caseTemplate)
          } yield Results.Created(richAlert.toJson)
        }
      }

  def get(alertId: String): Action[AnyContent] =
    entryPoint("get alert")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .visible
            .richAlert
            .getOrFail()
            .map(alert ⇒ Results.Ok(alert.toJson))
        }
      }

  def list: Action[AnyContent] =
    entryPoint("list alert")
      .authenticated { implicit request ⇒
        val alerts = db.transaction { implicit graph ⇒
          alertSrv.initSteps.visible.richAlert
            .map(_.toJson)
            .toList()
        }
        Success(Results.Ok(Json.toJson(alerts)))
      }

  def update(alertId: String): Action[AnyContent] =
    entryPoint("update alert")
      .extract('alert, FieldsParser.update("alert", alertProperties))
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val propertyUpdaters: Seq[PropertyUpdater] = request.body('alert)
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .updateProperties(propertyUpdaters)
          .map(_ ⇒ Results.NoContent)
      }

  def mergeWithCase(alertId: String, caseId: String) = ???

  def markAsRead(alertId: String): Action[AnyContent] =
    entryPoint("mark alert as read")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.markAsRead(alertId)
              Results.NoContent
            }
        }
      }

  def markAsUnread(alertId: String): Action[AnyContent] =
    entryPoint("mark alert as unread")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.markAsUnread(alertId)
              Results.NoContent
            }
        }
      }

  def createCase(alertId: String): Action[AnyContent] =
    entryPoint("create case from alert")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          for {
            (alert, organisation) ← alertSrv.get(alertId).alertUserOrganisation(Permissions.manageCase).getOrFail()
            richCase              ← alertSrv.createCase(alert, None, organisation)
          } yield Results.Created(richCase.toJson)
        }
      }

  def followAlert(alertId: String): Action[AnyContent] =
    entryPoint("follow alert")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.followAlert(alertId)
              Results.NoContent
            }
        }
      }

  def unfollowAlert(alertId: String): Action[AnyContent] =
    entryPoint("unfollow alert")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.unfollowAlert(alertId)
              Results.NoContent
            }
        }
      }

  def stats: Action[AnyContent] =
    entryPoint("alert stats")
      .extract('query, statsParser("listAlert"))
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

  def search: Action[AnyContent] =
    entryPoint("search alert")
      .extract('query, searchParser("listAlert"))
      .authenticated { implicit request ⇒
        val query: Query = request.body('query)
        val result = db.transaction { graph ⇒
          queryExecutor.execute(query, graph, request.authContext)
        }
        Success(Results.Ok((result.toJson \ "result").as[JsValue]).withHeaders("X-Total" → "100"))
      }
}
