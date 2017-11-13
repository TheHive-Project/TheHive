package connectors.cortex.services

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.libs.ws.{ WSAuthScheme, WSRequest, WSResponse }
import play.api.mvc.MultipartFormData.{ DataPart, FilePart }

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models.{ Analyzer, CortexArtifact, DataArtifact, FileArtifact }
import models.HealthStatus
import services.CustomWSAPI

import org.elastic4play.utils.RichFuture

case class CortexError(status: Int, requestUrl: String, message: String) extends Exception(s"Cortex error on $requestUrl ($status) \n$message")
class CortexClient(val name: String, baseUrl: String, key: String, authentication: Option[(String, String)], ws: CustomWSAPI) {

  private[CortexClient] lazy val logger = Logger(getClass)

  logger.info(s"new Cortex($name, $baseUrl, $key) Basic Auth enabled: ${authentication.isDefined}")
  def request[A](uri: String, f: WSRequest ⇒ Future[WSResponse], t: WSResponse ⇒ A)(implicit ec: ExecutionContext): Future[A] = {
    val requestBuilder = ws.url(s"$baseUrl/$uri").withHttpHeaders("auth" → key)
    val authenticatedRequestBuilder = authentication.fold(requestBuilder) {
      case (username, password) ⇒ requestBuilder.withAuth(username, password, WSAuthScheme.BASIC)
    }
    f(authenticatedRequestBuilder).map {
      case response if response.status / 100 == 2 ⇒ t(response)
      case error                                  ⇒ throw CortexError(error.status, s"$baseUrl/$uri", error.body)
    }
  }

  def getAnalyzer(analyzerId: String)(implicit ec: ExecutionContext): Future[Analyzer] = {
    request(s"api/analyzer/$analyzerId", _.get, _.json.as[Analyzer]).map(_.copy(cortexIds = List(name)))
  }

  def listAnalyzer(implicit ec: ExecutionContext): Future[Seq[Analyzer]] = {
    request(s"api/analyzer", _.get, _.json.as[Seq[Analyzer]]).map(_.map(_.copy(cortexIds = List(name))))
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

  def listJob(implicit ec: ExecutionContext): Future[Seq[JsObject]] = {
    request(s"api/job", _.get, _.json.as[Seq[JsObject]])
  }

  def getJob(jobId: String)(implicit ec: ExecutionContext): Future[JsObject] = {
    request(s"api/job/$jobId", _.get, _.json.as[JsObject])
  }

  def removeJob(jobId: String)(implicit ec: ExecutionContext): Future[Unit] = {
    request(s"api/job/$jobId", _.delete, _ ⇒ ())
  }

  def report(jobId: String)(implicit ec: ExecutionContext): Future[JsObject] = {
    request(s"api/job/$jobId/report", _.get, _.json.as[JsObject])
  }

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

  def status()(implicit system: ActorSystem, ec: ExecutionContext): Future[JsObject] =
    getVersion()
      .map {
        case Some(version) ⇒ Json.obj(
          "name" → name,
          "version" → version,
          "status" → "OK")
        case None ⇒ Json.obj(
          "name" → name,
          "version" → "",
          "status" → "ERROR")
      }

  def health()(implicit system: ActorSystem, ec: ExecutionContext): Future[HealthStatus.Type] = {
    getVersion()
      .map {
        case None ⇒ HealthStatus.Error
        case _    ⇒ HealthStatus.Ok
      }
  }
}