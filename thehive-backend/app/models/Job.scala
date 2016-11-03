package models

import java.util.Date

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

import play.api.libs.json.{ JsObject, JsValue, Json }

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.models.{ AttributeDef, AttributeFormat => F, AttributeOption => O, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration }

import JsonFormat.jobStatusFormat
import services.AuditedModel

object JobStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val InProgress, Success, Failure = Value
}

trait JobAttributes { _: AttributeDef =>
  val analyzerId = attribute("analyzerId", F.stringFmt, "Analyzer", O.readonly)
  val status = attribute("status", F.enumFmt(JobStatus), "Status of the job", JobStatus.InProgress)
  val artifactId = attribute("artifactId", F.stringFmt, "Original artifact on which this job was executed", O.readonly)
  val startDate = attribute("startDate", F.dateFmt, "Timestamp of the job start", O.model)
  val endDate = optionalAttribute("endDate", F.dateFmt, "Timestamp of the job completion (or fail)")
  val report = optionalAttribute("report", F.textFmt, "Analysis result", O.unaudited)

}
@Singleton
class JobModel @Inject() (artifactModel: ArtifactModel) extends ChildModelDef[JobModel, Job, ArtifactModel, Artifact](artifactModel, "case_artifact_job") with JobAttributes with AuditedModel {

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = Future.successful {
    attrs - "report" - "endDate" +
      ("startDate" -> Json.toJson(new Date)) +
      ("status" -> Json.toJson(JobStatus.InProgress))
  }
}
class Job(model: JobModel, attributes: JsObject) extends EntityDef[JobModel, Job](model, attributes) with JobAttributes {
  override def toJson = super.toJson + ("report" -> report().fold[JsValue](JsObject(Nil))(r => Json.parse(r)))
}
//    def insertInArtifact(artifact: CaseArtifacts#ENTITY) = {
//      db.create(tableName, attributes + ("parent" -> JsString(artifact.id)) + (s"$$routing" -> JsString(artifact.routing))).map { indexResponse =>
//        read(indexResponse.getId(), Some(artifact.id), attributes)
//      }
//    }
//    override def toJson = {
//      val json = super.toJson
//      (json \ "report" \ analyzerId).toOption match {
//        case Some(report) => json + ("report" -> report)
//        case None         => json
//      }
//
//    }
