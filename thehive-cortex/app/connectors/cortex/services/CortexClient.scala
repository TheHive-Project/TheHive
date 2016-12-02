package connectors.cortex.services

import scala.concurrent.{ ExecutionContext, Future }

import akka.stream.scaladsl.{ FileIO, Source }

import play.api.libs.json.{ JsValue, Json }
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }
import play.api.mvc.MultipartFormData.{ DataPart, FilePart }

import org.elastic4play.models.JsonFormat._
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models.Analyzer
import connectors.cortex.models.FileArtifact
import connectors.cortex.models.DataArtifact
import connectors.cortex.models.Job
import connectors.cortex.models.CortexArtifact
import play.api.libs.json.JsObject

class CortexClient(name: String, baseUrl: String, key: String) {
  def request[A](uri: String, f: WSRequest ⇒ Future[WSResponse], t: WSResponse ⇒ A)(implicit ws: WSClient, ec: ExecutionContext) = {
    f(ws.url(baseUrl + "/" + uri).withHeaders("auth" → key)).map {
      case response if response.status / 100 == 2 ⇒ t(response)
      case error                                  ⇒ ???
    }
  }

  def getAnalyzer(analyzerId: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/analyzer/$analyzerId", _.get, _.json.as[Analyzer])
  }

  def listAnalyzer(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/analyzer", _.get, _.json.as[Seq[Analyzer]])
  }

  def analyze(analyzerId: String, artifact: CortexArtifact)(implicit ws: WSClient, ec: ExecutionContext) = {
    artifact match {
      case FileArtifact(file, attributes) ⇒
        val body = Source(FilePart("data", file.getName, None, FileIO.fromPath(file.toPath)) :: DataPart("_json", attributes.toString) :: Nil)
        request(s"/api/analyzer/$analyzerId", _.post(body), _.json)
      case a: DataArtifact ⇒
        request(s"/api/analyzer/$analyzerId", _.post(Json.toJson(a)), _.json.as[JsObject])
    }
  }

  def listAnalyzerForType(dataType: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/analyzer/type/$dataType", _.get, _.json.as[Seq[Analyzer]])
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

  def waitReport(jobId: String, atMost: String)(implicit ws: WSClient, ec: ExecutionContext) = {
    request(s"/api/job/$jobId/waitreport", _.get, r ⇒ r.json.as[JsObject])
  }
}