package models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.{ JsObject, JsValue }

import org.elastic4play.models.{ Attribute, AttributeDef, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F }

trait CaseReportAttributes { _: AttributeDef ⇒
  val templateName: A[String] = attribute("name", F.stringFmt, "Name of the template")
  val content: A[String] = attribute("content", F.textFmt, "Content of the template")
}

class CaseReportModel extends ModelDef[CaseReportModel, CaseReport]("case_report", "CaseReport", "/case_report") with CaseReportAttributes {

}

class CaseReport(model: CaseReportModel, attributes: JsObject) extends EntityDef[CaseReportModel, CaseReport](model, attributes) with CaseReportAttributes {
}