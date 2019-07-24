package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.services.ActionSrv
import org.thp.thehive.models.{EntityHelper, Permissions}
import play.api.libs.json.{JsArray, Writes}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class ActionCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    actionSrv: ActionSrv,
    entityHelper: EntityHelper,
    implicit val executionContext: ExecutionContext
) {
  import ActionConversion._

  implicit val entityWrites: Writes[Entity] = entityHelper.writes

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .auth { _ =>
        Success(Results.Ok(JsArray.empty))
      }

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
}
