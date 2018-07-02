package connectors.cortex.services

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.http.HeaderNames
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.libs.ws.{ WSAuthScheme, WSRequest, WSResponse }
import play.api.mvc.MultipartFormData.{ DataPart, FilePart }

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models.{ Analyzer, CortexArtifact, DataArtifact, FileArtifact }
import models.HealthStatus
import services.CustomWSAPI

import org.elastic4play.NotFoundError
import org.elastic4play.utils.RichFuture

object CortexAuthentication {

  abstract class Type {
    val name: String
    def apply(request: WSRequest): WSRequest
  }

  case class Basic(username: String, password: String) extends Type {
    val name = "basic"
    def apply(request: WSRequest): WSRequest = {
      request.withAuth(username, password, WSAuthScheme.BASIC)
    }
  }

  case class Key(key: String) extends Type {
    val name = "key"
    def apply(request: WSRequest): WSRequest = {
      request.withHttpHeaders(HeaderNames.AUTHORIZATION → s"Bearer $key")
    }
  }
}

case class CortexError(status: Int, requestUrl: String, message: String) extends Exception(s"Cortex error on $requestUrl ($status) \n$message")
class CortexClient(val name: String, baseUrl: String, authentication: Option[CortexAuthentication.Type], ws: CustomWSAPI) {

  private[CortexClient] lazy val logger = Logger(getClass)

  logger.info(s"new Cortex($name, $baseUrl) authentication: ${authentication.fold("no")(_.getClass.getName)}")
  private def request[A](uri: String, f: WSRequest ⇒ Future[WSResponse], t: WSResponse ⇒ A)(implicit ec: ExecutionContext): Future[A] = {
    val request = ws.url(s"$baseUrl/$uri")
    val authenticatedRequest = authentication.fold(request)(_.apply(request))
    f(authenticatedRequest).map {
      case response if response.status / 100 == 2 ⇒ t(response)
      case error                                  ⇒ throw CortexError(error.status, s"$baseUrl/$uri", error.body)
    }
  }

  def getAnalyzer(analyzerId: String)(implicit ec: ExecutionContext): Future[Analyzer] = {
    request(s"api/analyzer/$analyzerId", _.get, _.json.as[Analyzer]).map(_.copy(cortexIds = List(name)))
      .recoverWith { case _ ⇒ getAnalyzerByName(analyzerId) } // if get analyzer using cortex2 API fails, try using legacy API
  }

  def getAnalyzerByName(analyzerName: String)(implicit ec: ExecutionContext): Future[Analyzer] = {
    val searchRequest = Json.obj(
      "query" -> Json.obj(
        "_field" -> "name",
        "_value" -> analyzerName),
      "range" -> "0-1")
    request(s"api/analyzer/_search", _.post(searchRequest),
      _.json.as[Seq[Analyzer]])
      .flatMap { analyzers ⇒
        analyzers.headOption
          .fold[Future[Analyzer]](Future.failed(NotFoundError(s"analyzer $analyzerName not found"))) { analyzer ⇒
            Future.successful(analyzer.copy(cortexIds = List(name)))
          }
      }
  }

  def listAnalyzer(implicit ec: ExecutionContext): Future[Seq[Analyzer]] = {
    request(s"api/analyzer?range=all", _.get, _.json.as[Seq[Analyzer]]).map(_.map(_.copy(cortexIds = List(name))))
  }

  def analyze(analyzerId: String, artifact: CortexArtifact)(implicit ec: ExecutionContext): Future[JsValue] = {
    artifact match {
      case FileArtifact(data, attributes) ⇒
        val body = Source(List(
          FilePart("data", (attributes \ "attachment" \ "name").asOpt[String].getOrElse("noname"), None, data),
          DataPart("_json", attributes.toString)))
        request(s"api/analyzer/$analyzerId/run", _.post(body), _.json)
      case a: DataArtifact ⇒
        request(s"api/analyzer/$analyzerId/run", _.post(Json.toJson(a)), _.json.as[JsObject])
    }
  }

  def listAnalyzerForType(dataType: String)(implicit ec: ExecutionContext): Future[Seq[Analyzer]] = {
    request(s"api/analyzer/type/$dataType", _.get, _.json.as[Seq[Analyzer]]).map(_.map(_.copy(cortexIds = List(name))))
  }

  //  def listJob(implicit ec: ExecutionContext): Future[Seq[JsObject]] = {
  //    request(s"api/job", _.get, _.json.as[Seq[JsObject]])
  //  }

  //  def getJob(jobId: String)(implicit ec: ExecutionContext): Future[JsObject] = {
  //    request(s"api/job/$jobId", _.get, _.json.as[JsObject])
  //  }

  //  def removeJob(jobId: String)(implicit ec: ExecutionContext): Future[Unit] = {
  //    request(s"api/job/$jobId", _.delete, _ ⇒ ())
  //  }

  //  def report(jobId: String)(implicit ec: ExecutionContext): Future[JsObject] = {
  //    request(s"api/job/$jobId/report", _.get, _.json.as[JsObject])
  //  }

  def waitReport(jobId: String, atMost: Duration)(implicit ec: ExecutionContext): Future[JsObject] = {
    request(s"api/job/$jobId/waitreport", _.withQueryStringParameters("atMost" → atMost.toString).get, _.json.as[JsObject])
  }

  def getVersion()(implicit system: ActorSystem, ec: ExecutionContext): Future[Option[String]] = {
    request("api/status", _.get, identity)
      .map {
        case resp if resp.status / 100 == 2 ⇒ (resp.json \ "versions" \ "Cortex").asOpt[String]
        case _                              ⇒ None
      }
      .recover { case _ ⇒ None }
      .withTimeout(1.seconds, None)
  }

  def getCurrentUser()(implicit system: ActorSystem, ec: ExecutionContext): Future[Option[String]] = {
    request("/api/user/current", _.get, identity)
      .map {
        case resp if resp.status / 100 == 2 ⇒ (resp.json \ "id").asOpt[String]
        case _                              ⇒ None
      }
      .recover { case _ ⇒ None }
      .withTimeout(1.seconds, None)
  }

  def status()(implicit system: ActorSystem, ec: ExecutionContext): Future[JsObject] =
    for {
      version ← getVersion()
      versionValue = version.getOrElse("")
      currentUser ← getCurrentUser()
      status = if (version.isDefined && currentUser.isDefined) "OK"
      else if (version.isDefined) "AUTH_ERROR"
      else "ERROR"
    } yield {
      Json.obj(
        "name" → name,
        "version" → versionValue,
        "status" → status)
    }

  def health()(implicit system: ActorSystem, ec: ExecutionContext): Future[HealthStatus.Type] = {
    getVersion()
      .map {
        case None ⇒ HealthStatus.Error
        case _    ⇒ HealthStatus.Ok
      }
  }
}