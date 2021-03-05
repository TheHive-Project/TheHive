package org.thp.thehive.controllers.v1

import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PublicProperty
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.Hash
import org.thp.scalligraph.{EntityId, NotFoundError}
import org.thp.thehive.controllers.v0.{QueryCtrl => QueryCtrlV0}
import org.thp.thehive.services.{CustomFieldSrv, ImpactStatusSrv, ResolutionStatusSrv}
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.inject.Injector
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}

import java.lang.{Boolean => JBoolean}
import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

@Singleton
class DescribeCtrl @Inject() (
    cacheApi: SyncCacheApi,
    entrypoint: Entrypoint,
    alertCtrl: AlertCtrl,
    auditCtrl: AuditCtrl,
    caseCtrl: CaseCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    customFieldCtrl: CustomFieldCtrl,
//    dashboardCtrl: DashboardCtrl,
    logCtrl: LogCtrl,
    observableCtrl: ObservableCtrl,
    observableTypeCtrl: ObservableTypeCtrl,
    organisationCtrl: OrganisationCtrl,
//    pageCtrl: PageCtrl,
    patternCtrl: PatternCtrl,
    procedureCtrl: ProcedureCtrl,
    profileCtrl: ProfileCtrl,
    tagCtrl: TagCtrl,
    taskCtrl: TaskCtrl,
    taxonomyCtrl: TaxonomyCtrl,
    userCtrl: UserCtrl,
    customFieldSrv: CustomFieldSrv,
    impactStatusSrv: ImpactStatusSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    injector: Injector,
    db: Database,
    applicationConfig: ApplicationConfig
) {

  case class PropertyDescription(name: String, `type`: String, values: Seq[JsValue] = Nil, labels: Seq[String] = Nil)
  val metadata = Seq(
    PropertyDescription("_createdBy", "user"),
    PropertyDescription("_createdAt", "date"),
    PropertyDescription("_updatedBy", "user"),
    PropertyDescription("_updatedAt", "date")
  )
  case class EntityDescription(label: String, initialQuery: String, attributes: Seq[PropertyDescription]) {
    def toJson: JsObject =
      Json.obj(
        "label"        -> label,
        "initialQuery" -> initialQuery,
        "attributes"   -> (attributes ++ metadata)
      )
  }

  lazy val logger: Logger = Logger(getClass)

  val cacheExpireConfig: ConfigItem[Duration, Duration] =
    applicationConfig.item[Duration]("describe.cache.expire", "Custom fields refresh in describe")
  def cacheExpire: Duration = cacheExpireConfig.get

  def describeCortexEntity(
      name: String,
      initialQuery: String,
      className: String,
      packageName: String = "org.thp.thehive.connector.cortex.controllers.v0"
  ): Option[EntityDescription] =
    Try(
      EntityDescription(
        name,
        initialQuery,
        injector
          .instanceOf(getClass.getClassLoader.loadClass(s"$packageName.$className"))
          .asInstanceOf[QueryCtrlV0]
          .publicData
          .publicProperties
          .list
          .flatMap(propertyToJson(name, _))
      )
    ).toOption

  def entityDescriptions: Seq[EntityDescription] =
    cacheApi.getOrElseUpdate("describe.v1", cacheExpire) {
      Seq(
        EntityDescription("alert", "listAlert", alertCtrl.publicProperties.list.flatMap(propertyToJson("alert", _))),
        EntityDescription("audit", "listAudit", auditCtrl.publicProperties.list.flatMap(propertyToJson("audit", _))),
        EntityDescription("case", "listCase", caseCtrl.publicProperties.list.flatMap(propertyToJson("case", _))),
        EntityDescription("caseTemplate", "listCaseTemplate", caseTemplateCtrl.publicProperties.list.flatMap(propertyToJson("caseTemplate", _))),
        EntityDescription("customField", "listCustomField", customFieldCtrl.publicProperties.list.flatMap(propertyToJson("customField", _))),
        // EntityDescription("dashboard", "listDashboard", dashboardCtrl.publicProperties.list.flatMap(propertyToJson("dashboard", _))),
        EntityDescription("log", "listLog", logCtrl.publicProperties.list.flatMap(propertyToJson("case_task_log", _))),
        EntityDescription("observable", "listObservable", observableCtrl.publicProperties.list.flatMap(propertyToJson("observable", _))),
        EntityDescription(
          "observableType",
          "listObservableType",
          observableTypeCtrl.publicProperties.list.flatMap(propertyToJson("observableType", _))
        ),
        EntityDescription("organisation", "listOrganisation", organisationCtrl.publicProperties.list.flatMap(propertyToJson("organisation", _))),
        // EntityDescription("page", "listPage", pageCtrl.publicProperties.list.flatMap(propertyToJson("page", _)))
        EntityDescription("pattern", "listPattern", patternCtrl.publicProperties.list.flatMap(propertyToJson("pattern", _))),
        EntityDescription("procedure", "listProcedure", procedureCtrl.publicProperties.list.flatMap(propertyToJson("procedure", _))),
        EntityDescription("profile", "listProfile", profileCtrl.publicProperties.list.flatMap(propertyToJson("profile", _))),
        EntityDescription("task", "listTask", taskCtrl.publicProperties.list.flatMap(propertyToJson("task", _))),
        EntityDescription("taxonomy", "listTaxonomy", taxonomyCtrl.publicProperties.list.flatMap(propertyToJson("taxonomy", _))),
        EntityDescription("user", "listUser", userCtrl.publicProperties.list.flatMap(propertyToJson("user", _)))
      ) ++ describeCortexEntity("job", "listJob", "JobCtrl") ++
        describeCortexEntity("action", "listAction", "ActionCtrl")
    }

  implicit val propertyDescriptionWrites: Writes[PropertyDescription] =
    Json.writes[PropertyDescription].transform((_: JsObject) + ("description" -> JsString("")))

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

  def impactStatus: PropertyDescription =
    db.roTransaction { implicit graph =>
      PropertyDescription("impactStatus", "enumeration", impactStatusSrv.startTraversal.toSeq.map(s => JsString(s.value)))
    }

  def resolutionStatus: PropertyDescription =
    db.roTransaction { implicit graph =>
      PropertyDescription("resolutionStatus", "enumeration", resolutionStatusSrv.startTraversal.toSeq.map(s => JsString(s.value)))
    }

  def customDescription(model: String, propertyName: String): Option[Seq[PropertyDescription]] =
    (model, propertyName) match {
      case (_, "assignee") => Some(Seq(PropertyDescription("assignee", "user")))
      case ("case", "status") =>
        Some(
          Seq(PropertyDescription("status", "enumeration", Seq(JsString("Open"), JsString("Resolved"), JsString("Deleted"), JsString("Duplicated"))))
        )
      case ("case", "impactStatus")     => Some(Seq(impactStatus))
      case ("case", "resolutionStatus") => Some(Seq(resolutionStatus))
//    //case ("observable", "status") =>
//    //  Some(PropertyDescription("status", "enumeration", Seq(JsString("Ok"))))
//    //case ("observable", "dataType") =>
//    //  Some(PropertyDescription("status", "enumeration", Seq(JsString("sometesttype", "fqdn", "url", "regexp", "mail", "hash", "registry", "custom-type", "uri_path", "ip", "user-agent", "autonomous-system", "file", "mail_subject", "filename", "other", "domain"))))
//    case ("alert", "status") =>
//      Some(Seq(PropertyDescription("status", "enumeration", Seq(JsString("New"), JsString("Updated"), JsString("Ignored"), JsString("Imported")))))
//    case ("case_task", "status") =>
//      Some(
//        Seq(PropertyDescription("status", "enumeration", Seq(JsString("Waiting"), JsString("InProgress"), JsString("Completed"), JsString("Cancel"))))
//      )
//    case ("case", "impactStatus") =>
//      Some(Seq(PropertyDescription("impactStatus", "enumeration", Seq(JsString("NoImpact"), JsString("WithImpact"), JsString("NotApplicable")))))
//    case ("case", "resolutionStatus") =>
//      Some(
//        Seq(
//          PropertyDescription(
//            "resolutionStatus",
//            "enumeration",
//            Seq(JsString("FalsePositive"), JsString("Duplicated"), JsString("Indeterminate"), JsString("TruePositive"), JsString("Other"))
//          )
//        )
//      )
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
      case (_, "_createdBy")   => Some(Seq(PropertyDescription("_createdBy", "user")))
      case (_, "_updatedBy")   => Some(Seq(PropertyDescription("_updatedBy", "user")))
      case (_, "customFields") => Some(customFields)
//    case ("case_artifact_job" | "action", "status") =>
//      Some(
//        Seq(
//          PropertyDescription(
//            "status",
//            "enumeration",
//            Seq(JsString("InProgress"), JsString("Success"), JsString("Failure"), JsString("Waiting"), JsString("Deleted"))
//          )
//        )
//      )
      case _ => None
    }

  def propertyToJson(model: String, prop: PublicProperty): Seq[PropertyDescription] =
    customDescription(model, prop.propertyName).getOrElse {
      prop.mapping.domainTypeClass match {
        case c if c == classOf[Boolean] || c == classOf[JBoolean] => Seq(PropertyDescription(prop.propertyName, "boolean"))
        case c if c == classOf[Date]                              => Seq(PropertyDescription(prop.propertyName, "date"))
        case c if c == classOf[Hash]                              => Seq(PropertyDescription(prop.propertyName, "string"))
        case c if classOf[Number].isAssignableFrom(c)             => Seq(PropertyDescription(prop.propertyName, "number"))
        case c if c == classOf[String]                            => Seq(PropertyDescription(prop.propertyName, "string"))
        case c if c == classOf[EntityId]                          => Seq(PropertyDescription(prop.propertyName, "string"))
        case _ =>
          logger.warn(s"Unrecognized property ${prop.propertyName}:${prop.mapping.domainTypeClass.getSimpleName}. Add a custom description")
          Seq(PropertyDescription(prop.propertyName, "unknown"))
      }
    }

  def describe(modelName: String): Action[AnyContent] =
    entrypoint("describe model")
      .auth { _ =>
        entityDescriptions
          .collectFirst {
            case desc if desc.label == modelName => Success(Results.Ok(desc.toJson))
          }
          .getOrElse(Failure(NotFoundError(s"Model $modelName not found")))
      }

  def describeAll: Action[AnyContent] =
    entrypoint("describe all models")
      .auth { _ =>
        val descriptors = entityDescriptions.map(desc => desc.label -> desc.toJson)
        Success(Results.Ok(JsObject(descriptors)))
      }
}
