package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json.{JsObject, Json, OFormat}

import org.thp.scalligraph.models.Entity

case class OutputEntity(_type: String, _id: String, _createdAt: Date, _createdBy: String, _updatedAt: Option[Date], _updatedBy: Option[String])

object OutputEntity {
  implicit val format: OFormat[OutputEntity] = Json.format[OutputEntity]

  def apply(e: Entity): OutputEntity = OutputEntity(
    e._model.label,
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
    _createdBy: String,
//    _updatedBy: Option[String] = None, // can't be set
    _createdAt: Date,
//    _updatedAt: Option[Date] = None, // can't be set
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
  implicit val format: OFormat[OutputAudit] = Json.format[OutputAudit]
}

/*
{
 "base": {
  base: true
    createdAt/By
    details: ** updated fields **
    id
    object: { _ with parent _}
    objectId
    objectType:
    operation:
    requestId:
    rootId:
    startDate:
    _id
    _parent
    _routing
    _type: audit
    _version
 }
 "summary": { objectType: { operation: count}}
}
 */
