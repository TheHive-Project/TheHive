package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.Logger
import play.api.libs.json.{ JsBoolean, JsObject, Json }

import akka.actor.Actor
import models.{ Audit, AuditModel }

import org.elastic4play.controllers.Fields
import org.elastic4play.models.{ Attribute, BaseEntity, BaseModelDef }
import org.elastic4play.services._
import org.elastic4play.utils.Instance

trait AuditedModel { self: BaseModelDef ⇒
  def attributes: Seq[Attribute[_]]

  lazy val auditedAttributes: Map[String, Attribute[_]] =
    attributes
      .collect { case a if !a.isUnaudited ⇒ a.name → a }
      .toMap
  def selectAuditedAttributes(attrs: JsObject) = JsObject {
    attrs.fields.flatMap {
      case (attrName, value) ⇒
        val attrNames = attrName.split("\\.").toSeq
        auditedAttributes.get(attrNames.head).map { _ ⇒
          val reverseNames = attrNames.reverse
          reverseNames.drop(1).foldLeft(reverseNames.head → value)((jsTuple, name) ⇒ name → JsObject(Seq(jsTuple)))
        }
    }
  }
}

@Singleton
class AuditActor @Inject() (
    auditModel: AuditModel,
    createSrv: CreateSrv,
    eventSrv: EventSrv,
    implicit val ec: ExecutionContext) extends Actor {
  object EntityExtractor {
    def unapply(e: BaseEntity) = Some((e.model, e.id, e.routing))
  }
  var currentRequestIds = Set.empty[String]
  private[AuditActor] lazy val logger = Logger(getClass)

  override def preStart(): Unit = {
    eventSrv.subscribe(self, classOf[EventMessage])
    super.preStart()
  }

  override def postStop(): Unit = {
    eventSrv.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = {
    case RequestProcessEnd(request, _) ⇒
      currentRequestIds = currentRequestIds - Instance.getRequestId(request)
    case AuditOperation(EntityExtractor(model: AuditedModel, id, routing), action, details, authContext, date) ⇒
      val requestId = authContext.requestId
      createSrv[AuditModel, Audit](auditModel, Fields.empty
        .set("operation", action.toString)
        .set("details", model.selectAuditedAttributes(details))
        .set("objectType", model.name)
        .set("objectId", id)
        .set("base", JsBoolean(!currentRequestIds.contains(requestId)))
        .set("startDate", Json.toJson(date))
        .set("rootId", routing)
        .set("requestId", requestId))(authContext)
        .failed.foreach(t ⇒ logger.error("Audit error", t))
      currentRequestIds = currentRequestIds + requestId
  }
}