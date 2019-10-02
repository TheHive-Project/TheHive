package org.thp.thehive.connector.cortex.controllers.v0

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query}
import org.thp.thehive.connector.cortex.dto.v0.{InputAction, OutputAction}
import org.thp.thehive.connector.cortex.models.{RichAction, Action => CortexAction}
import org.thp.thehive.connector.cortex.services.{ActionSrv, ActionSteps, EntityHelper}
import org.thp.thehive.controllers.v0.{IdOrName, OutputParam, QueryableCtrl}
import org.thp.thehive.models._
import org.thp.thehive.services._

@Singleton
class ActionCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    actionSrv: ActionSrv,
    entityHelper: EntityHelper,
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    alertSrv: AlertSrv,
    implicit val executionContext: ExecutionContext
) extends QueryableCtrl {
  import ActionConversion._
  import org.thp.thehive.controllers.v0.AlertConversion._
  import org.thp.thehive.controllers.v0.CaseConversion._
  import org.thp.thehive.controllers.v0.LogConversion._
  import org.thp.thehive.controllers.v0.ObservableConversion._
  import org.thp.thehive.controllers.v0.TaskConversion._

  implicit val entityWrites: OWrites[Entity] = OWrites[Entity] { entity =>
    db.roTransaction { implicit graph =>
        entity match {
          case c: Case       => caseSrv.get(c).richCaseWithoutPerms.getOrFail().map(_.toJson.as[JsObject])
          case t: Task       => taskSrv.get(t).richTask.getOrFail().map(_.toJson.as[JsObject])
          case o: Observable => observableSrv.get(o).richObservable.getOrFail().map(_.toJson.as[JsObject])
          case l: Log        => logSrv.get(l).richLog.getOrFail().map(_.toJson.as[JsObject])
          case a: Alert      => alertSrv.get(a).richAlert.getOrFail().map(_.toJson.as[JsObject])
        }
      }
      .getOrElse(Json.obj("_type" -> entity._model.label, "_id" -> entity._id))
  }

  override val entityName: String                           = "action"
  override val publicProperties: List[PublicProperty[_, _]] = actionProperties
  override val initialQuery: Query =
    Query.init[ActionSteps]("listAction", (graph, _) => actionSrv.initSteps(graph))
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, ActionSteps](
    "getAction",
    FieldsParser[IdOrName],
    (param, graph, authContext) => actionSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, ActionSteps, PagedResult[RichAction]](
    "page",
    FieldsParser[OutputParam],
    (range, actionSteps, _) => actionSteps.richPage(range.from, range.to, withTotal = true)(_.richAction.raw)
  )
  override val outputQuery: Query = Query.output[RichAction, OutputAction]

  def create: Action[AnyContent] =
    entryPoint("create action")
      .extract("action", FieldsParser[InputAction])
      .asyncAuth { implicit request =>
        val action: CortexAction = fromInputAction(request.body("action"))
        val tryEntity = db.roTransaction { implicit graph =>
          entityHelper.get(action.objectType, action.objectId, Permissions.manageAction)
        }
        for {
          entity <- Future.fromTry(tryEntity)
          action <- actionSrv.execute(action, entity)
        } yield Results.Ok(action.toJson)
      }

  def getByEntity(objectType: String, objectId: String): Action[AnyContent] =
    entryPoint("get by entity")
      .authRoTransaction(db) { implicit request => implicit graph =>
        for {
          entity <- entityHelper.get(toEntityType(objectType), objectId, Permissions.manageAction)
        } yield Results.Ok(Json.toJson(actionSrv.listForEntity(entity._id)))
      }
}
