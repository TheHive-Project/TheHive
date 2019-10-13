package org.thp.thehive.connector.cortex.controllers.v0

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.{InputReportTemplate, OutputCortexWorker, OutputReportTemplate}
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.Entity
import org.thp.thehive.connector.cortex.dto.v0.{InputAction, OutputAction, OutputJob, OutputWorker}
import org.thp.thehive.connector.cortex.models.{Action, Job, JobStatus, ReportTemplate, RichAction}
import org.thp.thehive.controllers.v0.Conversion.toObjectType
import play.api.libs.json.{JsArray, JsObject, JsValue}

object Conversion {

  implicit class InputActionOps(inputAction: InputAction) {

    def toAction: Action =
      inputAction
        .into[Action]
        .withFieldConst(_.responderName, None)
        .withFieldConst(_.responderDefinition, None)
        .withFieldConst(_.status, JobStatus.Unknown)
        .withFieldComputed(_.parameters, _.parameters.getOrElse(JsObject.empty))
        .withFieldConst(_.startDate, new Date())
        .withFieldConst(_.endDate, None)
        .withFieldConst(_.report, None)
        .withFieldConst(_.cortexJobId, None)
        .withFieldConst(_.operations, Nil)
        .withFieldComputed(_.objectType, a => toObjectType(a.objectType))
        .transform
  }

  implicit class ActionOps(action: RichAction) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputAction] =
      Output[OutputAction](
        action
          .into[OutputAction]
          .withFieldComputed(_.status, _.status.toString)
          .withFieldComputed(_.objectId, _.context._id)
          .withFieldComputed(_.objectType, _.context._model.label)
          .withFieldComputed(_.operations, a => JsArray(a.operations).toString)
          .withFieldComputed(_.report, _.report.map(_.toString).getOrElse("{}"))
          .transform
      )
  }

  implicit class JobOps(job: Job with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputJob] =
      Output[OutputJob](
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
          .withFieldConst(_.id, job._id)
          .transform
      )
  }

  implicit class ReportTemplateOps(reportTemplate: ReportTemplate with Entity) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputReportTemplate] =
      Output[OutputReportTemplate](
        reportTemplate
          .into[OutputReportTemplate]
          .withFieldComputed(_.analyzerId, _.workerId)
          .withFieldComputed(_.id, _._id)
          .withFieldComputed(_.content, _.content)
          .transform
      )
  }

  implicit class InputReportTemplateOps(inputReportTemplate: InputReportTemplate) {

    def toReportTemplate: ReportTemplate =
      inputReportTemplate
        .into[ReportTemplate]
        .withFieldComputed(_.workerId, _.analyzerId)
        .withFieldComputed(_.content, _.content)
        .transform
  }

  implicit class OutputWorkerOps(worker: (OutputCortexWorker, Seq[String])) {
    def toJson: JsValue = toOutput.toJson

    def toOutput: Output[OutputWorker] =
      Output(
        worker
          ._1
          .into[OutputWorker]
          .withFieldConst(_.cortexIds, worker._2)
          .transform
      )
  }
}
