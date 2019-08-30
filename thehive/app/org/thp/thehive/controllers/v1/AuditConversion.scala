package org.thp.thehive.controllers.v1

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.thehive.dto.v1.{OutputAudit, OutputEntity}
import org.thp.thehive.models.RichAudit

trait AuditConversion {
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

}
