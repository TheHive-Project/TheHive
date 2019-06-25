package org.thp.cortex.client

import akka.actor.ActorSystem
import org.thp.cortex.dto.v0._
import org.thp.scalligraph.{DelayRetry, Retry}
import play.api.Logger
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}

import akka.stream.scaladsl.Source
import org.thp.cortex.client.models.{Artifact, Attachment, InputCortexAnalyzer, OutputCortexAnalyzer}
import org.thp.cortex.client.v0.Conversion

class CortexClient(val name: String, baseUrl: String, refreshDelay: FiniteDuration, maxRetryOnError: Int)(
    implicit ws: CustomWSAPI,
    auth: Authentication,
    system: ActorSystem,
    ec: ExecutionContext
) extends Conversion {
  lazy val job            = new BaseClient[InputArtifact, OutputJob](s"$baseUrl/api/job")
  lazy val analyser       = new BaseClient[InputCortexAnalyzer, OutputCortexAnalyzer](s"$baseUrl/api/analyzer")
  lazy val logger         = Logger(getClass)
  val retrier: DelayRetry = Retry(maxRetryOnError).delayed(refreshDelay)(system.scheduler, ec)

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
  def getAnalyzer(id: String): Future[OutputCortexAnalyzer] = analyser.get(id).map(_.copy(cortexIds = Some(List(name))))

  def analyse(analyzerId: String, artifact: Artifact): Future[OutputJob] = {
    val requestBody = Json.toJson(artifact.toInputArtifact)
    val result = artifact.attachment match {
      case None ⇒
        auth(ws.url(s"api/analyzer/$analyzerId/run"))
          .post(requestBody)
      case Some(Attachment(filename, size, contentType, data)) ⇒
        auth(ws.url(s"api/analyzer/$analyzerId/run"))
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
      case Success(r) if r.status == Status.CREATED ⇒ Success(r.body[JsValue].as[OutputJob])
      case Success(r)                               ⇒ Failure(ApplicationError(r))
      case Failure(t)                               ⇒ throw t
    }
  }
}
