package models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.{ JsObject, JsValue }

import models.JsonFormat.caseTemplateStatusFormat

import org.elastic4play.models.{ Attribute, AttributeDef, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F }

object CaseTemplateStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Ok, Deleted = Value
}

trait CaseTemplateAttributes { _: AttributeDef ⇒
  def taskAttributes: Seq[Attribute[_]]

  val templateName: A[String] = attribute("name", F.stringFmt, "Name of the template")
  val titlePrefix: A[Option[String]] = optionalAttribute("titlePrefix", F.textFmt, "Title of the case")
  val description: A[Option[String]] = optionalAttribute("description", F.textFmt, "Description of the case")
  val severity: A[Option[Long]] = optionalAttribute("severity", F.numberFmt, "Severity if the case is an incident (0-5)")
  val tags: A[Seq[String]] = multiAttribute("tags", F.stringFmt, "Case tags")
  val flag: A[Option[Boolean]] = optionalAttribute("flag", F.booleanFmt, "Flag of the case")
  val tlp: A[Option[Long]] = optionalAttribute("tlp", F.numberFmt, "TLP level")
  val status: A[CaseTemplateStatus.Value] = attribute("status", F.enumFmt(CaseTemplateStatus), "Status of the case", CaseTemplateStatus.Ok)
  val metricNames: A[Seq[String]] = multiAttribute("metricNames", F.stringFmt, "List of acceptable metric name")
  val customFields: A[Option[JsValue]] = optionalAttribute("customFields", F.customFields, "List of acceptable custom fields")
  val tasks: A[Seq[JsObject]] = multiAttribute("tasks", F.objectFmt(taskAttributes), "List of created tasks")
}

@Singleton
class CaseTemplateModel @Inject() (taskModel: TaskModel) extends ModelDef[CaseTemplateModel, CaseTemplate]("caseTemplate") with CaseTemplateAttributes {
  def taskAttributes: Seq[Attribute[_]] = taskModel
    .attributes
    .filter(_.isForm)
}
class CaseTemplate(model: CaseTemplateModel, attributes: JsObject) extends EntityDef[CaseTemplateModel, CaseTemplate](model, attributes) with CaseTemplateAttributes {
  def taskAttributes = Nil
}