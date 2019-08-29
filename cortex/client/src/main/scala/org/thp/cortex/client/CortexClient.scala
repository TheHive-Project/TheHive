package org.thp.cortex.client

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{Format, JsObject, JsString, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClientConfig
import play.api.mvc.MultipartFormData.{DataPart, FilePart}

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.thp.client._
import org.thp.cortex.dto.v0.{Attachment, _}

case class CortexClientConfig(
    name: String,
    url: String,
    includedTheHiveOrganisations: Seq[String] = Seq("*"),
    excludedTheHiveOrganisations: Seq[String] = Nil,
    wsConfig: ProxyWSConfig = ProxyWSConfig(AhcWSClientConfig(), None),
    auth: Authentication
)

object CortexClientConfig {
  implicit val format: Format[CortexClientConfig] = Json.using[Json.WithDefaultValues].format[CortexClientConfig]
}

class CortexClient(
    val name: String,
    val baseUrl: String,
    val includedTheHiveOrganisations: Seq[String],
    val excludedTheHiveOrganisations: Seq[String],
    implicit val ws: WSClient,
    implicit val auth: Authentication
) {

  def this(config: CortexClientConfig, mat: Materializer) =
    this(
      config.name,
      config.url,
      config.includedTheHiveOrganisations,
      config.excludedTheHiveOrganisations,
      new ProxyWS(config.wsConfig, mat),
      config.auth
    )

  lazy val job            = new BaseClient[CortexInputJob, CortexOutputJob](s"$strippedUrl/api/job")
  lazy val analyser       = new BaseClient[InputCortexWorker, OutputCortexWorker](s"$strippedUrl/api/analyzer")
  lazy val responder      = new BaseClient[InputCortexWorker, OutputCortexWorker](s"$strippedUrl/api/responder")
  lazy val logger         = Logger(getClass)
  val strippedUrl: String = baseUrl.replaceFirst("/*$", "")

  /**
    * GET analysers endpoint
    *
    * @return
    */
  def listAnalyser: Future[Seq[OutputCortexWorker]] = analyser.list()

  /**
    * GET analyzer by id
    *
    * @param id guess
    * @return
    */
  def getAnalyzer(id: String): Future[OutputCortexWorker] = analyser.get(id)

  /**
    * GET analyzer by dataType
    *
    * @param dataType guess
    * @return
    */
  def listAnalyzersByType(dataType: String): Future[Seq[OutputCortexWorker]] = analyser.list(s"/type/$dataType")

  /**
    * Search an analyzer by name
    *
    * @param analyzerName the name to search for
    * @return
    */
  def getAnalyzerByName(analyzerName: String): Future[OutputCortexWorker] =
    analyser
      .search[SearchQuery](SearchQuery("name", analyzerName, "0-1"))
      .map(_.headOption.getOrElse(throw ApplicationError(404, JsString(s"Analyzer $analyzerName not found"))))

  /**
    * Gets the job status and report if complete
    *
    * @param jobId the cortex job id
    * @param atMost the time that Cortex has to wait before sending a response
    *               (in case the job terminates in the meantime)
    * @return
    */
  def getReport(jobId: String, atMost: Duration): Future[CortexOutputJob] = job.get(jobId, s"/waitreport?atMost=$atMost")

  /**
    * Submits an artifact for analyze with the appropriate analyzer selection
    *
    * @param analyzerId the analyzer to invoke
    * @param artifact the artifact to analyze
    * @return
    */
  def analyse(analyzerId: String, artifact: InputCortexArtifact): Future[CortexOutputJob] = {
    val requestBody = Json.toJson(artifact)
    val result = artifact.attachment match {
      case None =>
        auth(ws.url(s"$strippedUrl/api/analyzer/$analyzerId/run"))
          .post(requestBody)
      case Some(Attachment(filename, size, contentType, data)) =>
        auth(ws.url(s"$strippedUrl/api/analyzer/$analyzerId/run"))
          .post(
            Source(
              List(
                FilePart("data", filename, Some(contentType), data, size),
                DataPart("_json", requestBody.toString)
              )
            )
          )
    }
    result.transform {
      case Success(r) if r.status == Status.CREATED => Success(r.json.as[CortexOutputJob])
      case Success(r)                               => Try(r.json.as[CortexOutputJob])
      case Failure(t)                               => throw t
    }
  }

  /**
    * Gets an artifact attachment from id
    *
    * @param id the id the look for
    * @return
    */
  def getAttachment(id: String): Future[Source[ByteString, _]] =
    auth(ws.url(s"$strippedUrl/api/datastore/$id"))
      .stream()
      .map(r => r.bodyAsSource)

  /**
    * Gets a responder by id
    *
    * @param id the id to look for
    * @return
    */
  def getResponder(id: String): Future[OutputCortexWorker] = responder.get(id)

  /**
    * Search a responder by name
    *
    * @param responderName the name to search for
    * @return
    */
  def getResponderByName(responderName: String): Future[OutputCortexWorker] =
    responder
      .search[SearchQuery](SearchQuery("name", responderName, "0-1"))
      .map(_.headOption.getOrElse(throw ApplicationError(404, JsString(s"Responder $responderName not found"))))

  /**
    * Search a responder by entity type
    *
    * @param entityType the type to search for
    * @return
    */
  def getRespondersByType(entityType: String): Future[Seq[OutputCortexWorker]] =
    responder
      .search[SearchQuery](SearchQuery("dataTypeList", s"thehive:$entityType", "0-200"))

  /**
    * Search responders according to a formatted query
    * @param query the query that should look like {query: {...}}
    * @return
    */
  def searchResponders(query: JsObject): Future[Seq[OutputCortexWorker]] =
    responder
      .search[SearchQuery](SearchQuery("", "", "all", Some(query)))

  /**
    * Materializes an action as a job on Cortex client server
    *
    * @param responderId the responsible responder
    * @param action the action to execute
    * @return
    */
  def execute(responderId: String, action: InputCortexAction): Future[CortexOutputJob] = {
    val requestBody = Json.toJson(action)
    val result      = auth(ws.url(s"$strippedUrl/api/responder/$responderId/run")).post(requestBody)

    result.transform {
      case Success(r) if r.status == Status.CREATED => Success(r.json.as[CortexOutputJob])
      case Success(r)                               => Try(r.json.as[CortexOutputJob])
      case Failure(t)                               => throw t
    }
  }

  /**
    * Retrieve the name of the cortex user
    * @return user name
    */
  def getCurrentUser: Future[String] =
    auth(ws.url(s"$strippedUrl/api/user/current"))
      .get
      .transform {
        case Success(r) if r.status == Status.OK => Try((r.json \ "id").as[String])
        case Success(r)                          => Failure(ApplicationError(r))
        case Failure(t)                          => throw t
      }

  def getHealth: Future[String] = getVersion.transform {
    case _: Success[_] => Success("Ok")
    case _             => Success("Error")
  }

  /**
    * Retrieve version of remote cortex
    * @return version of cortex
    */
  def getVersion: Future[String] =
    auth(ws.url(s"$strippedUrl/api/status"))
      .get
      .transform {
        case Success(r) if r.status == Status.OK => Try((r.json \ "versions" \ "Cortex").as[String])
        case Success(r)                          => Failure(ApplicationError(r))
        case Failure(t)                          => throw t
      }
}
