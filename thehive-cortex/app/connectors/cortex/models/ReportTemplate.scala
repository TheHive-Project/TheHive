package connectors.cortex.models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.JsObject

import org.elastic4play.models.{ AttributeDef, AttributeFormat ⇒ F, AttributeOption ⇒ O, EntityDef, ModelDef }
import org.elastic4play.models.BaseEntity
import play.api.libs.json.JsString
import scala.concurrent.Future
import org.elastic4play.models.HiveEnumeration

import connectors.cortex.models.JsonFormat._

object ReportType extends Enumeration with HiveEnumeration {
  type Type = Value
  val short, long = Value
}

trait ReportTemplateAttributes { _: AttributeDef ⇒
  val reportTemplateId = attribute("_id", F.stringFmt, "Report template id", O.model)
  val content = attribute("content", F.textFmt, "Content of the template")
  val reportType = attribute("reportType", F.enumFmt(ReportType), "Type of the report (short or long)")
  val analyzerId = attribute("analyzerId", F.stringFmt, "Id of analyzers")
}

@Singleton
class ReportTemplateModel @Inject() extends ModelDef[ReportTemplateModel, ReportTemplate]("reportTemplate", "Report template", "/connector/cortex/reportTemplate") with ReportTemplateAttributes {
  override def creationHook(parent: Option[BaseEntity], attrs: JsObject) = {
    val maybeId = for {
      analyzerId ← (attrs \ "analyzerId").asOpt[String]
      reportType ← (attrs \ "reportType").asOpt[String]
    } yield analyzerId + "_" + reportType
    Future.successful(maybeId.fold(attrs)(id ⇒ attrs + ("_id" → JsString(id))))
  }
}
class ReportTemplate(model: ReportTemplateModel, attributes: JsObject) extends EntityDef[ReportTemplateModel, ReportTemplate](model, attributes) with ReportTemplateAttributes
