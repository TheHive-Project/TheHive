package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.services.ActionSrv
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class ResponderCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    actionSrv: ActionSrv
) {

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .auth { _ =>
        Success(Results.Ok(JsArray.empty))
      }

}
