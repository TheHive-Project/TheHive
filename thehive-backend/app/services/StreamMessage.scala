package services

import scala.concurrent.{ExecutionContext, Future}

import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsObject, Json}

import org.elastic4play.services.{AuditOperation, AuxSrv, MigrationEvent}

trait AggregatedMessage[M] {
  def add(message: M): AggregatedMessage[M]
  def toJson(implicit ec: ExecutionContext): Future[JsObject]
}

case class AggregatedAuditMessage(auxSrv: AuxSrv, message: Future[JsObject], summary: Map[String, Map[String, Int]])
    extends AggregatedMessage[AuditOperation] {

  def add(operation: AuditOperation): AggregatedMessage[AuditOperation] = {
    // Additional operation, update only the summary
    val modelSummary = summary.getOrElse(operation.entity.model.modelName, Map.empty[String, Int])
    val actionCount  = modelSummary.getOrElse(operation.action.toString, 0)
    copy(
      summary = summary + (operation.entity.model.modelName → (modelSummary +
        (operation.action.toString                          → (actionCount + 1))))
    )
  }

  def toJson(implicit ec: ExecutionContext): Future[JsObject] = message.map { msg ⇒
    Json.obj("base" → msg, "summary" → summary)
  }
}

object AggregatedAuditMessage {
  lazy val logger: Logger = Logger(getClass)

  def apply(auxSrv: AuxSrv, operation: AuditOperation)(implicit ec: ExecutionContext): AggregatedAuditMessage = {
    // First operation of the group
    val msg = auxSrv(operation.entity, 10, withStats = false, removeUnaudited = true)
      .recover {
        case error ⇒
          logger.error("auxSrv fails", error)
          JsObject.empty
      }
      .map { obj ⇒
        Json.obj(
          "objectId"   → operation.entity.id,
          "objectType" → operation.entity.model.modelName,
          "operation"  → operation.action,
          "startDate"  → operation.date,
          "rootId"     → operation.entity.routing,
          "user"       → operation.authContext.userId,
          "createdBy"  → operation.authContext.userId,
          "createdAt"  → operation.date,
          "requestId"  → operation.authContext.requestId,
          "object"     → obj,
          "details"    → operation.details
        )
      }
    new AggregatedAuditMessage(auxSrv, msg, Map(operation.entity.model.modelName → Map(operation.action.toString → 1)))
  }
}

case class AggregatedMigrationMessage(tableName: String, current: Long, total: Long) extends AggregatedMessage[MigrationEvent] {
  override def add(message: MigrationEvent): AggregatedMessage[MigrationEvent] = {
    assert(message.modelName == tableName)
    if (current < message.current)
      copy(current = message.current, total = message.total)
    else this
  }

  def toJson(implicit ec: ExecutionContext): Future[JsObject] =
    Future.successful(
      Json.obj("base" → Json.obj("rootId" → current, "objectType" → "migration", "tableName" → tableName, "current" → current, "total" → total))
    )
}

object AggregatedMigrationMessage {
  def apply(event: MigrationEvent) = new AggregatedMigrationMessage(event.modelName, event.current, event.total)
  def endOfMigration               = new AggregatedMigrationMessage("end", 0, 0)
}
