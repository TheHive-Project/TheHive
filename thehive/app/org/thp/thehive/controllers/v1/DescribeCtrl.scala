package org.thp.thehive.controllers.v1

import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.services._
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class TheHiveModelDescription(
    alertCtrl: AlertCtrl,
    auditCtrl: AuditCtrl,
    caseCtrl: CaseCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    customFieldCtrl: CustomFieldCtrl,
    dashboardCtrl: DashboardCtrl,
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
    db: Database
) extends ModelDescription
    with TheHiveOpsNoDeps {

  val metadata = Seq(
    PropertyDescription("_createdBy", "user"),
    PropertyDescription("_createdAt", "date"),
    PropertyDescription("_updatedBy", "user"),
    PropertyDescription("_updatedAt", "date")
  )

  lazy val logger: Logger = Logger(getClass)

  override def entityDescriptions: Seq[EntityDescription] =
    Seq(
      EntityDescription("alert", "", "listAlert", alertCtrl.publicProperties.list.flatMap(propToDesc("alert", _))),
      EntityDescription("audit", "", "listAudit", auditCtrl.publicProperties.list.flatMap(propToDesc("audit", _))),
      EntityDescription("case", "", "listCase", caseCtrl.publicProperties.list.flatMap(propToDesc("case", _))),
      EntityDescription("caseTemplate", "", "listCaseTemplate", caseTemplateCtrl.publicProperties.list.flatMap(propToDesc("caseTemplate", _))),
      EntityDescription("customField", "", "listCustomField", customFieldCtrl.publicProperties.list.flatMap(propToDesc("customField", _))),
      EntityDescription("dashboard", "", "listDashboard", dashboardCtrl.publicProperties.list.flatMap(propToDesc("dashboard", _))),
      EntityDescription("log", "", "listLog", logCtrl.publicProperties.list.flatMap(propToDesc("case_task_log", _))),
      EntityDescription("observable", "", "listObservable", observableCtrl.publicProperties.list.flatMap(propToDesc("observable", _))),
      EntityDescription(
        "observableType",
        "",
        "listObservableType",
        observableTypeCtrl.publicProperties.list.flatMap(propToDesc("observableType", _))
      ),
      EntityDescription("organisation", "", "listOrganisation", organisationCtrl.publicProperties.list.flatMap(propToDesc("organisation", _))),
      // EntityDescription("page", "", "listPage", pageCtrl.publicProperties.list.flatMap(propToDesc("page", _)))
      EntityDescription("pattern", "", "listPattern", patternCtrl.publicProperties.list.flatMap(propToDesc("pattern", _))),
      EntityDescription("procedure", "", "listProcedure", procedureCtrl.publicProperties.list.flatMap(propToDesc("procedure", _))),
      EntityDescription("profile", "", "listProfile", profileCtrl.publicProperties.list.flatMap(propToDesc("profile", _))),
      EntityDescription("tag", "", "listTag", tagCtrl.publicProperties.list.flatMap(propToDesc("tag", _))),
      EntityDescription("task", "", "listTask", taskCtrl.publicProperties.list.flatMap(propToDesc("task", _))),
      EntityDescription("taxonomy", "", "listTaxonomy", taxonomyCtrl.publicProperties.list.flatMap(propToDesc("taxonomy", _))),
      EntityDescription("user", "", "listUser", userCtrl.publicProperties.list.flatMap(propToDesc("user", _)))
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

  def impactStatus: PropertyDescription =
    db.roTransaction { implicit graph =>
      PropertyDescription("impactStatus", "enumeration", impactStatusSrv.startTraversal.toSeq.map(s => JsString(s.value)))
    }

  def resolutionStatus: PropertyDescription =
    db.roTransaction { implicit graph =>
      PropertyDescription("resolutionStatus", "enumeration", resolutionStatusSrv.startTraversal.toSeq.map(s => JsString(s.value)))
    }

  override def customDescription(model: String, propertyName: String): Option[Seq[PropertyDescription]] =
    (model, propertyName) match {
      case (_, "assignee") => Some(Seq(PropertyDescription("assignee", "user")))
      case ("case", "status") =>
        Some(
          Seq(PropertyDescription("status", "enumeration", Seq(JsString("Open"), JsString("Resolved"), JsString("Deleted"), JsString("Duplicated"))))
        )
      case ("case", "impactStatus")     => Some(Seq(impactStatus))
      case ("case", "resolutionStatus") => Some(Seq(resolutionStatus))
      case ("dashboard", "status") =>
        Some(Seq(PropertyDescription("status", "enumeration", Seq(JsString("Shared"), JsString("Private"), JsString("Deleted")))))
      case ("task", "status") =>
        Some(
          Seq(
            PropertyDescription("status", "enumeration", Seq(JsString("Waiting"), JsString("InProgress"), JsString("Completed"), JsString("Cancel")))
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
      case (_, "_createdBy")   => Some(Seq(PropertyDescription("_createdBy", "user")))
      case (_, "_updatedBy")   => Some(Seq(PropertyDescription("_updatedBy", "user")))
      case (_, "customFields") => Some(customFields)
      case (_, "patternId")    => Some(Seq(PropertyDescription("patternId", "string", Nil)))
      case _                   => None
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
    cacheApi.getOrElseUpdate("describe.v1", cacheExpire)(versionedModelDescriptions.collect {
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
