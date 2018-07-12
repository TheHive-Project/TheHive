package models

import play.api.libs.json.JsObject

import org.elastic4play.models.{ AttributeDef, EntityDef, ModelDef, AttributeFormat ⇒ F }

trait CaseReportAttributes { _: AttributeDef ⇒
  val templateName: A[String] = attribute("name", F.stringFmt, "Name of the template")
  val content: A[String] = attribute("content", F.textFmt, "Content of the template")
}

class CaseReportModel extends ModelDef[CaseReportModel, CaseReport]("case_report", "case_report", "/case_report") with CaseReportAttributes

class CaseReport(model: CaseReportModel, attributes: JsObject)
  extends EntityDef[CaseReportModel, CaseReport](model, attributes)
  with CaseReportAttributes