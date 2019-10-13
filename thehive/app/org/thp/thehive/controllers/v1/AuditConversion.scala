package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.steps.IdMapping
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.dto.v1.{OutputAudit, OutputEntity}
import org.thp.thehive.models.RichAudit
import org.thp.thehive.services.AuditSteps

import scala.language.implicitConversions

object AuditConversion {
  implicit def toOutputAudit(audit: RichAudit): Output[OutputAudit] =
    Output[OutputAudit](
      audit
        .into[OutputAudit]
        .withFieldComputed(_.operation, _.action)
        .withFieldComputed(_._id, _._id)
        .withFieldComputed(_._createdAt, _._createdAt)
        .withFieldComputed(_._createdBy, _._createdBy)
        .withFieldComputed(_.obj, a => a.`object`.map(OutputEntity.apply))
//        .withFieldComputed(_.obj, a ⇒ OutputEntity(a.obj))
//        .withFieldComputed(
//          _.summary,
//          _.summary.mapValues(
//            opCount ⇒
//              opCount.map {
//                case (op, count) ⇒ op.toString → count
//              }
//          )
        .withFieldConst(_.attributeName, None) // FIXME
        .withFieldConst(_.oldValue, None)
        .withFieldConst(_.newValue, None)
        .withFieldConst(_.summary, Map.empty[String, Map[String, Int]])
        .transform
    )

  val auditProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[AuditSteps]
      .property("operation", UniMapping.string)(_.rename("action").readonly)
      .property("details", UniMapping.string)(_.field.readonly)
      .property("objectType", UniMapping.string.optional)(_.field.readonly)
      .property("objectId", UniMapping.string.optional)(_.field.readonly)
      .property("base", UniMapping.boolean)(_.rename("mainAction").readonly)
      .property("startDate", UniMapping.date)(_.rename("_createdAt").readonly)
      .property("requestId", UniMapping.string)(_.field.readonly)
      .property("rootId", IdMapping)(_.select(_.context._id).readonly)
      .build
}
