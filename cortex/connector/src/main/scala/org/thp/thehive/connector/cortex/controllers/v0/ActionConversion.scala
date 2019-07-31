package org.thp.thehive.connector.cortex.controllers.v0

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.connector.cortex.dto.v0.{InputAction, OutputAction}
import org.thp.thehive.connector.cortex.models.{Action, ActionContext, JobStatus, RichAction}
import org.thp.thehive.connector.cortex.services.ActionSteps
import play.api.libs.json.JsObject

import scala.language.implicitConversions

object ActionConversion {

  implicit def fromInputAction(inputAction: InputAction): Action =
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
      .withFieldConst(_.operations, None)
      .withFieldComputed(_.objectType, a => toEntityType(a.objectType))
      .transform

  def toEntityType(t: String): String = t match {
    case "case"          => "Case"
    case "case_artifact" => "Observable"
    case "case_task"     => "Task"
    case "case_task_log" => "Log"
    case "alert"         => "Alert"
  }

  implicit def toOutputAction(action: RichAction): Output[OutputAction] =
    Output[OutputAction](
      action
        .into[OutputAction]
        .withFieldComputed(_.status, _.status.toString)
        .withFieldComputed(_.objectId, _.context._id)
        .withFieldComputed(_.objectType, _.context._model.label)
        .withFieldComputed(_.operations, _.operations.getOrElse(""))
        .withFieldComputed(_.report, _.report.map(_.toString).getOrElse("{}"))
        .transform
    )

  val actionProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ActionSteps]
      .property("responderId", UniMapping.string)(_.simple.readonly)
      .property("objectType", UniMapping.string)(
        _.derived(
          _.outTo[ActionContext].value[String]("_label").map(_.toLowerCase)
        ).readonly
      )
      .property("status", UniMapping.string)(_.simple.readonly)
      .property("startDate", UniMapping.date)(_.simple.readonly)
      .property("objectId", UniMapping.string)(_.simple.readonly)
      .property("responderName", UniMapping.string.optional)(_.simple.readonly)
      .property("cortexId", UniMapping.string.optional)(_.simple.readonly)
      .property("tlp", UniMapping.int.optional)(_.simple.readonly)
      .build
}
