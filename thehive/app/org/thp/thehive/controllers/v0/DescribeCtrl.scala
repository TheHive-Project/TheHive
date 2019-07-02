package org.thp.thehive.controllers.v0

import java.lang.{Boolean => JBoolean}
import java.util.Date

import scala.util.Success

import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.Hash
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.query.PublicProperty
import org.thp.thehive.services.{CaseSrv, TaskSrv, UserSrv}

@Singleton
class DescribeCtrl @Inject()(entryPoint: EntryPoint, caseSrv: CaseSrv, userSrv: UserSrv, taskSrv: TaskSrv) {

  import AlertConversion._
  import CaseConversion._
  import LogConversion._
  import ObservableConversion._
  import TaskConversion._
  import UserConversion._

  lazy val logger = Logger(getClass)

  lazy val caseDescription: JsObject =
    Json.obj("label" -> "case", "path" -> "/case", "attributes" -> caseProperties(caseSrv, userSrv).map(propertyToJson("case", _)))
  lazy val taskDescription: JsObject =
    Json.obj("label" -> "case_task", "path" -> "/case/task", "attributes" -> taskProperties(taskSrv, userSrv).map(propertyToJson("task", _)))
  lazy val alertDescription: JsObject =
    Json.obj("label" -> "alert", "path" -> "/alert", "attributes" -> alertProperties.map(propertyToJson("alert", _)))
  lazy val observableDescription: JsObject =
    Json.obj("label" -> "case_artifact", "path" -> "/case/artifact", "attributes" -> observableProperties.map(propertyToJson("observable", _)))
  lazy val userDescription: JsObject =
    Json.obj("label" -> "user", "path" -> "/user", "attributes" -> userProperties(userSrv).map(propertyToJson("user", _)))
  lazy val logDescription: JsObject =
    Json.obj("label" -> "case_task_log", "path" -> "/case/task/log", "attributes" -> logProperties.map(propertyToJson("log", _)))

  // audit ?
  // action

  case class PropertyDescription(name: String, `type`: String, values: Seq[JsValue] = Nil, labels: Seq[String] = Nil)

  implicit val propertyDescriptionWrites: Writes[PropertyDescription] =
    Json.writes[PropertyDescription].transform((_: JsObject) + ("description" -> JsString("")))

  def customDescription(model: String, propertyName: String): Option[PropertyDescription] = (model, propertyName) match {
    case (_, "owner") => Some(PropertyDescription("owner", "user"))
    case ("case", "status") =>
      Some(PropertyDescription("status", "enumeration", Seq(JsString("Open"), JsString("Resolved"), JsString("Deleted"), JsString("Duplicated"))))
    //case ("observable", "status") =>
    //  Some(PropertyDescription("status", "enumeration", Seq(JsString("Ok"))))
    //case ("observable", "dataType") =>
    //  Some(PropertyDescription("status", "enumeration", Seq(JsString("sometesttype", "fqdn", "url", "regexp", "mail", "hash", "registry", "custom-type", "uri_path", "ip", "user-agent", "autonomous-system", "file", "mail_subject", "filename", "other", "domain"))))
    case ("alert", "status") =>
      Some(PropertyDescription("status", "enumeration", Seq(JsString("New"), JsString("Updated"), JsString("Ignored"), JsString("Imported"))))
    case ("task", "status") =>
      Some(PropertyDescription("status", "enumeration", Seq(JsString("Waiting"), JsString("InProgress"), JsString("Completed"), JsString("Cancel"))))
    case ("case", "impactStatus") =>
      Some(PropertyDescription("impactStatus", "enumeration", Seq(JsString("NoImpact"), JsString("WithImpact"), JsString("NotApplicable"))))
    case ("case", "resolutionStatus") =>
      Some(
        PropertyDescription(
          "resolutionStatus",
          "enumeration",
          Seq(JsString("FalsePositive"), JsString("Duplicated"), JsString("Indeterminate"), JsString("TruePositive"), JsString("Other"))
        )
      )
    case (_, "tlp") =>
      Some(PropertyDescription("tlp", "number", Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)), Seq("white", "green", "amber", "red")))
    case (_, "pap") =>
      Some(PropertyDescription("pap", "number", Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)), Seq("white", "green", "amber", "red")))
    case (_, "severity") =>
      Some(PropertyDescription("severity", "number", Seq(JsNumber(1), JsNumber(2), JsNumber(3)), Seq("low", "medium", "high")))
    case _ => None
  }

  def propertyToJson(model: String, prop: PublicProperty[_, _]): PropertyDescription =
    customDescription(model, prop.propertyName).getOrElse {
      prop.mapping.domainTypeClass match {
        case c if c == classOf[Boolean] || c == classOf[JBoolean] => PropertyDescription(prop.propertyName, "boolean")
        case c if c == classOf[Date]                              => PropertyDescription(prop.propertyName, "date")
        case c if c == classOf[Hash]                              => PropertyDescription(prop.propertyName, "hash")
        case c if classOf[Number].isAssignableFrom(c)             => PropertyDescription(prop.propertyName, "number")
        case c if c == classOf[String]                            => PropertyDescription(prop.propertyName, "string")
        case _ =>
          logger.warn(s"Unrecognized property $prop. Add a custom description")
          PropertyDescription(prop.propertyName, "unknown")
      }
    }

  def describe(modelName: String): Action[AnyContent] =
    entryPoint("describe model")
      .auth { _ =>
        val output = modelName match {
          case "case"          => caseDescription
          case "case_task"     => taskDescription
          case "alert"         => alertDescription
          case "case_artifact" => observableDescription
          case "user"          => userDescription
          case "case_task_log" => logDescription
        }
        Success(Results.Ok(output))
      }

  def describeAll: Action[AnyContent] =
    entryPoint("describe all models")
      .auth { _ =>
        Success(
          Results.Ok(
            Json.obj(
              "case_artifact"     -> observableDescription,
              "case"              -> caseDescription,
              "case_task"         -> taskDescription,
              "alert"             -> alertDescription,
              "user"              -> userDescription,
              "task_artifact_log" -> logDescription
            )
          )
        )
      }
}
