package connectors.cortex.services

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import play.api.{ Configuration, Logger }
import play.api.http.HeaderNames
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.libs.ws.{ WSAuthScheme, WSRequest, WSResponse }
import play.api.mvc.MultipartFormData.{ DataPart, FilePart }

import akka.stream.scaladsl.Source
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models._
import javax.inject.{ Inject, Singleton }
import models.HealthStatus
import services.CustomWSAPI

import org.elastic4play.NotFoundError

object CortexConfig {
  def getCortexClient(name: String, configuration: Configuration, ws: CustomWSAPI): Option[CortexClient] = {
    val url = configuration.getOptional[String]("url").getOrElse(sys.error("url is missing")).replaceFirst("/*$", "")
    val authentication =
      configuration.getOptional[String]("key").map(CortexAuthentication.Key)
        .orElse {
          for {
            basicEnabled ← configuration.getOptional[Boolean]("basicAuth")
            if basicEnabled
            username ← configuration.getOptional[String]("username")
            password ← configuration.getOptional[String]("password")
          } yield CortexAuthentication.Basic(username, password)
        }
    Some(new CortexClient(name, url, authentication, ws))
  }

  def getInstances(configuration: Configuration, globalWS: CustomWSAPI): Seq[CortexClient] = {
    for {
      cfg ← configuration.getOptional[Configuration]("cortex").toSeq
      cortexWS = globalWS.withConfig(cfg)
      key ← cfg.subKeys
      if key != "ws"
      c ← Try(cfg.get[Configuration](key)).toOption
      instanceWS = cortexWS.withConfig(c)
      cic ← getCortexClient(key, c, instanceWS)
    } yield cic
  }
}

@Singleton
case class CortexConfig(instances: Seq[CortexClient], refreshDelay: FiniteDuration, maxRetryOnError: Int) {

  @Inject
  def this(configuration: Configuration, globalWS: CustomWSAPI) = this(
    CortexConfig.getInstances(configuration, globalWS),
    configuration.getOptional[FiniteDuration]("cortex.refreshDelay").getOrElse(1.minute),
    configuration.getOptional[Int]("cortex.maxRetryOnError").getOrElse(3))
}

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

  def getResponderById(responderId: String)(implicit ec: ExecutionContext): Future[Responder] = {
    request(s"api/responder/$responderId", _.get, _.json.as[Responder]).map(_.addCortexId(name))
  }

  def getResponderByName(responderName: String)(implicit ec: ExecutionContext): Future[Responder] = {
    val searchRequest = Json.obj(
      "query" → Json.obj(
        "_field" → "name",
        "_value" → responderName),
      "range" → "0-1")
    request(s"api/responder/_search", _.post(searchRequest),
      _.json.as[Seq[Responder]])
      .flatMap { analyzers ⇒
        analyzers.headOption
          .fold[Future[Responder]](Future.failed(NotFoundError(s"responder $responderName not found"))) { responder ⇒
            Future.successful(responder.addCortexId(name))
          }
      }
  }

  def getAnalyzerByName(analyzerName: String)(implicit ec: ExecutionContext): Future[Analyzer] = {
    val searchRequest = Json.obj(
      "query" → Json.obj(
        "_field" → "name",
        "_value" → analyzerName),
      "range" → "0-1")
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

  def findResponders(query: JsObject)(implicit ec: ExecutionContext): Future[Seq[Responder]] = {
    request(s"api/responder/_search?range=all", _.post(Json.obj("query" → query)), _.json.as[Seq[Responder]]).map(_.map(_.addCortexId(name)))
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

  def execute(
      responderId: String,
      label: String,
      dataType: String,
      data: JsValue,
      tlp: Long,
      pap: Long,
      message: String,
      parameters: JsObject)(implicit ec: ExecutionContext): Future[JsValue] = {
    val body = Json.obj(
      "label" → label,
      "data" → data,
      "dataType" → dataType,
      "tlp" → tlp,
      "pap" → pap,
      "message" → message,
      "parameters" → parameters)
    request(s"api/responder/$responderId/run", _.post(body), _.json.as[JsObject])
  }

  def listAnalyzerForType(dataType: String)(implicit ec: ExecutionContext): Future[Seq[Analyzer]] = {
    request(s"api/analyzer/type/$dataType", _.get, _.json.as[Seq[Analyzer]]).map(_.map(_.copy(cortexIds = List(name))))
  }

  def waitReport(jobId: String, atMost: Duration)(implicit ec: ExecutionContext): Future[JsObject] = {
    request(s"api/job/$jobId/waitreport", _.withQueryStringParameters("atMost" → atMost.toString).get, _.json.as[JsObject])
  }

  def getVersion()(implicit ec: ExecutionContext): Future[Option[String]] = {
    request("api/status", _.get, identity)
      .map {
        case resp if resp.status / 100 == 2 ⇒ (resp.json \ "versions" \ "Cortex").asOpt[String]
        case _                              ⇒ None
      }
      .recover { case _ ⇒ None }
  }

  def getCurrentUser()(implicit ec: ExecutionContext): Future[Option[String]] = {
    request("api/user/current", _.get, identity)
      .map {
        case resp if resp.status / 100 == 2 ⇒ (resp.json \ "id").asOpt[String]
        case _                              ⇒ None
      }
      .recover { case _ ⇒ None }
  }

  def status()(implicit ec: ExecutionContext): Future[JsObject] =
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

  def health()(implicit ec: ExecutionContext): Future[HealthStatus.Type] = {
    getVersion()
      .map {
        case None ⇒ HealthStatus.Error
        case _    ⇒ HealthStatus.Ok
      }
  }
}