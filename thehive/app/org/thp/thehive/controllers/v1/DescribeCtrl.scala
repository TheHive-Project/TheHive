package org.thp.thehive.controllers.v1

import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.{Database, IndexType}
import org.thp.scalligraph.query.PublicProperty
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
    queryExecutor: TheHiveQueryExecutor,
    customFieldSrv: CustomFieldSrv,
    impactStatusSrv: ImpactStatusSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    db: Database
) extends ModelDescription
    with TheHiveOpsNoDeps {

  val metadata = Seq(
    PropertyDescription("_createdBy", "user", indexType = IndexType.standard),
    PropertyDescription("_createdAt", "date", indexType = IndexType.standard),
    PropertyDescription("_updatedBy", "user", indexType = IndexType.standard),
    PropertyDescription("_updatedAt", "date", indexType = IndexType.standard)
  )

  lazy val logger: Logger = Logger(getClass)

  override def entityDescriptions: Seq[EntityDescription] =
    queryExecutor.controllers.map { ctrl =>
      EntityDescription(
        label = ctrl.entityName,
        path = "",
        initialQuery = s"list${ctrl.entityName.capitalize}",
        attributes = ctrl.publicProperties.list.flatMap(propertyDescription(ctrl.entityName, _)) ++ metadata
      )
    }

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

  def impactStatus(prop: PublicProperty): PropertyDescription =
    db.roTransaction { implicit graph =>
      PropertyDescription("impactStatus", "enumeration", impactStatusSrv.startTraversal.toSeq.map(s => JsString(s.value)), indexType = prop.indexType)
    }

  def resolutionStatus(prop: PublicProperty): PropertyDescription =
    db.roTransaction { implicit graph =>
      PropertyDescription(
        "resolutionStatus",
        "enumeration",
        resolutionStatusSrv.startTraversal.toSeq.map(s => JsString(s.value)),
        indexType = prop.indexType
      )
    }

  override def propertyDescription(model: String, prop: PublicProperty): Seq[PropertyDescription] =
    (model, prop.propertyName) match {
      case (_, "assignee") => Seq(PropertyDescription("assignee", "user", indexType = prop.indexType))
      case ("case", "status") =>
        Seq(
          PropertyDescription(
            "status",
            "enumeration",
            Seq(JsString("Open"), JsString("Resolved"), JsString("Deleted"), JsString("Duplicated")),
            indexType = prop.indexType
          )
        )
      case ("case", "impactStatus")     => Seq(impactStatus(prop))
      case ("case", "resolutionStatus") => Seq(resolutionStatus(prop))
      case ("dashboard", "status") =>
        Seq(
          PropertyDescription("status", "enumeration", Seq(JsString("Shared"), JsString("Private"), JsString("Deleted")), indexType = prop.indexType)
        )
      case ("task", "status") =>
        Seq(
          PropertyDescription(
            "status",
            "enumeration",
            Seq(JsString("Waiting"), JsString("InProgress"), JsString("Completed"), JsString("Cancel")),
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
      case (_, "_createdBy")   => Seq(PropertyDescription("_createdBy", "user", indexType = prop.indexType))
      case (_, "_updatedBy")   => Seq(PropertyDescription("_updatedBy", "user", indexType = prop.indexType))
      case (_, "customFields") => customFields
      case (_, "patternId")    => Seq(PropertyDescription("patternId", "string", Nil, indexType = prop.indexType))
      case _                   => super.propertyDescription(model, prop)
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
      case (1, desc) => desc.entityDescriptions
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
