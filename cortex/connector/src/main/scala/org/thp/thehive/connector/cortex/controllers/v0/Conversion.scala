package org.thp.thehive.connector.cortex.controllers.v0

import java.util.Date

import play.api.libs.json.{JsArray, JsObject}

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.{OutputWorker => CortexWorker}
import org.thp.scalligraph.controllers.Outputer
import org.thp.scalligraph.models.Entity
import org.thp.thehive.connector.cortex.dto.v0.{InputAction, InputAnalyzerTemplate, OutputAction, OutputAnalyzerTemplate, OutputJob, OutputWorker}
import org.thp.thehive.connector.cortex.models._
import org.thp.thehive.controllers.v0.Conversion.toObjectType

object Conversion {
  implicit class InputActionOps(inputAction: InputAction) {

    def toAction: Action =
      inputAction
        .into[Action]
        .withFieldConst(_.responderName, None)
        .withFieldConst(_.responderDefinition, None)
        .withFieldConst(_.status, JobStatus.Waiting)
        .withFieldComputed(_.parameters, _.parameters.getOrElse(JsObject.empty))
        .withFieldConst(_.startDate, new Date())
        .withFieldConst(_.endDate, None)
        .withFieldConst(_.report, None)
        .withFieldConst(_.cortexJobId, None)
        .withFieldConst(_.operations, Nil)
        .withFieldComputed(_.objectType, a => toObjectType(a.objectType))
        .transform
  }

  implicit val actionOutput: Outputer.Aux[RichAction, OutputAction] = Outputer[RichAction, OutputAction](
    _.into[OutputAction]
      .withFieldComputed(_.status, _.status.toString)
      .withFieldComputed(_.objectId, _.context._id)
      .withFieldComputed(_.objectType, _.context._model.label)
      .withFieldComputed(_.operations, a => JsArray(a.operations).toString)
      .withFieldComputed(_.report, _.report.map(_.toString).getOrElse("{}"))
      .transform
  )

  implicit val jobOutput: Outputer.Aux[Job with Entity, OutputJob] = Outputer[Job with Entity, OutputJob](
    job =>
      job
        .asInstanceOf[Job]
        .into[OutputJob]
        .withFieldComputed(_.analyzerId, _.workerId)
        .withFieldComputed(_.analyzerName, _.workerName)
        .withFieldComputed(_.analyzerDefinition, _.workerDefinition)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldComputed(_.endDate, _.endDate)
        .withFieldComputed(_.cortexId, _.cortexId)
        .withFieldComputed(_.cortexJobId, _.cortexJobId)
        .withFieldConst(_.report, None)
        .withFieldConst(_.id, job._id)
        .transform
  )

  implicit val analyzerTemplateOutput: Outputer.Aux[AnalyzerTemplate with Entity, OutputAnalyzerTemplate] =
    Outputer[AnalyzerTemplate with Entity, OutputAnalyzerTemplate](
      _.into[OutputAnalyzerTemplate]
        .withFieldComputed(_.analyzerId, _.workerId)
        .withFieldComputed(_.id, _._id)
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

  implicit val workerOutput: Outputer.Aux[(CortexWorker, Seq[String]), OutputWorker] =
    Outputer[(CortexWorker, Seq[String]), OutputWorker](
      worker =>
        worker
          ._1
          .into[OutputWorker]
          .withFieldConst(_.cortexIds, worker._2)
          .transform
    )
}
