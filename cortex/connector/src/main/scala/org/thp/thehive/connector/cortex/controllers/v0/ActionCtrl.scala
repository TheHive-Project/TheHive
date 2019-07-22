package org.thp.thehive.connector.cortex.controllers.v0

import scala.util.Success
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.services.ActionSrv
import org.thp.thehive.models.EntityHelper

@Singleton
class ActionCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    actionSrv: ActionSrv,
    entityHelper: EntityHelper
) {

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .auth { _ =>
        Success(Results.Ok(JsArray.empty))
      }

  def create: Action[AnyContent] =
    entryPoint("create action")
      .extract("action", FieldsParser[InputAction])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputAction: InputAction = request.body("action")

//        actionSrv.execute(inputAction)
        Success(Results.Ok)
      }
}
