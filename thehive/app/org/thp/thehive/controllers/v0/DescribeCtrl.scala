package org.thp.thehive.controllers.v0

import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.{Database, IndexType}
import org.thp.scalligraph.query.PublicProperty
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
    PropertyDescription("createdBy", "user", indexType = IndexType.standard),
    PropertyDescription("createdAt", "date", indexType = IndexType.standard),
    PropertyDescription("updatedBy", "user", indexType = IndexType.standard),
    PropertyDescription("updatedAt", "date", indexType = IndexType.standard)
  )

  lazy val logger: Logger = Logger(getClass)

  override def entityDescriptions: Seq[EntityDescription] =
    Seq(
      EntityDescription("case", "/case", "", publicCase.publicProperties.list.flatMap(propertyDescription("case", _)) ++ metadata),
      EntityDescription("case_task", "/case/task", "", publicTask.publicProperties.list.flatMap(propertyDescription("case_task", _)) ++ metadata),
      EntityDescription("alert", "/alert", "", publicAlert.publicProperties.list.flatMap(propertyDescription("alert", _)) ++ metadata),
      EntityDescription(
        "case_artifact",
        "/case/artifact",
        "",
        publicObservable.publicProperties.list.flatMap(propertyDescription("case_artifact", _)) ++ metadata
      ),
      EntityDescription("user", "/user", "", publicUser.publicProperties.list.flatMap(propertyDescription("user", _)) ++ metadata),
      EntityDescription(
        "case_task_log",
        "/case/task/log",
        "",
        publicLog.publicProperties.list.flatMap(propertyDescription("case_task_log", _)) ++ metadata
      ),
      EntityDescription("audit", "/audit", "", publicAudit.publicProperties.list.flatMap(propertyDescription("audit", _)) ++ metadata),
      EntityDescription(
        "caseTemplate",
        "/caseTemplate",
        "",
        publicCaseTemplate.publicProperties.list.flatMap(propertyDescription("caseTemplate", _)) ++ metadata
      ),
      EntityDescription(
        "customField",
        "/customField",
        "",
        publicCustomField.publicProperties.list.flatMap(propertyDescription("customField", _)) ++ metadata
      ),
      EntityDescription(
        "observableType",
        "/observableType",
        "",
        publicObservableType.publicProperties.list.flatMap(propertyDescription("observableType", _)) ++ metadata
      ),
      EntityDescription(
        "organisation",
        "/organisation",
        "",
        publicOrganisation.publicProperties.list.flatMap(propertyDescription("organisation", _)) ++ metadata
      ),
      EntityDescription("profile", "/profile", "", publicProfile.publicProperties.list.flatMap(propertyDescription("profile", _)) ++ metadata),
      EntityDescription("dashboard", "/dashboard", "", publicDashboard.publicProperties.list.flatMap(propertyDescription("dashboard", _)) ++ metadata)
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
        .map(cf => PropertyDescription(s"customFields.${cf.name}", cf.`type`.toString, cf.options, cf.options.map(jsonToString), IndexType.none))
    }
  }

  override def propertyDescription(model: String, prop: PublicProperty): Seq[PropertyDescription] =
    (model, prop.propertyName) match {
      case (_, "owner") => Seq(PropertyDescription("owner", "user", indexType = prop.indexType))
      case ("case", "status") =>
        Seq(
          PropertyDescription(
            "status",
            "enumeration",
            Seq(JsString("Open"), JsString("Resolved"), JsString("Deleted"), JsString("Duplicated")),
            indexType = prop.indexType
          )
        )
      //case ("observable", "status") =>
      //  Some(PropertyDescription("status", "enumeration", Seq(JsString("Ok"))))
      //case ("observable", "dataType") =>
      //  Some(PropertyDescription("status", "enumeration", Seq(JsString("sometesttype", "fqdn", "url", "regexp", "mail", "hash", "registry", "custom-type", "uri_path", "ip", "user-agent", "autonomous-system", "file", "mail_subject", "filename", "other", "domain"))))
      case ("alert", "status") =>
        Seq(
          PropertyDescription(
            "status",
            "enumeration",
            Seq(JsString("New"), JsString("Updated"), JsString("Ignored"), JsString("Imported")),
            indexType = prop.indexType
          )
        )
      case ("case_task", "status") =>
        Seq(
          PropertyDescription(
            "status",
            "enumeration",
            Seq(JsString("Waiting"), JsString("InProgress"), JsString("Completed"), JsString("Cancel")),
            indexType = prop.indexType
          )
        )
      case ("case", "impactStatus") =>
        Seq(
          PropertyDescription(
            "impactStatus",
            "enumeration",
            Seq(JsString("NoImpact"), JsString("WithImpact"), JsString("NotApplicable")),
            indexType = prop.indexType
          )
        )
      case ("case", "resolutionStatus") =>
        Seq(
          PropertyDescription(
            "resolutionStatus",
            "enumeration",
            Seq(JsString("FalsePositive"), JsString("Duplicated"), JsString("Indeterminate"), JsString("TruePositive"), JsString("Other")),
            indexType = prop.indexType
          )
        )
      case (_, "tlp") =>
        Seq(
          PropertyDescription(
            "tlp",
            "number",
            Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)),
            Seq("white", "green", "amber", "red"),
            indexType = prop.indexType
          )
        )
      case (_, "pap") =>
        Seq(
          PropertyDescription(
            "pap",
            "number",
            Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)),
            Seq("white", "green", "amber", "red"),
            indexType = prop.indexType
          )
        )
      case (_, "severity") =>
        Seq(
          PropertyDescription(
            "severity",
            "number",
            Seq(JsNumber(1), JsNumber(2), JsNumber(3), JsNumber(4)),
            Seq("low", "medium", "high", "critical"),
            indexType = prop.indexType
          )
        )
      case (_, "createdBy")    => Seq(PropertyDescription("createdBy", "user", indexType = prop.indexType))
      case (_, "updatedBy")    => Seq(PropertyDescription("updatedBy", "user", indexType = prop.indexType))
      case (_, "customFields") => customFields
      case ("case_artifact_job" | "action", "status") =>
        Seq(
          PropertyDescription(
            "status",
            "enumeration",
            Seq(JsString("InProgress"), JsString("Success"), JsString("Failure"), JsString("Waiting"), JsString("Deleted")),
            indexType = prop.indexType
          )
        )
      case ("dashboard", "status") =>
        Seq(
          PropertyDescription("status", "enumeration", Seq(JsString("Shared"), JsString("Private"), JsString("Deleted")), indexType = prop.indexType)
        )
      case (_, "patternId") =>
        Seq(PropertyDescription("patternId", "string", Nil, indexType = prop.indexType))
      case _ => super.propertyDescription(model, prop)
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
