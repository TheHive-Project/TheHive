package models

import java.util.Date
import javax.inject.{Inject, Singleton}

import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsObject, Json}

import models.JsonFormat.logStatusFormat
import services.AuditedModel

import org.elastic4play.models.{AttributeDef, ChildModelDef, EntityDef, HiveEnumeration, AttributeFormat => F, AttributeOption => O}

object LogStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Ok, Deleted = Value
}

trait LogAttributes { _: AttributeDef =>
  val message   = attribute("message", F.textFmt, "Message")
  val startDate = attribute("startDate", F.dateFmt, "Timestamp of the comment", new Date)
  // attachment is stored as JsObject containing :
  // - id (string): used to get data from dataStore
  // - hashes (array of string): contain hashes of the data using different algorithms
  // - filename (string): the original name of the file
  // - size (number): the size of the file
  // - contentType (string): the mimetype of the file (send by client)
  val attachment = optionalAttribute("attachment", F.attachmentFmt, "Attached file", O.readonly)
  val status     = attribute("status", F.enumFmt(LogStatus), "Status of the log", LogStatus.Ok)
  val owner      = attribute("owner", F.userFmt, "User who owns the log")
}

@Singleton
class LogModel @Inject()(taskModel: TaskModel)
    extends ChildModelDef[LogModel, Log, TaskModel, Task](taskModel, "case_task_log", "Log", "/case/task/log")
    with LogAttributes
    with AuditedModel {
  override val defaultSortBy   = Seq("-startDate")
  override val removeAttribute = Json.obj("status" -> LogStatus.Deleted)
}
class Log(model: LogModel, attributes: JsObject) extends EntityDef[LogModel, Log](model, attributes) with LogAttributes
