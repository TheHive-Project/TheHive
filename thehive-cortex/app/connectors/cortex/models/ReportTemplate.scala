package models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.JsObject

import org.elastic4play.models.{ AttributeDef, AttributeFormat ⇒ F, EntityDef, ModelDef }

trait ReportTemplateAttributes { _: AttributeDef ⇒
  val content = attribute("content", F.textFmt, "Content of the template")
  val falvor = attribute("flavor", F.stringFmt, "Flavor of the report (short or long)")
  val analyzers = multiAttribute("analyzers", F.stringFmt, "Id of analyzers")
}

@Singleton
class ReportTemplateModel @Inject() extends ModelDef[ReportTemplateModel, ReportTemplate]("reportTemplate") with ReportTemplateAttributes
class ReportTemplate(model: ReportTemplateModel, attributes: JsObject) extends EntityDef[ReportTemplateModel, ReportTemplate](model, attributes) with ReportTemplateAttributes