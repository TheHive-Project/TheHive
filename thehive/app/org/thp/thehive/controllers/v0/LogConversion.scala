package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputLog, OutputLog}
import org.thp.thehive.models.Log
import org.thp.thehive.services.LogSteps

trait LogConversion {

  implicit def toOutputLog(log: Log with Entity): Output[OutputLog] =
    Output[OutputLog](
      log
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
        .withFieldConst(_.status, "Ok")
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
      .property[String]("message")(_.simple.updatable)
      .property[Boolean]("deleted")(_.simple.updatable)
      .property[Date]("startDate")(_.rename("date").readonly)
      .property[String]("status")(_.simple.readonly)
      .build
}
