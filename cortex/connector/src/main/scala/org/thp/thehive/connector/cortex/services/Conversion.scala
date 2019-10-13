package org.thp.thehive.connector.cortex.services

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.{CortexJobStatus, CortexOperationType, CortexOutputArtifact, CortexOutputOperation}
import org.thp.thehive.connector.cortex.models._
import org.thp.thehive.models.Observable

object Conversion {

  implicit class CortexJobStatusOps(jobStatus: CortexJobStatus.Value) {

    def toJobStatus: JobStatus.Value =
      jobStatus match {
        case CortexJobStatus.Failure    => JobStatus.Failure
        case CortexJobStatus.InProgress => JobStatus.InProgress
        case CortexJobStatus.Success    => JobStatus.Success
        case CortexJobStatus.Unknown    => JobStatus.Unknown
        case CortexJobStatus.Waiting    => JobStatus.Waiting
      }
  }

  implicit class CortexOutputOperationOps(o: CortexOutputOperation) {

    def toActionOperation: ActionOperation = o.`type` match {
      case CortexOperationType.AddTagToCase     => AddTagToCase(o.tag.getOrElse("unknown tag"))
      case CortexOperationType.AddTagToArtifact => AddTagToArtifact(o.tag.getOrElse("unknown tag"))
      case CortexOperationType.CreateTask =>
        CreateTask(
          o.title.getOrElse("unknown title"),
          o.description.getOrElse("unknown description")
        )
      case CortexOperationType.AddCustomFields =>
        AddCustomFields(
          o.name.getOrElse("unknown name"),
          o.tpe.getOrElse("unknown tpe"),
          o.value.getOrElse("unknown value")
        )
      case CortexOperationType.CloseTask       => CloseTask()
      case CortexOperationType.MarkAlertAsRead => MarkAlertAsRead()
      case CortexOperationType.AddLogToTask    => AddLogToTask(o.content.getOrElse("unknown content"), o.owner)
      case CortexOperationType.AddArtifactToCase =>
        AddArtifactToCase(
          o.data.getOrElse("unknown data"),
          o.dataType.getOrElse("unknown dataType"),
          o.message.getOrElse("unknown message")
        )
      case CortexOperationType.AssignCase    => AssignCase(o.owner.getOrElse("unknown owner"))
      case CortexOperationType.AddTagToAlert => AddTagToAlert(o.tag.getOrElse("unknown tag"))
      case CortexOperationType.Unknown       => throw new Exception("Can't convert CortexOperationType.Unknown to ActionOperation")
    }
  }

  implicit class CortexOutputArtifactOps(artifact: CortexOutputArtifact) {

    def toObservable: Observable =
      artifact
        .into[Observable]
        .withFieldComputed(_.message, _.message)
        .withFieldComputed(_.tlp, _.tlp)
        .withFieldConst(_.ioc, false)
        .withFieldConst(_.sighted, false)
        .transform
  }
}
