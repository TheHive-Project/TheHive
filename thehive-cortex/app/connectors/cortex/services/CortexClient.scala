package connectors.cortex.services

import akka.stream.scaladsl.Source
import connectors.cortex.models.{ Analyzer, CortexArtifact, DataArtifact, FileArtifact }
import connectors.cortex.models.JsonFormat._
import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.libs.ws.{ WSRequest, WSResponse }
import play.api.mvc.MultipartFormData.{ DataPart, FilePart }
import services.CustomWSAPI

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

class CortexClient(val name: String, baseUrl: String, key: String, ws: CustomWSAPI) {

  lazy val logger = Logger(getClass)

  def request[A](uri: String, f: WSRequest ⇒ Future[WSResponse], t: WSResponse ⇒ A)(implicit ec: ExecutionContext): Future[A] = {
    logger.info(s"Requesting Cortex $baseUrl")
    f(ws.url(s"$baseUrl/$uri").withHeaders("auth" → key)).map {
      case response if response.status / 100 == 2 ⇒ t(response)
      case error ⇒
        logger.error(s"Cortex error on $baseUrl (${error.status}) \n${error.body}")
        sys.error("")
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
    request(s"api/job/$jobId/waitreport", _.withQueryString("atMost" → atMost.toString).get, r ⇒ r.json.as[JsObject])
  }
}