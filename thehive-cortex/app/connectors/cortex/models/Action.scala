package connectors.cortex.models

import java.util.Date

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future
import play.api.libs.json.JsObject
import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.models.{AttributeDef, BaseEntity, EntityDef, ModelDef, AttributeFormat => F, AttributeOption => O}
import org.elastic4play.utils.RichJson
import connectors.cortex.models.JsonFormat.jobStatusFormat
import services.AuditedModel

trait ActionAttributes { _: AttributeDef =>
  val responderId         = attribute("responderId", F.stringFmt, "Analyzer", O.readonly)
  val responderName       = optionalAttribute("responderName", F.stringFmt, "Name of the responder", O.readonly)
  val responderDefinition = optionalAttribute("responderDefinition", F.stringFmt, "Name of the responder definition", O.readonly)
  val status              = attribute("status", F.enumFmt(JobStatus), "Status of the action job", JobStatus.InProgress)
  val objectType          = attribute("objectType", F.stringFmt, "Type of the object on which this job was executed")
  val objectId            = attribute("objectId", F.stringFmt, "Object ID on which this job was executed", O.readonly)
  val startDate           = attribute("startDate", F.dateFmt, "Timestamp of the job start") // , O.model)
  val endDate             = optionalAttribute("endDate", F.dateFmt, "Timestamp of the job completion (or fail)")
  val report              = optionalAttribute("report", F.textFmt, "Action output", O.unaudited)
  val cortexId            = optionalAttribute("cortexId", F.stringFmt, "Id of cortex where the job is run", O.readonly)
  val cortexJobId         = optionalAttribute("cortexJobId", F.stringFmt, "Id of job in cortex", O.readonly)
  val operations          = attribute("operations", F.textFmt, "Update operations applied at the end of the job", "[]", O.unaudited)
}

@Singleton
class ActionModel @Inject()
    extends ModelDef[ActionModel, Action]("action", "Action", "/connector/cortex/action")
    with ActionAttributes
    with AuditedModel {

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = Future.successful {
    attrs
      .setIfAbsent("status", JobStatus.InProgress)
      .setIfAbsent("startDate", new Date)
  }
}
class Action(model: ActionModel, attributes: JsObject) extends EntityDef[ActionModel, Action](model, attributes) with ActionAttributes
