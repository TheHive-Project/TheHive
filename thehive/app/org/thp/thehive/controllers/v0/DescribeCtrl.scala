package org.thp.thehive.controllers.v0

import java.lang.{Boolean => JBoolean}
import java.util.Date

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PublicProperty
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.utils.Hash
import org.thp.thehive.services.CustomFieldSrv
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat

@Singleton
class DescribeCtrl @Inject()(
    cacheApi: SyncCacheApi,
    entryPoint: EntryPoint,
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
    alertCtrl: AlertCtrl,
    observableCtrl: ObservableCtrl,
    userCtrl: UserCtrl,
    logCtrl: LogCtrl,
    customFieldSrv: CustomFieldSrv,
    db: Database,
    applicationConfig: ApplicationConfig
) {

  lazy val logger = Logger(getClass)

  val cacheExpireConfig: ConfigItem[Duration, Duration] =
    applicationConfig.item[Duration]("describe.cache.expire", "Custom fields refresh in describe")
  def cacheExpire: Duration = cacheExpireConfig.get

  // audit ?
  // action
  val entityControllers: Map[String, QueryableCtrl] = Map(
    "case"          -> caseCtrl,
    "case_task"     -> taskCtrl,
    "alert"         -> alertCtrl,
    "case_artifact" -> observableCtrl,
    "user"          -> userCtrl,
    "case_task_log" -> logCtrl
  )

  def describe(label: String, ctrl: QueryableCtrl): JsObject =
    Json.obj(
      "label"      -> label,
      "path"       -> ("/" + label.replaceAllLiterally("_", "/")),
      "attributes" -> ctrl.publicProperties.flatMap(propertyToJson(label, _))
    )

  case class PropertyDescription(name: String, `type`: String, values: Seq[JsValue] = Nil, labels: Seq[String] = Nil)

  implicit val propertyDescriptionWrites: Writes[PropertyDescription] =
    Json.writes[PropertyDescription].transform((_: JsObject) + ("description" -> JsString("")))

  def customFields: List[PropertyDescription] = db.roTransaction { implicit graph =>
    customFieldSrv.initSteps.toList.map(cf => PropertyDescription(s"customFields.${cf.name}", cf.`type`.toString))
  }

  def customDescription(model: String, propertyName: String): Option[Seq[PropertyDescription]] = (model, propertyName) match {
    case (_, "owner") => Some(Seq(PropertyDescription("owner", "user")))
    case ("case", "status") =>
      Some(
        Seq(PropertyDescription("status", "enumeration", Seq(JsString("Open"), JsString("Resolved"), JsString("Deleted"), JsString("Duplicated"))))
      )
    //case ("observable", "status") =>
    //  Some(PropertyDescription("status", "enumeration", Seq(JsString("Ok"))))
    //case ("observable", "dataType") =>
    //  Some(PropertyDescription("status", "enumeration", Seq(JsString("sometesttype", "fqdn", "url", "regexp", "mail", "hash", "registry", "custom-type", "uri_path", "ip", "user-agent", "autonomous-system", "file", "mail_subject", "filename", "other", "domain"))))
    case ("alert", "status") =>
      Some(Seq(PropertyDescription("status", "enumeration", Seq(JsString("New"), JsString("Updated"), JsString("Ignored"), JsString("Imported")))))
    case ("case_task", "status") =>
      Some(
        Seq(PropertyDescription("status", "enumeration", Seq(JsString("Waiting"), JsString("InProgress"), JsString("Completed"), JsString("Cancel"))))
      )
    case ("case", "impactStatus") =>
      Some(Seq(PropertyDescription("impactStatus", "enumeration", Seq(JsString("NoImpact"), JsString("WithImpact"), JsString("NotApplicable")))))
    case ("case", "resolutionStatus") =>
      Some(
        Seq(
          PropertyDescription(
            "resolutionStatus",
            "enumeration",
            Seq(JsString("FalsePositive"), JsString("Duplicated"), JsString("Indeterminate"), JsString("TruePositive"), JsString("Other"))
          )
        )
      )
    case (_, "tlp") =>
      Some(Seq(PropertyDescription("tlp", "number", Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)), Seq("white", "green", "amber", "red"))))
    case (_, "pap") =>
      Some(Seq(PropertyDescription("pap", "number", Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)), Seq("white", "green", "amber", "red"))))
    case (_, "severity") =>
      Some(Seq(PropertyDescription("severity", "number", Seq(JsNumber(1), JsNumber(2), JsNumber(3)), Seq("low", "medium", "high"))))
    case (_, "createdBy")    => Some(Seq(PropertyDescription("createdBy", "user")))
    case (_, "updatedBy")    => Some(Seq(PropertyDescription("updatedBy", "user")))
    case (_, "customFields") => Some(customFields)
    case _                   => None
  }

  def propertyToJson(model: String, prop: PublicProperty[_, _]): Seq[PropertyDescription] =
    customDescription(model, prop.propertyName).getOrElse {
      prop.mapping.domainTypeClass match {
        case c if c == classOf[Boolean] || c == classOf[JBoolean] => Seq(PropertyDescription(prop.propertyName, "boolean"))
        case c if c == classOf[Date]                              => Seq(PropertyDescription(prop.propertyName, "date"))
        case c if c == classOf[Hash]                              => Seq(PropertyDescription(prop.propertyName, "hash"))
        case c if classOf[Number].isAssignableFrom(c)             => Seq(PropertyDescription(prop.propertyName, "number"))
        case c if c == classOf[String]                            => Seq(PropertyDescription(prop.propertyName, "string"))
        case _ =>
          logger.warn(s"Unrecognized property $prop. Add a custom description")
          Seq(PropertyDescription(prop.propertyName, "unknown"))
      }
    }

  def describe(modelName: String): Action[AnyContent] =
    entryPoint("describe model")
      .auth { _ =>
        entityControllers.get(modelName) match {
          case Some(ctrl) => Success(Results.Ok(cacheApi.getOrElseUpdate(s"describe.$modelName", cacheExpire)(describe(modelName, ctrl))))
          case None       => Failure(NotFoundError(s"Model $modelName not found"))
        }
      }

  def describeAll: Action[AnyContent] =
    entryPoint("describe all models")
      .auth { _ =>
        val descriptors = entityControllers.map {
          case (modelName, ctrl) => modelName -> cacheApi.getOrElseUpdate(s"describe.$modelName", cacheExpire)(describe(modelName, ctrl))
        }
        Success(Results.Ok(JsObject(descriptors)))
      }
}
