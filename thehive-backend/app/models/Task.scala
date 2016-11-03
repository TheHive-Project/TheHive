package models

import java.util.Date

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

import play.api.libs.json.{ JsBoolean, JsObject }
import play.api.libs.json.JsValue.jsValueToJsLookup

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.models.{ AttributeDef, AttributeFormat => F, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration }
import org.elastic4play.utils.RichJson

import JsonFormat.taskStatusFormat
import services.AuditedModel

object TaskStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Waiting, InProgress, Completed, Cancel = Value
}

trait TaskAttributes { _: AttributeDef =>
  val title = attribute("title", F.textFmt, "Title of the task")
  val description = optionalAttribute("description", F.textFmt, "Task details")
  val owner = optionalAttribute("owner", F.stringFmt, "User who owns the task")
  val status = attribute("status", F.enumFmt(TaskStatus), "Status of the task", TaskStatus.Waiting)
  val flag = attribute("flag", F.booleanFmt, "Flag of the task", false)
  val startDate = optionalAttribute("startDate", F.dateFmt, "Timestamp of the comment start")
  val endDate = optionalAttribute("endDate", F.dateFmt, "Timestamp of the comment end")

}
@Singleton
class TaskModel @Inject() (caseModel: CaseModel) extends ChildModelDef[TaskModel, Task, CaseModel, Case](caseModel, "case_task") with TaskAttributes with AuditedModel {
  override val defaultSortBy = Seq("-startDate")

  override def updateHook(task: BaseEntity, updateAttrs: JsObject): Future[JsObject] = Future.successful {
    (updateAttrs \ "status").asOpt[TaskStatus.Type] match {
      case Some(TaskStatus.InProgress) =>
        updateAttrs
          .setIfAbsent("startDate", new Date)
      case Some(TaskStatus.Completed) =>
        updateAttrs
          .setIfAbsent("endDate", new Date) +
          ("flag" -> JsBoolean(false))
      case _ => updateAttrs
    }
  }
}
class Task(model: TaskModel, attributes: JsObject) extends EntityDef[TaskModel, Task](model, attributes) with TaskAttributes