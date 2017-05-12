package connectors.cortex.models

import java.util.Date

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

import play.api.libs.json.{ JsObject, JsValue, Json }

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.models.{ AttributeDef, AttributeFormat ⇒ F, AttributeOption ⇒ O, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration }
import org.elastic4play.utils.RichJson

import connectors.cortex.models.JsonFormat.jobStatusFormat
import models.{ Artifact, ArtifactModel }
import services.AuditedModel

object JobStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val InProgress, Success, Failure = Value
}

trait JobAttributes { _: AttributeDef ⇒
  val analyzerId = attribute("analyzerId", F.stringFmt, "Analyzer", O.readonly)
  val status = attribute("status", F.enumFmt(JobStatus), "Status of the job", JobStatus.InProgress)
  val artifactId = attribute("artifactId", F.stringFmt, "Original artifact on which this job was executed", O.readonly)
  val startDate = attribute("startDate", F.dateFmt, "Timestamp of the job start") // , O.model)
  val endDate = optionalAttribute("endDate", F.dateFmt, "Timestamp of the job completion (or fail)")
  val report = optionalAttribute("report", F.textFmt, "Analysis result", O.unaudited)
  val cortexId = optionalAttribute("cortexId", F.stringFmt, "Id of cortex where the job is run", O.readonly)
  val cortexJobId = optionalAttribute("cortexJobId", F.stringFmt, "Id of job in cortex", O.readonly)

}
@Singleton
class JobModel @Inject() (artifactModel: ArtifactModel) extends ChildModelDef[JobModel, Job, ArtifactModel, Artifact](artifactModel, "case_artifact_job") with JobAttributes with AuditedModel {

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = Future.successful {
    attrs
      .setIfAbsent("status", JobStatus.InProgress)
      .setIfAbsent("startDate", new Date)
  }
}
class Job(model: JobModel, attributes: JsObject) extends EntityDef[JobModel, Job](model, attributes) with JobAttributes {
  override def toJson = super.toJson + ("report" → report().fold[JsValue](JsObject(Nil))(r ⇒ Json.parse(r))) // FIXME is parse fails (invalid report)
}

case class CortexJob(id: String, analyzerId: String, artifact: CortexArtifact, date: Date, status: JobStatus.Type, cortexIds: List[String] = Nil) {
  def onCortex(cortexId: String) = copy(cortexIds = cortexId :: cortexIds)
}