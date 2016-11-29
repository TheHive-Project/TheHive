package models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.JsObject

import org.elastic4play.models.{ Attribute, AttributeDef, AttributeFormat ⇒ F, EntityDef, HiveEnumeration, ModelDef }

import JsonFormat.caseTemplateStatusFormat

object CaseTemplateStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Ok, Deleted = Value
}

trait CaseTemplateAttributes { _: AttributeDef ⇒
  def taskAttributes: Seq[Attribute[_]]

  val templateName = attribute("name", F.stringFmt, "Name of the template")
  val titlePrefix = optionalAttribute("titlePrefix", F.textFmt, "Title of the case")
  val description = optionalAttribute("description", F.textFmt, "Description of the case")
  val severity = optionalAttribute("severity", F.numberFmt, "Severity if the case is an incident (0-5)")
  val tags = multiAttribute("tags", F.stringFmt, "Case tags")
  val flag = optionalAttribute("flag", F.booleanFmt, "Flag of the case")
  val tlp = optionalAttribute("tlp", F.numberFmt, "TLP level")
  val status = attribute("status", F.enumFmt(CaseTemplateStatus), "Status of the case", CaseTemplateStatus.Ok)
  val metricNames = multiAttribute("metricNames", F.stringFmt, "List of acceptable metric name")
  val tasks = multiAttribute("tasks", F.objectFmt(taskAttributes), "List of created tasks")
}

@Singleton
class CaseTemplateModel @Inject() (taskModel: TaskModel) extends ModelDef[CaseTemplateModel, CaseTemplate]("caseTemplate") with CaseTemplateAttributes {
  def taskAttributes = taskModel.attributes
}
class CaseTemplate(model: CaseTemplateModel, attributes: JsObject) extends EntityDef[CaseTemplateModel, CaseTemplate](model, attributes) with CaseTemplateAttributes {
  def taskAttributes = Nil
}