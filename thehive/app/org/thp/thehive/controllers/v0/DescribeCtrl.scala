package org.thp.thehive.controllers.v0

import java.lang.{Boolean => JBoolean}
import java.util.Date

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PublicProperty
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.Hash
import org.thp.thehive.services.CustomFieldSrv
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.inject.Injector
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

@Singleton
class DescribeCtrl @Inject() (
    cacheApi: SyncCacheApi,
    entrypoint: Entrypoint,
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
    alertCtrl: AlertCtrl,
    observableCtrl: ObservableCtrl,
    userCtrl: UserCtrl,
    logCtrl: LogCtrl,
    auditCtrl: AuditCtrl,
    customFieldSrv: CustomFieldSrv,
    injector: Injector,
    @Named("with-thehive-schema") db: Database,
    applicationConfig: ApplicationConfig
) {

  case class PropertyDescription(name: String, `type`: String, values: Seq[JsValue] = Nil, labels: Seq[String] = Nil)
  case class EntityDescription(label: String, path: String, attributes: Seq[PropertyDescription]) {
    def toJson: JsObject =
      Json.obj(
        "label"      -> label,
        "path"       -> path,
        "attributes" -> attributes
      )
  }

  lazy val logger: Logger = Logger(getClass)

  val cacheExpireConfig: ConfigItem[Duration, Duration] =
    applicationConfig.item[Duration]("describe.cache.expire", "Custom fields refresh in describe")
  def cacheExpire: Duration = cacheExpireConfig.get

  def describeCortexEntity(
      name: String,
      path: String,
      className: String,
      packageName: String = "org.thp.thehive.connector.cortex.controllers.v0"
  ): Option[EntityDescription] =
    Try(
      EntityDescription(
        name,
        path,
        injector
          .instanceOf(getClass.getClassLoader.loadClass(s"$packageName.$className"))
          .asInstanceOf[QueryableCtrl]
          .publicProperties
          .flatMap(propertyToJson(name, _))
      )
    ).toOption

  val entityDescriptions: Seq[EntityDescription] = Seq(
    EntityDescription("case", "/case", caseCtrl.publicProperties.flatMap(propertyToJson("case", _))),
    EntityDescription("case_task", "/case/task", taskCtrl.publicProperties.flatMap(propertyToJson("case_task", _))),
    EntityDescription("alert", "/alert", alertCtrl.publicProperties.flatMap(propertyToJson("alert", _))),
    EntityDescription("case_artifact", "/case/artifact", observableCtrl.publicProperties.flatMap(propertyToJson("case_artifact", _))),
    EntityDescription("user", "user", userCtrl.publicProperties.flatMap(propertyToJson("user", _))),
    EntityDescription("case_task_log", "/case/task/log", logCtrl.publicProperties.flatMap(propertyToJson("case_task_log", _))),
    EntityDescription("audit", "audit", auditCtrl.publicProperties.flatMap(propertyToJson("audit", _)))
  ) ++ describeCortexEntity("case_artifact_job", "/connector/cortex/job", "JobCtrl") ++
    describeCortexEntity("action", "/connector/cortex/action", "ActionCtrl")

  implicit val propertyDescriptionWrites: Writes[PropertyDescription] =
    Json.writes[PropertyDescription].transform((_: JsObject) + ("description" -> JsString("")))

  def customFields: Seq[PropertyDescription] = db.roTransaction { implicit graph =>
    customFieldSrv.startTraversal.toSeq.map(cf => PropertyDescription(s"customFields.${cf.name}", cf.`type`.toString))
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
      Some(
        Seq(
          PropertyDescription("severity", "number", Seq(JsNumber(1), JsNumber(2), JsNumber(3), JsNumber(4)), Seq("low", "medium", "high", "critical"))
        )
      )
    case (_, "createdBy")    => Some(Seq(PropertyDescription("createdBy", "user")))
    case (_, "updatedBy")    => Some(Seq(PropertyDescription("updatedBy", "user")))
    case (_, "customFields") => Some(customFields)
    case ("case_artifact_job" | "action", "status") =>
      Some(
        Seq(
          PropertyDescription(
            "status",
            "enumeration",
            Seq(JsString("InProgress"), JsString("Success"), JsString("Failure"), JsString("Waiting"), JsString("Deleted"))
          )
        )
      )
    case _ => None
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
    entrypoint("describe model")
      .auth { _ =>
        entityDescriptions
          .collectFirst {
            case desc if desc.label == modelName => Success(Results.Ok(cacheApi.getOrElseUpdate(s"describe.v0.$modelName", cacheExpire)(desc.toJson)))
          }
          .getOrElse(Failure(NotFoundError(s"Model $modelName not found")))
      }

  def describeAll: Action[AnyContent] =
    entrypoint("describe all models")
      .auth { _ =>
        val descriptors = entityDescriptions.map { desc =>
          desc.label -> cacheApi.getOrElseUpdate(s"describe.v0.${desc.label}", cacheExpire)(desc.toJson)
        }
        Success(Results.Ok(JsObject(descriptors)))
      }
}
