package connectors.cortex.services

import scala.concurrent.Future
import javax.inject.Singleton
import javax.inject.Inject
import play.api.libs.ws.WSClient
import play.api.Configuration
import java.nio.file.Path
import java.nio.file.Paths
import scala.util.Try
import scala.concurrent.ExecutionContext
import play.api.http.Status
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WSRequest
import play.api.libs.json.JsObject
import scala.language.implicitConversions
import akka.actor.ActorDSL.{ Act, actor }
import akka.actor.ActorSystem

import scala.util.control.NonFatal
import connectors.cortex.models.CortexModel
import connectors.cortex.models.Analyzer
import connectors.cortex.models.CortexJob
import org.elastic4play.services.EventSrv
import play.api.Logger
import services.MergeArtifact
import org.elastic4play.controllers.Fields
import connectors.cortex.models.JobStatus
import akka.stream.scaladsl.Sink
import akka.stream.Materializer

object CortexConfig {
  def getCortexClient(name: String, configuration: Configuration): Option[CortexClient] = {
    try {
      val url = configuration.getString("url").getOrElse(sys.error("url is missing"))
      val key = configuration.getString("key").getOrElse(sys.error("key is missing"))
      Some(new CortexClient(name, url, key))
    }
    catch {
      case NonFatal(_) ⇒ None
    }
  }

  def getInstances(configuration: Configuration): Map[String, CortexClient] = {
    val instances = for {
      cfg ← configuration.getConfig("cortex").toSeq
      key ← cfg.subKeys
      c ← cfg.getConfig(key)
      cic ← getCortexClient(key, c)
    } yield key → cic
    instances.toMap
  }
}
case class CortexConfig(truststore: Option[Path], instances: Map[String, CortexClient]) {

  @Inject
  def this(configuration: Configuration) = this(
    configuration.getString("cortex.cert").map(p ⇒ Paths.get(p)),
    CortexConfig.getInstances(configuration))
}

//  private[services] def mergeJobs(newArtifact: Artifact, artifacts: Seq[Artifact])(implicit authContext: AuthContext): Future[Done] = {
//    jobSrv.find(and(parent("case_artifact", withId(artifacts.map(_.id): _*)), "status" ~= JobStatus.Success), Some("all"), Nil)._1
//      .mapAsyncUnordered(5) { job ⇒
//        jobSrv.create(newArtifact, baseFields(job))
//      }
//      .runWith(Sink.ignore)
//  }

@Singleton
class CortexSrv @Inject() (
    cortexConfig: CortexConfig,
    jobSrv: JobSrv,
    eventSrv: EventSrv,
    implicit val ws: WSClient,
    implicit val ec: ExecutionContext,
    implicit val system: ActorSystem,
    implicit val mat: Materializer) {

  //  implicit def seqCortexModel[A](s: Seq[CortexModel[A]]) = new CortexModel[Seq[A]] { //= new CortexModel[Seq[A]] {
  //    def onCortex(cortexId: String) = s.map(_.onCortex(cortexId))
  //  }
  val mergeActor = actor(new Act {
    lazy val log = Logger(getClass)

    become {
      case MergeArtifact(newArtifact, artifacts, authContext) ⇒
        import org.elastic4play.services.QueryDSL._
        jobSrv.find(and(parent("case_artifact", withId(artifacts.map(_.id): _*)), "status" ~= JobStatus.Success), Some("all"), Nil)._1
          .mapAsyncUnordered(5) { job ⇒
            val baseFields = Fields(job.attributes - "_id" - "_routing" - "_parent" - "_type" - "createdBy" - "createdAt" - "updatedBy" - "updatedAt" - "user")
            jobSrv.create(newArtifact, baseFields)(authContext)
          }
          .runWith(Sink.ignore)
    }
  })

  eventSrv.subscribe(mergeActor, classOf[MergeArtifact]) // need to unsubsribe ?

  def askAllCortex[A](f: CortexClient ⇒ Future[CortexModel[A]]): Future[Seq[A]] = {
    Future.traverse(cortexConfig.instances.toSeq) {
      case (name, cortex) ⇒ f(cortex).map(_.onCortex(name))
    }
  }
  def askForAllCortex[A](f: CortexClient ⇒ Future[Seq[CortexModel[A]]]): Future[Seq[A]] = {
    Future
      .traverse(cortexConfig.instances.toSeq) {
        case (name, cortex) ⇒ f(cortex).map(_.map(_.onCortex(name)))
      }
      .map(_.flatten)
  }
  def getAnalyzer(analyzerId: String): Future[Seq[Analyzer]] = {
    askAllCortex(_.getAnalyzer(analyzerId))
  }

  def getAnalyzersFor(dataType: String): Future[Seq[Analyzer]] = {
    askForAllCortex(_.listAnalyzerForType(dataType))
  }

  def listAnalyzer: Future[Seq[Analyzer]] = {
    askForAllCortex(_.listAnalyzer)
  }

  def getJob(jobId: String): Future[CortexJob] = {
    // askAllCortex(_.getJob(jobId))
    ???
  }

  def listJob: Future[Seq[CortexJob]] = ???
  def createJob(analyzerId: String, artifactId: String): Future[CortexJob] = ???
}
/*
GET      /api/analyzer                  controllers.AnalyzerCtrl.list
GET      /api/analyzer/:id              controllers.AnalyzerCtrl.get(id)
POST     /api/analyzer/:id/run          controllers.AnalyzerCtrl.analyze(id)
GET      /api/analyzer/type/:dataType   controllers.AnalyzerCtrl.listForType(dataType)
GET      /api/job                       controllers.JobCtrl.list
GET      /api/job/:id                   controllers.JobCtrl.get(id)
DELETE   /api/job/:id                   controllers.JobCtrl.remove(id)
GET      /api/job/:id/report            controllers.JobCtrl.report(id)
GET      /api/job/:id/waitreport        controllers.JobCtrl.waitReport(id, atMost ?= "Inf")
*/ 