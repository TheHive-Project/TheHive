package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.services.ResponderSrv
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext

@Singleton
class ResponderCtrl @Inject()(
    entryPoint: EntryPoint,
    implicit val db: Database,
    responderSrv: ResponderSrv,
    implicit val ex: ExecutionContext
) {

  import WorkerConversion._

  def getResponders(entityType: String, entityId: String): Action[AnyContent] =
    entryPoint("get responders")
      .asyncAuth { implicit req =>
        db.roTransaction { implicit graph =>
          responderSrv
            .getRespondersByType(entityType, entityId)
            .map(l => Results.Ok(Json.toJson(l.map(toOutputWorker))))
        }
      }
}
