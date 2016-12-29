package connectors.cortex.models

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.JsObject

import org.elastic4play.models.{ AttributeDef, AttributeFormat ⇒ F, AttributeOption ⇒ O, EntityDef, ModelDef }
import org.elastic4play.BadRequestError
import org.elastic4play.models.BaseEntity
import play.api.libs.json.JsString
import scala.concurrent.Future
import org.elastic4play.models.HiveEnumeration

import JsonFormat._

object ReportType extends Enumeration with HiveEnumeration {
  type Type = Value
  val short, long = Value
}

trait ReportTemplateAttributes { _: AttributeDef ⇒
  val reportTemplateId = attribute("_id", F.stringFmt, "Report template id", O.model)
  val content = attribute("content", F.textFmt, "Content of the template")
  val flavor = attribute("flavor", F.enumFmt(ReportType), "Flavor of the report (short or long)")
  val analyzers = attribute("analyzers", F.stringFmt, "Id of analyzers")
}

@Singleton
class ReportTemplateModel @Inject() extends ModelDef[ReportTemplateModel, ReportTemplate]("reportTemplate") with ReportTemplateAttributes {
  override def creationHook(parent: Option[BaseEntity], attrs: JsObject) = {
    val maybeId = for {
      analyzers ← (attrs \ "analyzers").asOpt[String]
      flavor ← (attrs \ "flavor").asOpt[String]
    } yield analyzers + "_" + flavor
    Future.successful(maybeId.fold(attrs)(id ⇒ attrs + ("_id" → JsString(id))))
  }
}
class ReportTemplate(model: ReportTemplateModel, attributes: JsObject) extends EntityDef[ReportTemplateModel, ReportTemplate](model, attributes) with ReportTemplateAttributes