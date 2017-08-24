package connectors.cortex.services

import akka.stream.scaladsl.Source
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models.{ Analyzer, CortexArtifact, DataArtifact, FileArtifact }
import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.libs.ws.{ WSAuthScheme, WSRequest, WSResponse }
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue
import play.api.mvc.MultipartFormData.{ DataPart, FilePart }
import services.CustomWSAPI

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

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
    request(s"api/job/$jobId/report", _.get, r ⇒ r.json.as[JsObject])
  }

  def waitReport(jobId: String, atMost: Duration)(implicit ec: ExecutionContext): Future[JsObject] = {
    request(s"api/job/$jobId/waitreport", _.withQueryStringParameters("atMost" → atMost.toString).get, r ⇒ r.json.as[JsObject])
  }
}
