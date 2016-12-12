package connectors.cortex.services

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration

import akka.stream.scaladsl.Source

import play.api.libs.json.{ JsObject, Json }
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }
import play.api.mvc.MultipartFormData.{ DataPart, FilePart }

import org.elastic4play.NotFoundError

import connectors.cortex.models.{ Analyzer, CortexArtifact, DataArtifact, FileArtifact }
import connectors.cortex.models.JsonFormat._
import play.api.Logger

class CortexClient(val name: String, baseUrl: String, key: String) {

  lazy val logger = Logger(getClass)

  logger.info(s"new Cortex($name, $baseUrl, $key)")
  def request[A](uri: String, f: WSRequest ⇒ Future[WSResponse], t: WSResponse ⇒ A)(implicit ws: WSClient, ec: ExecutionContext): Future[A] = {
    val url = (baseUrl + uri)
    logger.info(s"Requesting Cortex $url")
    f(ws.url(url).withHeaders("auth" → key)).map {
      case response if response.status / 100 == 2 ⇒ t(response)
      case error ⇒
        logger.error(s"Cortex error on $url (${error.status}) \n${error.body}")
        sys.error("")
    }
  }

  def getAnalyzer(analyzerId: String)(implicit ws: WSClient, ec: ExecutionContext): Future[Analyzer] = {
    request(s"/api/analyzer/$analyzerId", _.get, _.json.as[Analyzer]).map(_.copy(cortexIds = List(name)))
  }

  def listAnalyzer(implicit ws: WSClient, ec: ExecutionContext): Future[Seq[Analyzer]] = {
    request(s"/api/analyzer", _.get, _.json.as[Seq[Analyzer]]).map(_.map(_.copy(cortexIds = List(name))))
  }

  def analyze(analyzerId: String, artifact: CortexArtifact)(implicit ws: WSClient, ec: ExecutionContext) = {
    artifact match {
      case FileArtifact(data, attributes) ⇒
        val body = Source(List(
          FilePart("data", (attributes \ "attachment" \ "name").asOpt[String].getOrElse("noname"), None, data),
          DataPart("_json", attributes.toString)))
        request(s"/api/analyzer/$analyzerId/run", _.post(body), _.json)
      case a: DataArtifact ⇒
        request(s"/api/analyzer/$analyzerId/run", _.post(Json.toJson(a)), _.json.as[JsObject])
    }
  }

  def listAnalyzerForType(dataType: String)(implicit ws: WSClient, ec: ExecutionContext): Future[Seq[Analyzer]] = {
    request(s"/api/analyzer/type/$dataType", _.get, _.json.as[Seq[Analyzer]]).map(_.map(_.copy(cortexIds = List(name))))
  }

  def listJob(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job", _.get, _.json.as[Seq[JsObject]])
  }

  def getJob(jobId: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId", _.get, _.json.as[JsObject])
  }

  def removeJob(jobId: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId", _.delete, _ ⇒ ())
  }

  def report(jobId: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId/report", _.get, r ⇒ r.json.as[JsObject])
  }

  def waitReport(jobId: String, atMost: Duration)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId/waitreport", _.withQueryString("atMost" → atMost.toString).get, r ⇒ r.json.as[JsObject])
  }
}