package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue, Json }

import org.elastic4play.database.DBRemove
import akka.NotUsed
import akka.actor.Actor
import akka.stream.scaladsl.Source
import models.{ Audit, AuditModel }

import org.elastic4play.controllers.Fields
import org.elastic4play.models.{ Attribute, BaseEntity, BaseModelDef }
import org.elastic4play.services._
import org.elastic4play.utils.{ Instance, RichJson }

trait AuditedModel { self: BaseModelDef ⇒
  def attributes: Seq[Attribute[_]]

  lazy val auditedAttributes: Map[String, Attribute[_]] =
    attributes
      .collect { case a if !a.isUnaudited ⇒ a.attributeName → a }
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
class AuditSrv @Inject() (
    auditModel: AuditModel,
    modelSrv: ModelSrv,
    auxSrv: AuxSrv,
    dBRemove: DBRemove,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext) {

  private[AuditSrv] lazy val logger = Logger(getClass)

  def apply(rootId: Option[String], count: Int): (Source[JsObject, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._

    val streamableEntities = modelSrv.list.collect {
      case m: AuditedModel if m.modelName != "user" ⇒ m.modelName
    }

    val filter = rootId match {
      case Some(rid) ⇒ and("rootId" ~= rid, "base" ~= true, "objectType" in (streamableEntities: _*))
      case None      ⇒ and("base" ~= true, "objectType" in (streamableEntities: _*))
    }
    val (src, total) = findSrv[AuditModel, Audit](auditModel, filter, Some(s"0-$count"), Seq("-startDate"))
    val entities = src.mapAsync(5) { audit ⇒
      val fSummary = findSrv(auditModel, and("requestId" ~= audit.requestId(), "objectType" in (streamableEntities: _*)), groupByField("objectType", groupByField("operation", selectCount)))
        .map { json ⇒
          json.collectValues {
            case objectType: JsObject ⇒ objectType.collectValues {
              case operation: JsObject ⇒ (operation \ "count").as[JsValue]
            }
          }
        }
      val fObj = auxSrv.apply(audit.objectType(), audit.objectId(), 10, withStats = false, removeUnaudited = true)

      for {
        summary ← fSummary
        obj ← fObj
      } yield JsObject(Seq("base" → (audit.toJson + ("object" → obj)), "summary" → summary))
    }
    (entities, total)
  }

  def realDelete(audit: Audit): Future[Unit] = {
    dBRemove(audit).map(_ ⇒ ())
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Audit, NotUsed], Future[Long]) = {
    findSrv[AuditModel, Audit](auditModel, queryDef, range, sortBy)
  }

  def findFor(entity: BaseEntity, range: Option[String], sortBy: Seq[String]): (Source[Audit, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    findSrv[AuditModel, Audit](auditModel, and("objectId" ~= entity.id, "objectType" ~= entity.model.modelName), range, sortBy)
  }
}

@Singleton
class AuditActor @Inject() (
    auditModel: AuditModel,
    createSrv: CreateSrv,
    eventSrv: EventSrv,
    webHooks: WebHooks,
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
      val audit = Json.obj(
        "operation" → action,
        "details" → model.selectAuditedAttributes(details),
        "objectType" → model.modelName,
        "objectId" → id,
        "base" → !currentRequestIds.contains(requestId),
        "startDate" → date,
        "rootId" → routing,
        "requestId" → requestId)

      createSrv[AuditModel, Audit](auditModel, Fields(audit))(authContext)
        .failed.foreach(t ⇒ logger.error("Audit error", t))
      currentRequestIds = currentRequestIds + requestId

      webHooks.send(audit)
  }
}