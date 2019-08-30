package org.thp.thehive.controllers.v0

import java.lang.{Boolean => JBoolean}
import java.util.Date

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.query.PublicProperty
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}
import scala.util.Success

import org.thp.scalligraph.utils.Hash

@Singleton
class DescribeCtrl @Inject()(
    entryPoint: EntryPoint,
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
    alertCtrl: AlertCtrl,
    observableCtrl: ObservableCtrl,
    userCtrl: UserCtrl,
    logCtrl: LogCtrl
) {

  lazy val logger = Logger(getClass)

  // audit ?
  // action
  lazy val entityDescriptors: Map[String, JsObject] = Map(
    "case"          -> caseCtrl,
    "case_task"     -> taskCtrl,
    "alert"         -> alertCtrl,
    "case_artifact" -> observableCtrl,
    "user"          -> userCtrl,
    "case_task_log" -> logCtrl
  ).map {
    case (label, ctrl) =>
      label -> Json.obj(
        "label"      -> label,
        "path"       -> ("/" + label.replaceAllLiterally("_", "/")),
        "attributes" -> ctrl.publicProperties.map(propertyToJson(label, _))
      )
  }

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
      .auth(_ => Success(Results.Ok(entityDescriptors(modelName))))

  def describeAll: Action[AnyContent] =
    entryPoint("describe all models")
      .auth(_ => Success(Results.Ok(JsObject(entityDescriptors))))
}
