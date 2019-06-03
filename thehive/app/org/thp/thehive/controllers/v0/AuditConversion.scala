package org.thp.thehive.controllers.v0

import scala.language.implicitConversions

import play.api.libs.json.{JsObject, Json}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.thehive.dto.v0.{OutputAudit, OutputEntity}
import org.thp.thehive.models.RichAudit

trait AuditConversion {
  implicit def toOutputAudit(audit: RichAudit): Output[OutputAudit] =
    Output[OutputAudit](
      audit
        .into[OutputAudit]
        .withFieldComputed(_.operation, _.action) // TODO map action to operation
        .withFieldComputed(_.id, _._id)
        .withFieldComputed(_._createdAt, _._createdAt)
        .withFieldComputed(_._createdBy, _._createdBy)
        .withFieldComputed(_.`object`, _.`object`.map(OutputEntity.apply))
        .withFieldConst(_.base, true)
        .withFieldComputed(_.details, a ⇒ Json.parse(a.details.getOrElse("{}")).as[JsObject])
        .withFieldComputed(_.objectId, a ⇒ a.`object`.getOrElse(a.context)._id)
        .withFieldComputed(_.objectType, a ⇒ a.`object`.getOrElse(a.context)._model.label)
        .withFieldComputed(_.rootId, _._id)
        .withFieldComputed(_.startDate, _._createdAt)
        .withFieldComputed(_.summary, a ⇒ Map(a.`object`.getOrElse(a.context)._model.label → Map(a.action → 1)))
        .transform
    )
}
