package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputLog, OutputLog}
import org.thp.thehive.models.{Log, RichLog}
import org.thp.thehive.services.LogSteps

object LogConversion {
  import AttachmentConversion._

  implicit def toOutputLog(richLog: RichLog): Output[OutputLog] =
    Output[OutputLog](
      richLog
        .into[OutputLog]
        .withFieldConst(_._type, "case_task_log")
        .withFieldComputed(_.id, _._id)
        .withFieldComputed(_._id, _._id)
        .withFieldComputed(_.updatedAt, _._updatedAt)
        .withFieldComputed(_.updatedBy, _._updatedBy)
        .withFieldComputed(_.createdAt, _._createdAt)
        .withFieldComputed(_.createdBy, _._createdBy)
        .withFieldComputed(_.message, _.message)
        .withFieldComputed(_.startDate, _._createdAt)
        .withFieldComputed(_.owner, _._createdBy)
        .withFieldComputed(_.status, l => if (l.deleted) "Deleted" else "Ok")
        .withFieldComputed(_.attachment, _.attachments.headOption.map(toOutputAttachment(_).toOutput))
        .transform
    )

  implicit def fromInputLog(inputLog: InputLog): Log =
    inputLog
      .into[Log]
      .withFieldConst(_.date, new Date)
      .withFieldConst(_.deleted, false)
      .transform

  val logProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[LogSteps]
      .property("message", UniMapping.string)(_.simple.updatable)
      .property("deleted", UniMapping.boolean)(_.simple.updatable)
      .property("startDate", UniMapping.date)(_.rename("date").readonly)
      .property("status", UniMapping.string)(_.simple.readonly)
      .property("attachment", UniMapping.string)(_.derived(_.out("LogAttachment").value[String]("id")).readonly)
      .build
}
