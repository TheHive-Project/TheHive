package connectors.cortex

import scala.concurrent.ExecutionContext

import org.elastic4play.BadRequestError
import org.elastic4play.NotFoundError
import org.elastic4play.Timed
import org.elastic4play.controllers.Authenticated
import org.elastic4play.controllers.FieldsBodyParser
import org.elastic4play.controllers.Renderer
import org.elastic4play.services.Role

import connectors.cortex.models.JsonFormat._
import connectors.Connector
import javax.inject.Inject
import javax.inject.Singleton
import play.api.Logger
import play.api.http.Status
import play.api.mvc.Controller
import play.api.routing.SimpleRouter
import play.api.routing.sird.GET
import play.api.routing.sird.POST
import play.api.routing.sird.UrlContext
import connectors.cortex.services.CortexSrv

@Singleton
class CortextCtrl @Inject() (
    cortexSrv: CortexSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    implicit val ec: ExecutionContext) extends Controller with Connector with Status {
  val name = "cortex"
  val log = Logger(getClass)
  val router = SimpleRouter {
    case POST(p"/job")                           ⇒ createJob
    case GET(p"/job/$jobId<[^/]*>")              ⇒ getJob(jobId)
    case GET(p"/job")                            ⇒ listJob
    case GET(p"/analyzer/$analyzerId<[^/]*>")    ⇒ getAnalyzer(analyzerId)
    case GET(p"/analyzer/type/$dataType<[^/]*>") ⇒ getAnalyzerFor(dataType)
    case GET(p"/analyzer")                       ⇒ listAnalyzer
    case r                                       ⇒ throw NotFoundError(s"${r.uri} not found")
  }

  @Timed
  def createJob = authenticated(Role.write).async(fieldsBodyParser) { implicit request ⇒
    val analyzerId = request.body.getString("analyzerId").getOrElse(throw BadRequestError(s"analyzerId is missing"))
    val artifactId = request.body.getString("artifactId").getOrElse(throw BadRequestError(s"artifactId is missing"))
    cortexSrv.createJob(analyzerId, artifactId).map { job ⇒
      renderer.toOutput(OK, job)
    }
  }

  @Timed
  def getJob(jobId: String) = authenticated(Role.read).async { implicit request ⇒
    cortexSrv.getJob(jobId).map { job ⇒
      renderer.toOutput(OK, job)
    }
  }

  @Timed
  def listJob = authenticated(Role.read).async { implicit request ⇒
    cortexSrv.listJob.map { jobs ⇒
      renderer.toOutput(OK, jobs)
    }
  }

  @Timed
  def getAnalyzer(analyzerId: String) = authenticated(Role.read).async { implicit request ⇒
    cortexSrv.getAnalyzer(analyzerId).map { analyzer ⇒
      renderer.toOutput(OK, analyzer)
    }
  }

  @Timed
  def getAnalyzerFor(dataType: String) = authenticated(Role.read).async { implicit request ⇒
    cortexSrv.getAnalyzersFor(dataType).map { analyzers ⇒
      renderer.toOutput(OK, analyzers)
    }
  }

  @Timed
  def listAnalyzer = authenticated(Role.read).async { implicit request ⇒
    cortexSrv.listAnalyzer.map { analyzers ⇒
      renderer.toOutput(OK, analyzers)
    }
  }

  //*  POST     /api/case/artifact/:artifactId/job         controllers.JobCtrl.create(artifactId)
  //POST     /api/case/artifact/job/_stats              controllers.JobCtrl.stats()
  //POST     /api/case/artifact/job/_search             controllers.JobCtrl.find()
  //GET      /api/case/artifact/:artifactId/job         controllers.JobCtrl.findInArtifact(artifactId)
  //GET      /api/case/artifact/job/:jobId              controllers.JobCtrl.get(jobId)
  //POST     /api/analyzer/_search                      controllers.AnalyzerCtrl.find()
  //GET      /api/analyzer/:analyzerId                  controllers.AnalyzerCtrl.get(analyzerId)
  //GET      /api/analyzer/:analyzerId/report/:flavor   controllers.AnalyzerCtrl.getReport(analyzerId, flavor)

}