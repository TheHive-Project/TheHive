package models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.JsObject

import org.elastic4play.models.{ AttributeDef, EntityDef, ModelDef, AttributeFormat ⇒ F }
trait CaseReportingTemplateAttributes { _: AttributeDef ⇒

  val title: A[String] = attribute("title", F.stringFmt, "Title of the template")
  val content = attribute("content", F.textFmt, "Content of the template")
  val isDefault = attribute("isDefault", F.booleanFmt, "Is the template set as default")
}

@Singleton
class CaseReportingTemplateModel @Inject() extends ModelDef[CaseReportingTemplateModel, CaseReportingTemplate]("caseReportingTemplate", "Case reporting template", "/case/reporting") with CaseReportingTemplateAttributes {

}
class CaseReportingTemplate(model: CaseReportingTemplateModel, attributes: JsObject) extends EntityDef[CaseReportingTemplateModel, CaseReportingTemplate](model, attributes) with CaseReportingTemplateAttributes
