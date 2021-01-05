package org.thp.thehive.connector.cortex.controllers.v0

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.services.ResponderSrv
import org.thp.thehive.controllers.v0.Conversion._
import play.api.libs.json.JsObject
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext

@Singleton
class ResponderCtrl @Inject() (
    entrypoint: Entrypoint,
    implicit val db: Database,
    responderSrv: ResponderSrv,
    implicit val ex: ExecutionContext
) {

  def getResponders(entityType: String, entityId: String): Action[AnyContent] =
    entrypoint("get responders")
      .asyncAuth { implicit req =>
        responderSrv
          .getRespondersByType(entityType, EntityIdOrName(entityId))
          .map(l => Results.Ok(l.toSeq.toJson))
      }

  def searchResponders: Action[AnyContent] =
    entrypoint("search responders")
      .extract("query", FieldsParser.jsObject)
      .asyncAuth { implicit req =>
        val query: JsObject = req.body("query")
        responderSrv
          .searchResponders(query)
          .map(l => Results.Ok(l.toSeq.toJson))
      }
}
