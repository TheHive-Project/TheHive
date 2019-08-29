package org.thp.thehive.connector.cortex.controllers.v0

import scala.concurrent.ExecutionContext

import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.services.ResponderSrv

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
        responderSrv
          .getRespondersByType(entityType, entityId)
          .map(l => Results.Ok(Json.toJson(l.map(toOutputWorker))))
      }

  def searchResponders: Action[AnyContent] =
    entryPoint("search responders")
      .extract("query", FieldsParser.jsObject)
      .asyncAuth { implicit req =>
        val query: JsObject = req.body("query")
        responderSrv
          .searchResponders(query)
          .map(l => Results.Ok(Json.toJson(l.map(toOutputWorker))))
      }
}
