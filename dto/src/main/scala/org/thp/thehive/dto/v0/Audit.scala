package org.thp.thehive.dto.v0

import java.util.Date

import org.thp.scalligraph.models.Entity
import play.api.libs.json._

case class OutputEntity(_type: String, _id: String, _createdAt: Date, _createdBy: String, _updatedAt: Option[Date], _updatedBy: Option[String])

object OutputEntity {
  implicit val format: OFormat[OutputEntity] = Json.format[OutputEntity]

  def apply(e: Entity): OutputEntity = OutputEntity(
    e._label,
    e._id,
    e._createdAt,
    e._createdBy,
    e._updatedAt,
    e._updatedBy
  )
}

case class OutputAudit(
    _id: String,
    id: String,
    createdBy: String,
//    _updatedBy: Option[String] = None, // can't be set
    createdAt: Date,
//    _updatedAt: Option[Date] = None, // can't be set
    _type: String,
    base: Boolean = true, // always true
    details: JsObject,
    objectId: String,
    objectType: String,
    operation: String,
    requestId: String,
    rootId: String,
    startDate: Date,
    `object`: Option[OutputEntity],
    summary: Map[String, Map[String, Int]]
)

object OutputAudit {

  val auditWrites: OWrites[OutputAudit] = Json.writes[OutputAudit].transform { js: JsObject =>
    Json.obj("base" -> (js - "summary"), "summary" -> (js \ "summary").asOpt[JsObject])
  }

  val auditReads: Reads[OutputAudit] = Reads[OutputAudit] { js =>
    for {
      base    <- (js \ "base").validate[JsObject]
      summary <- (js \ "summary").validate[JsObject]
      audit   <- Json.reads[OutputAudit].reads(base ++ summary)
    } yield audit
  }
  implicit val format: OFormat[OutputAudit] = OFormat(auditReads, auditWrites)
}
