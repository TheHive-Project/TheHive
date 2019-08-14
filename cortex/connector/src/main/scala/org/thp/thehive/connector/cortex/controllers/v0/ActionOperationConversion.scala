package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.cortex.dto.v0.{CortexOperationType, CortexOutputOperation}
import org.thp.thehive.connector.cortex.models._

import scala.language.implicitConversions

object ActionOperationConversion {

  implicit def fromCortexOutputOperation(o: CortexOutputOperation): ActionOperation = o.`type` match {
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
