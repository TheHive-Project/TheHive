package connectors.cortex.controllers

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc._
import play.api.routing.SimpleRouter
import play.api.routing.sird.{ DELETE, GET, PATCH, POST, UrlContext }

import akka.actor.ActorSystem

import org.elastic4play.{ BadRequestError, NotFoundError, Timed }
import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.{ Agg, AuxSrv, QueryDSL, QueryDef }
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }
import connectors.Connector
import connectors.cortex.models.JsonFormat.{ analyzerFormat, workerFormat }
import connectors.cortex.services.{ CortexActionSrv, CortexAnalyzerSrv, CortexConfig }
import models.HealthStatus.Type
import models.{ HealthStatus, Roles }

@Singleton
class CortexCtrl @Inject() (
    reportTemplateCtrl: ReportTemplateCtrl,
    cortexConfig: CortexConfig,
    cortexAnalyzerSrv: CortexAnalyzerSrv,
    cortexActionSrv: CortexActionSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    fieldsBodyParser: FieldsBodyParser,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext,
    implicit val system: ActorSystem) extends AbstractController(components) with Connector with Status {

  val name = "cortex"
  private[CortexCtrl] lazy val logger = Logger(getClass)

  override def status: Future[JsObject] =
    Future.traverse(cortexConfig.instances)(instance ⇒ instance.status())
      .map { statusDetails ⇒
        val distinctStatus = statusDetails.map(s ⇒ (s \ "status").as[String]).toSet
        val healthStatus = if (distinctStatus.contains("OK")) {
          if (distinctStatus.size > 1) "WARNING" else "OK"
        }
        else "ERROR"
        Json.obj(
          "enabled" → true,
          "servers" → statusDetails,
          "status" → healthStatus)
      }

  override def health: Future[Type] = {
    Future.traverse(cortexConfig.instances)(instance ⇒ instance.health())
      .map { healthStatus ⇒
        val distinctStatus = healthStatus.toSet
        if (distinctStatus.contains(HealthStatus.Ok)) {
          if (distinctStatus.size > 1) HealthStatus.Warning else HealthStatus.Ok
        }
        else if (distinctStatus.contains(HealthStatus.Error)) HealthStatus.Error
        else HealthStatus.Warning
      }
  }

  val router = SimpleRouter {
    case POST(p"/job") ⇒ createJob
    case GET(p"/job/$jobId<[^/]*>") ⇒ getJob(jobId)
    case POST(p"/job/_search") ⇒ findJob
    case POST(p"/job/_stats") ⇒ statsJob

    case GET(p"/analyzer/$analyzerId<[^/]*>") ⇒ getAnalyzer(analyzerId)
    case GET(p"/analyzer/type/$dataType<[^/]*>") ⇒ getAnalyzerFor(dataType)
    case GET(p"/analyzer") ⇒ listAnalyzer

    case GET(p"/worker/$workerId<[^/]*>") ⇒ getWorker(workerId)
    case GET(p"/worker") ⇒ findWorker
    case POST(p"/worker/_search") ⇒ findWorker
    case GET(p"/worker/$entityType<[^/]*>/$entityId<[^/]*>") ⇒ getWorkers(entityType, entityId)

    case POST(p"/action") ⇒ createAction
    case GET(p"/action") ⇒ findAction
    case POST(p"/action/_search") ⇒ findAction
    case GET(p"/action/$entityType<[^/]*>/$entityId<[^/]*>") ⇒ getActions(entityType, entityId)
    case GET(p"/action/$actionId<[^/]*>") ⇒ getAction(actionId)

    case POST(p"/report/template/_search") ⇒ reportTemplateCtrl.find()
    case POST(p"/report/template") ⇒ reportTemplateCtrl.create()
    case GET(p"/report/template/$caseTemplateId<[^/]*>") ⇒ reportTemplateCtrl.get(caseTemplateId)
    case PATCH(p"/report/template/$caseTemplateId<[^/]*>") ⇒ reportTemplateCtrl.update(caseTemplateId)
    case DELETE(p"/report/template/$caseTemplateId<[^/]*>") ⇒ reportTemplateCtrl.delete(caseTemplateId)
    case GET(p"/report/template/content/$analyzerId<[^/]*>/$reportType<[^/]*>") ⇒ reportTemplateCtrl.getContent(analyzerId, reportType)
    case POST(p"/report/template/_import") ⇒ reportTemplateCtrl.importTemplatePackage
    case r ⇒ throw NotFoundError(s"${r.uri} not found")
  }

  @Timed
  def createJob: Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    val analyzerId = request.body.getString("analyzerId").getOrElse(throw BadRequestError(s"analyzerId is missing"))
    val artifactId = request.body.getString("artifactId").getOrElse(throw BadRequestError(s"artifactId is missing"))
    val cortexId = request.body.getString("cortexId")
    cortexAnalyzerSrv.submitJob(cortexId, analyzerId, artifactId).map { job ⇒
      renderer.toOutput(OK, job)
    }
  }

  @Timed
  def getJob(jobId: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val withStats = request.body.getBoolean("nstats").getOrElse(false)
    for {
      job ← cortexAnalyzerSrv.getJob(jobId)
      jobJson = job.toJson
      jobWithStats ← if (withStats) cortexAnalyzerSrv.addImportFieldInArtifacts(jobJson) else Future.successful(Json.toJson(job))
    } yield Ok(jobWithStats)
  }

  @Timed
  def findJob: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (jobs, total) = cortexAnalyzerSrv.find(query, range, sort)
    val jobWithoutReport = auxSrv.apply(jobs, nparent, withStats, removeUnaudited = true)
    renderer.toOutput(OK, jobWithoutReport, total)
  }

  @Timed
  def statsJob: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query")
      .fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request.body.getValue("stats")
      .getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    cortexAnalyzerSrv.stats(query, aggs).map(s ⇒ Ok(s))
  }

  @Timed
  def getAnalyzer(analyzerId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    cortexAnalyzerSrv.getAnalyzer(analyzerId).map { analyzer ⇒
      renderer.toOutput(OK, analyzer)
    }
  }

  @Timed
  def getAnalyzerFor(dataType: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    cortexAnalyzerSrv.getAnalyzersFor(dataType).map { analyzers ⇒
      renderer.toOutput(OK, analyzers)
    }
  }

  @Timed
  def listAnalyzer: Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    cortexAnalyzerSrv.listAnalyzer.map { analyzers ⇒
      renderer.toOutput(OK, analyzers)
    }
  }

  def getWorker(workerId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    cortexActionSrv.getWorkerById(workerId).map { worker ⇒
      renderer.toOutput(OK, worker)
    }
  }

  def getWorkers(entityType: String, entityId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    val query = Json.obj(
      "dataTypeList" -> s"thehive:$entityType")
    cortexActionSrv.findWorkers(query).map { workers ⇒
      renderer.toOutput(OK, workers)
    }
  }

  def findWorker: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query") match {
      case Some(o: JsObject) ⇒ o
      case _                 ⇒ JsObject.empty
    }
    cortexActionSrv.findWorkers(query).map { workers ⇒
      renderer.toOutput(OK, workers)
    }
  }

  def createAction: Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    cortexActionSrv.executeAction(request.body).map { action ⇒
      renderer.toOutput(OK, action)
    }
  }

  def findAction: Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (actions, total) = cortexActionSrv.find(query, range, sort)
    renderer.toOutput(OK, actions, total)
  }

  def getActions(entityType: String, entityId: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val (actions, total) = cortexActionSrv.find(and("objectType" ~= entityType, "objectId" ~= entityId), range, sort)
    renderer.toOutput(OK, actions, total)
  }

  def getAction(actionId: String): Action[AnyContent] = authenticated(Roles.read).async { implicit request ⇒
    cortexActionSrv.getAction(actionId).map { action ⇒
      renderer.toOutput(OK, action)
    }
  }
}
