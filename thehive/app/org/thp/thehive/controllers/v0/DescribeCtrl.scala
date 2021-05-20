package org.thp.thehive.controllers.v0

import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.services.{CustomFieldSrv, EntityDescription, PropertyDescription}
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class TheHiveModelDescription(
    publicAlert: PublicAlert,
    publicAudit: PublicAudit,
    publicCase: PublicCase,
    publicCaseTemplate: PublicCaseTemplate,
    publicCustomField: PublicCustomField,
    publicDashboard: PublicDashboard,
    publicLog: PublicLog,
    publicObservable: PublicObservable,
    publicObservableType: PublicObservableType,
    publicOrganisation: PublicOrganisation,
//    publicPage: PublicPage,
    publicProfile: PublicProfile,
    publicTask: PublicTask,
    publicUser: PublicUser,
    customFieldSrv: CustomFieldSrv,
    db: Database
) extends ModelDescription
    with TraversalOps {

  private val metadata = Seq(
    PropertyDescription("createdBy", "user"),
    PropertyDescription("createdAt", "date"),
    PropertyDescription("updatedBy", "user"),
    PropertyDescription("updatedAt", "date")
  )

  lazy val logger: Logger = Logger(getClass)

  override def entityDescriptions: Seq[EntityDescription] =
    Seq(
      EntityDescription("case", "/case", "", publicCase.publicProperties.list.flatMap(propToDesc("case", _)) ++ metadata),
      EntityDescription("case_task", "/case/task", "", publicTask.publicProperties.list.flatMap(propToDesc("case_task", _)) ++ metadata),
      EntityDescription("alert", "/alert", "", publicAlert.publicProperties.list.flatMap(propToDesc("alert", _)) ++ metadata),
      EntityDescription(
        "case_artifact",
        "/case/artifact",
        "",
        publicObservable.publicProperties.list.flatMap(propToDesc("case_artifact", _)) ++ metadata
      ),
      EntityDescription("user", "/user", "", publicUser.publicProperties.list.flatMap(propToDesc("user", _)) ++ metadata),
      EntityDescription("case_task_log", "/case/task/log", "", publicLog.publicProperties.list.flatMap(propToDesc("case_task_log", _)) ++ metadata),
      EntityDescription("audit", "/audit", "", publicAudit.publicProperties.list.flatMap(propToDesc("audit", _)) ++ metadata),
      EntityDescription(
        "caseTemplate",
        "/caseTemplate",
        "",
        publicCaseTemplate.publicProperties.list.flatMap(propToDesc("caseTemplate", _)) ++ metadata
      ),
      EntityDescription(
        "customField",
        "/customField",
        "",
        publicCustomField.publicProperties.list.flatMap(propToDesc("customField", _)) ++ metadata
      ),
      EntityDescription(
        "observableType",
        "/observableType",
        "",
        publicObservableType.publicProperties.list.flatMap(propToDesc("observableType", _)) ++ metadata
      ),
      EntityDescription(
        "organisation",
        "/organisation",
        "",
        publicOrganisation.publicProperties.list.flatMap(propToDesc("organisation", _)) ++ metadata
      ),
      EntityDescription("profile", "/profile", "", publicProfile.publicProperties.list.flatMap(propToDesc("profile", _)) ++ metadata),
      EntityDescription("dashboard", "/dashboard", "", publicDashboard.publicProperties.list.flatMap(propToDesc("dashboard", _)) ++ metadata)
      //        EntityDescription("page", "/page", "", publicPage.publicProperties.list.flatMap(propertyToJson("page", _)))
    )

  def customFields: Seq[PropertyDescription] = {
    def jsonToString(v: JsValue): String =
      v match {
        case JsString(s)  => s
        case JsBoolean(b) => b.toString
        case JsNumber(v)  => v.toString
        case other        => other.toString
      }
    db.roTransaction { implicit graph =>
      customFieldSrv
        .startTraversal
        .toSeq
        .map(cf => PropertyDescription(s"customFields.${cf.name}", cf.`type`.toString, cf.options, cf.options.map(jsonToString)))
    }
  }

  override def customDescription(model: String, propertyName: String): Option[Seq[PropertyDescription]] =
    (model, propertyName) match {
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
          Seq(
            PropertyDescription("status", "enumeration", Seq(JsString("Waiting"), JsString("InProgress"), JsString("Completed"), JsString("Cancel")))
          )
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
        Some(
          Seq(PropertyDescription("tlp", "number", Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)), Seq("white", "green", "amber", "red")))
        )
      case (_, "pap") =>
        Some(
          Seq(PropertyDescription("pap", "number", Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)), Seq("white", "green", "amber", "red")))
        )
      case (_, "severity") =>
        Some(
          Seq(
            PropertyDescription(
              "severity",
              "number",
              Seq(JsNumber(1), JsNumber(2), JsNumber(3), JsNumber(4)),
              Seq("low", "medium", "high", "critical")
            )
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
      case ("dashboard", "status") =>
        Some(Seq(PropertyDescription("status", "enumeration", Seq(JsString("Shared"), JsString("Private"), JsString("Deleted")))))
      case (_, "patternId") =>
        Some(Seq(PropertyDescription("patternId", "string", Nil)))
      case _ => None
    }
}

class DescribeCtrl(
    applicationConfig: ApplicationConfig,
    cacheApi: SyncCacheApi,
    entrypoint: Entrypoint,
    versionedModelDescriptions: Seq[(Int, ModelDescription)]
) {
  val cacheExpireConfig: ConfigItem[Duration, Duration] =
    applicationConfig.item[Duration]("describe.cache.expire", "Custom fields refresh in describe")
  def cacheExpire: Duration = cacheExpireConfig.get

  def entityDescriptions: Seq[EntityDescription] =
    cacheApi.getOrElseUpdate("describe.v0", cacheExpire)(versionedModelDescriptions.collect {
      case (0, desc) => desc.entityDescriptions
    }.flatten)

  def describe(modelName: String): Action[AnyContent] =
    entrypoint("describe model")
      .auth { _ =>
        entityDescriptions
          .collectFirst {
            case desc if desc.label == modelName => Success(Results.Ok(Json.toJson(desc)))
          }
          .getOrElse(Failure(NotFoundError(s"Model $modelName not found")))
      }

  def describeAll: Action[AnyContent] =
    entrypoint("describe all models")
      .auth { _ =>
        val descriptors = entityDescriptions.map(desc => desc.label -> Json.toJson(desc))
        Success(Results.Ok(JsObject(descriptors)))
      }
}
/*

    connectors: Set[Connector]

 */
