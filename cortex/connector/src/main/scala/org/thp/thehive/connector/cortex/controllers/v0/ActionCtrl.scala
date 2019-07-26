package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query}
import org.thp.thehive.connector.cortex.dto.v0.{InputAction, OutputAction}
import org.thp.thehive.connector.cortex.models.RichAction
import org.thp.thehive.connector.cortex.services.{ActionSrv, ActionSteps}
import org.thp.thehive.controllers.v0.{OutputParam, QueryableCtrl}
import org.thp.thehive.models.{EntityHelper, Permissions}
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ActionCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    actionSrv: ActionSrv,
    entityHelper: EntityHelper,
    implicit val executionContext: ExecutionContext
) extends QueryableCtrl {
  import ActionConversion._

  implicit val entityWrites: Writes[Entity] = entityHelper.writes

  override val entityName: String                           = "action"
  override val publicProperties: List[PublicProperty[_, _]] = actionProperties
  override val initialQuery: Query =
    Query.init[ActionSteps]("listAction", (graph, _) => actionSrv.initSteps(graph))
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
        db.transaction { implicit graph =>
          val inputAction: InputAction = request.body("action")

          for {
            entity <- Future.fromTry(entityHelper.get(inputAction.objectType, inputAction.objectId, Permissions.manageAction))
            action <- actionSrv.execute(inputAction, entity)
          } yield Results.Ok(action.toJson)
        }
      }

  def getByEntity(entityType: String, entityId: String): Action[AnyContent] =
    entryPoint("get by entity")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          entity <- entityHelper.get(entityType, entityId, Permissions.manageAction)
        } yield Results.Ok(Json.toJson(actionSrv.listForEntity(entity._id)))
      }
}
