package org.thp.thehive.connector.cortex.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.{OutputWorker => CortexWorker}
import org.thp.scalligraph.controllers.Renderer
import org.thp.scalligraph.models.Entity
import org.thp.thehive.connector.cortex.dto.v0._
import org.thp.thehive.connector.cortex.models._
import play.api.libs.json.{JsArray, JsFalse, Json}

object Conversion {
  import org.thp.thehive.controllers.v0.Conversion._

  implicit val actionOutput: Renderer.Aux[RichAction, OutputAction] = Renderer.json[RichAction, OutputAction](
    _.into[OutputAction]
      .withFieldRenamed(_.workerId, _.responderId)
      .withFieldRenamed(_.workerName, _.responderName)
      .withFieldRenamed(_.workerDefinition, _.responderDefinition)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldComputed(_.objectId, _.context._id)
      .withFieldComputed(_.objectType, _.context._model.label)
      .withFieldComputed(_.operations, a => JsArray(a.operations).toString)
      .withFieldComputed(_.report, _.report.map(_.toString).getOrElse("{}"))
      .transform
  )

  implicit val jobOutput: Renderer.Aux[RichJob, OutputJob] = Renderer.json[RichJob, OutputJob](job =>
    job
      .into[OutputJob]
      .withFieldComputed(_.analyzerId, _.workerId)
      .withFieldComputed(_.analyzerName, _.workerName)
      .withFieldComputed(_.analyzerDefinition, _.workerDefinition)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldComputed(_.endDate, _.endDate)
      .withFieldComputed(_.cortexId, _.cortexId)
      .withFieldComputed(_.cortexJobId, _.cortexJobId)
      .withFieldComputed(
        _.report,
        j =>
          j.report.map {
            case r if j.status == JobStatus.Success => Json.obj("success" -> true, "full" -> r, "artifacts" -> j.observables.map(_.toJson))
            case r                                  => r + ("success" -> JsFalse)
          }
      )
      .withFieldConst(_.id, job._id)
      .withFieldConst(_._type, "case_artifact_job")
      .transform
  )

  implicit val analyzerTemplateOutput: Renderer.Aux[AnalyzerTemplate with Entity, OutputAnalyzerTemplate] =
    Renderer.json[AnalyzerTemplate with Entity, OutputAnalyzerTemplate](at =>
      at.asInstanceOf[AnalyzerTemplate]
        .into[OutputAnalyzerTemplate]
        .withFieldComputed(_.analyzerId, _.workerId)
        .withFieldConst(_.id, at._id)
        .withFieldComputed(_.content, _.content)
        .transform
    )

  implicit class InputAnalyzerTemplateOps(inputAnalyzerTemplate: InputAnalyzerTemplate) {

    def toAnalyzerTemplate: AnalyzerTemplate =
      inputAnalyzerTemplate
        .into[AnalyzerTemplate]
        .withFieldRenamed(_.analyzerId, _.workerId)
        .transform
  }

  implicit val workerOutput: Renderer.Aux[(CortexWorker, Seq[String]), OutputWorker] =
    Renderer.json[(CortexWorker, Seq[String]), OutputWorker](worker =>
      worker
        ._1
        .into[OutputWorker]
        .withFieldConst(_.cortexIds, worker._2)
        .transform
    )
}
