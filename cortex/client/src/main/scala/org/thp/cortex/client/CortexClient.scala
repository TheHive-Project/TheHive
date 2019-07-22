package org.thp.cortex.client

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.thp.cortex.dto.v0.{Attachment, _}
import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

class CortexClient(val name: String, baseUrl: String)(
    implicit ws: CustomWSAPI,
    auth: Authentication
) {
  lazy val job            = new BaseClient[CortexInputJob, CortexOutputJob](s"$strippedUrl/api/job")
  lazy val analyser       = new BaseClient[InputCortexAnalyzer, OutputCortexAnalyzer](s"$strippedUrl/api/analyzer")
  lazy val responder      = new BaseClient[InputCortexResponder, OutputCortexResponder](s"$strippedUrl/api/responder")
  lazy val logger         = Logger(getClass)
  val strippedUrl: String = baseUrl.replaceFirst("/*$", "")

  /**
    * GET analysers endpoint
    *
    * @return
    */
  def listAnalyser: Future[Seq[OutputCortexAnalyzer]] = analyser.list.map(_.map(_.copy(cortexIds = Some(List(name)))))

  /**
    * GET analyzer by id
    *
    * @param id guess
    * @return
    */
  def getAnalyzer(id: String): Future[OutputCortexAnalyzer] = analyser.get(id, None).map(_.copy(cortexIds = Some(List(name))))

  /**
    * Search an analyzer by name
    *
    * @param analyzerName the name to search for
    * @return
    */
  def getAnalyzerByName(analyzerName: String): Future[OutputCortexAnalyzer] =
    analyser
      .search[SearchQuery](SearchQuery("name", analyzerName, "0-1"))
      .flatMap(l => Future.fromTry(Try(l.head.copy(cortexIds = Some(List(name))))))

  /**
    * Gets the job status and report if complete
    *
    * @param jobId the cortex job id
    * @param atMost the time that Cortex has to wait before sending a response
    *               (in case the job terminates in the meantime)
    * @return
    */
  def getReport(jobId: String, atMost: Duration): Future[CortexOutputJob] = job.get(jobId, Some(s"/waitreport?atMost=${atMost.toString}"))

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
      case Success(r) if r.status == Status.CREATED => Success(r.body[JsValue].as[CortexOutputJob])
      case Success(r)                               => Try(r.body[JsValue].as[CortexOutputJob])
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
      .get()
      .map(r => r.bodyAsSource)

  /**
    * Gets a responder by id
    *
    * @param id the id to look for
    * @return
    */
  def getResponder(id: String): Future[OutputCortexResponder] = responder.get(id, None).map(_.addCortexId(name))

  /**
    * Search a responder by name
    *
    * @param responderName the name to search for
    * @return
    */
  def getResponderByName(responderName: String): Future[OutputCortexResponder] =
    responder
      .search[SearchQuery](SearchQuery("name", responderName, "0-1"))
      .flatMap(l => Future.fromTry(Try(l.head.addCortexId(name))))

  /**
    * Search a responder by entity type
    *
    * @param entityType the type to search for
    * @return
    */
  def getRespondersByType(entityType: String): Future[Seq[OutputCortexResponder]] =
    responder
      .search[SearchQuery](SearchQuery("dataTypeList", s"thehive:$entityType", "0-200"))
      .map(l => l.map(_.addCortexId(name)))

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
      case Success(r) if r.status == Status.CREATED => Success(r.body[JsValue].as[CortexOutputJob])
      case Success(r)                               => Try(r.body[JsValue].as[CortexOutputJob])
      case Failure(t)                               => throw t
    }
  }
}
