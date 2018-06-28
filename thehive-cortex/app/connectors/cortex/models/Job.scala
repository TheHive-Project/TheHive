package connectors.cortex.models

import java.util.Date

import javax.inject.{ Inject, Provider, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.libs.json._

import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.models.{ AttributeDef, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration, AttributeFormat ⇒ F, AttributeOption ⇒ O }
import org.elastic4play.utils.RichJson
import connectors.cortex.models.JsonFormat.jobStatusFormat
import models.{ Artifact, ArtifactModel }
import services.{ ArtifactSrv, AuditedModel, CaseSrv }

object JobStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val InProgress, Success, Failure, Waiting = Value
}

trait JobAttributes { _: AttributeDef ⇒
  val analyzerId = attribute("analyzerId", F.stringFmt, "Analyzer", O.readonly)
  val analyzerName = optionalAttribute("analyzerName", F.stringFmt, "Name of the analyzer", O.readonly)
  val analyzerDefinition = optionalAttribute("analyzerDefinition", F.stringFmt, "Name of the analyzer definition", O.readonly)
  val status = attribute("status", F.enumFmt(JobStatus), "Status of the job", JobStatus.InProgress)
  val artifactId = attribute("artifactId", F.stringFmt, "Original artifact on which this job was executed", O.readonly)
  val startDate = attribute("startDate", F.dateFmt, "Timestamp of the job start") // , O.model)
  val endDate = optionalAttribute("endDate", F.dateFmt, "Timestamp of the job completion (or fail)")
  val report = optionalAttribute("report", F.textFmt, "Analysis result", O.unaudited)
  val cortexId = optionalAttribute("cortexId", F.stringFmt, "Id of cortex where the job is run", O.readonly)
  val cortexJobId = optionalAttribute("cortexJobId", F.stringFmt, "Id of job in cortex", O.readonly)

}
@Singleton
class JobModel @Inject() (
    artifactModel: ArtifactModel,
    caseSrvProvider: Provider[CaseSrv],
    artifactSrvProvider: Provider[ArtifactSrv],
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends ChildModelDef[JobModel, Job, ArtifactModel, Artifact](artifactModel, "case_artifact_job", "Job", "/connector/cortex/job") with JobAttributes with AuditedModel {

  private lazy val logger = Logger(getClass)
  private lazy val caseSrv = caseSrvProvider.get
  private lazy val artifactSrv = artifactSrvProvider.get

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = Future.successful {
    attrs
      .setIfAbsent("status", JobStatus.InProgress)
      .setIfAbsent("startDate", new Date)
  }

  override def getStats(entity: BaseEntity): Future[JsObject] = {
    entity match {
      case job: Job ⇒
        import org.elastic4play.services.QueryDSL._
        for {
          caze ← caseSrv.find(child("case_artifact", withId(job.parentId.get)), Some("0-1"), Nil)._1.runWith(Sink.headOption)
          updatedReport ← job.report()
            .flatMap(Json.parse(_).asOpt[JsObject])
            .map { report ⇒
              val artifacts = for {
                artifact ← (report \ "artifacts").asOpt[Seq[JsObject]].getOrElse(Nil)
                dataType ← (artifact \ "dataType").asOpt[String]
                data ← (artifact \ "data").asOpt[String]
                artifactFound = artifactSrv.find(and(
                  "data" ~= data,
                  "dataType" ~= dataType,
                  withParent(caze.get)), Some("0-1"), Nil)._1
                  .runWith(Sink.headOption)
                  .map(_.isDefined)
                  .recover { case _ ⇒ false }
              } yield artifactFound.map(af ⇒ artifact + ("imported" -> JsBoolean(af)))
              Future.sequence(artifacts).map(a ⇒ report + ("artifacts" -> JsArray(a)))
            }
            .getOrElse(Future.successful(JsObject.empty))
        } yield job.toJson + ("report" -> updatedReport)
      case other ⇒
        logger.warn(s"Request jobStats from a non-job entity ?! ${other.getClass}:$other")
        Future.successful(JsObject.empty)
    }
  }
}

object Job {
  def fixJobAttr(attr: JsObject): JsObject = {
    val analyzerId = (attr \ "analyzerId").as[String]
    val attrWithAnalyzerName = (attr \ "analyzerName").asOpt[String].fold(attr + ("analyzerName" -> JsString(analyzerId)))(_ ⇒ attr)
    (attr \ "analyzerDefinition").asOpt[String].fold(attrWithAnalyzerName + ("analyzerDefinition" -> JsString(analyzerId)))(_ ⇒ attrWithAnalyzerName)
  }
}

class Job(model: JobModel, attributes: JsObject) extends EntityDef[JobModel, Job](model, Job.fixJobAttr(attributes)) with JobAttributes {
  override def toJson = super.toJson + ("report" → report().fold[JsValue](JsObject.empty)(r ⇒ Json.parse(r))) // FIXME is parse fails (invalid report)
}

case class CortexJob(
    id: String,
    analyzerId: String,
    analyzerName: String,
    analyzerDefinition: String,
    artifact: CortexArtifact,
    date: Date,
    status: JobStatus.Type,
    cortexIds: List[String] = Nil) {
  def onCortex(cortexId: String) = copy(cortexIds = cortexId :: cortexIds)
}