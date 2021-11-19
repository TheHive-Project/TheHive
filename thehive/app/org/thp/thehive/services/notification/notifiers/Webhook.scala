package org.thp.thehive.services.notification.notifiers

import akka.stream.Materializer
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.client.{Authentication, NoAuthentication, ProxyWS, ProxyWSConfig}
import org.thp.scalligraph.models.{Entity, UMapping}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.{Converter, Graph, IdentityConverter, Traversal}
import org.thp.scalligraph.{BadConfigurationError, EntityIdOrName}
import org.thp.thehive.controllers.v0.AuditRenderer
import org.thp.thehive.controllers.v0.Conversion.fromObjectType
import org.thp.thehive.models._
import org.thp.thehive.services.{AuditSrv, _}
import play.api.libs.json.Json.WithDefaultValues
import play.api.libs.json._
import play.api.{Configuration, Logger}

import java.util.{Date, Map => JMap}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class WebhookNotification(
    name: String,
    url: String,
    version: Int = 0,
    auth: Authentication = NoAuthentication,
    wsConfig: ProxyWSConfig = ProxyWSConfig(),
    includedTheHiveOrganisations: Seq[String] = Seq("*"),
    excludedTheHiveOrganisations: Seq[String] = Nil
) {
  def organisationAuthorised(organisation: Organisation): Boolean =
    (includedTheHiveOrganisations.contains("*") ||
      includedTheHiveOrganisations.isEmpty ||
      includedTheHiveOrganisations.contains(organisation.name)) &&
      !excludedTheHiveOrganisations.contains(organisation.name)
}

object WebhookNotification {
  implicit val format: Format[WebhookNotification] = Json.using[WithDefaultValues].format[WebhookNotification]
}

class WebhookProvider(
    appConfig: ApplicationConfig,
    auditSrv: AuditSrv,
    customFieldSrv: CustomFieldSrv,
    ec: ExecutionContext,
    mat: Materializer
) extends NotifierProvider {
  override val name: String = "webhook"

  val webhookConfigs: ConfigItem[Seq[WebhookNotification], Seq[WebhookNotification]] =
    appConfig.item[Seq[WebhookNotification]]("notification.webhook.endpoints", "webhook configuration list")

  override def apply(config: Configuration): Try[Notifier] =
    for {
      name <- config.getOrFail[String]("endpoint")
      config <-
        webhookConfigs
          .get
          .find(_.name == name)
          .fold[Try[WebhookNotification]](Failure(BadConfigurationError(s"Webhook configuration `$name` not found`")))(Success.apply)

    } yield new Webhook(config, auditSrv, customFieldSrv, mat, ec)
}

class Webhook(
    config: WebhookNotification,
    auditSrv: AuditSrv,
    customFieldSrv: CustomFieldSrv,
    mat: Materializer,
    implicit val ec: ExecutionContext
) extends Notifier
    with TheHiveOpsNoDeps {
  override val name: String = "webhook"

  lazy val logger: Logger = Logger(getClass)

  object v0 extends AuditRenderer

  object v1 {
    import org.thp.thehive.controllers.v1.Conversion._

    def caseToJson: Traversal.V[Case] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
      _.richCaseWithoutPerms.domainMap[JsObject](_.toJson.as[JsObject])

    def taskToJson: Traversal.V[Task] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
      _.project(
        _.by(_.richTaskWithoutActionRequired.domainMap(_.toJson))
          .by(t => caseToJson(t.`case`))
      ).domainMap {
        case (task, case0) => task.as[JsObject] + ("case" -> case0)
      }

    def alertToJson: Traversal.V[Alert] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
      _.richAlert.domainMap(_.toJson.as[JsObject])

    def logToJson: Traversal.V[Log] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
      _.project(
        _.by(_.richLog.domainMap(_.toJson))
          .by(l => taskToJson(l.task))
      ).domainMap { case (log, task) => log.as[JsObject] + ("case_task" -> task) }

    def observableToJson: Traversal.V[Observable] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
      _.project(
        _.by(_.richObservable.domainMap(_.toJson))
          .by(_.coalesceMulti(o => caseToJson(o.`case`), o => alertToJson(o.alert)))
      ).domainMap {
        case (obs, caseOrAlert) => obs.as[JsObject] + ((caseOrAlert \ "_type").asOpt[String].getOrElse("<unknown>") -> caseOrAlert)
      }

    case class Job(
        workerId: String,
        workerName: String,
        workerDefinition: String,
        status: String,
        startDate: Date,
        endDate: Date,
        report: Option[JsObject],
        cortexId: String,
        cortexJobId: String
    )
    def jobToJson
        : Traversal[Vertex, Vertex, IdentityConverter[Vertex]] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
      _.project(
        _.by.by
      ).domainMap {
        case (vertex, _) =>
          JsObject(
            UMapping.string.optional.getProperty(vertex, "workerId").map(v => "analyzerId" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "workerName").map(v => "analyzerName" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "workerDefinition").map(v => "analyzerDefinition" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "status").map(v => "status" -> JsString(v)).toList :::
              UMapping.date.optional.getProperty(vertex, "startDate").map(v => "startDate" -> JsNumber(v.getTime)).toList :::
              UMapping.date.optional.getProperty(vertex, "endDate").map(v => "endDate" -> JsNumber(v.getTime)).toList :::
              UMapping.string.optional.getProperty(vertex, "cortexId").map(v => "cortexId" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "cortexJobId").map(v => "cortexJobId" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "_createdBy").map(v => "_createdBy" -> JsString(v)).toList :::
              UMapping.date.optional.getProperty(vertex, "_createdAt").map(v => "_createdAt" -> JsNumber(v.getTime)).toList :::
              UMapping.string.optional.getProperty(vertex, "_updatedBy").map(v => "_updatedBy" -> JsString(v)).toList :::
              UMapping.date.optional.getProperty(vertex, "_updatedAt").map(v => "_updatedAt" -> JsNumber(v.getTime)).toList :::
              UMapping.string.optional.getProperty(vertex, "_type").map(v => "_type" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "_id").map(v => "_id" -> JsString(v)).toList
          )
      }

    def auditRenderer: Traversal.V[Audit] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
      (_: Traversal.V[Audit])
        .coalesce(
          _.`object` //.out[Audited]
            .chooseValue(
              _.on(_.label)
                .option("Case", t => caseToJson(t.v[Case]))
                .option("Task", t => taskToJson(t.v[Task]))
                .option("Log", t => logToJson(t.v[Log]))
                .option("Observable", t => observableToJson(t.v[Observable]))
                .option("Alert", t => alertToJson(t.v[Alert]))
                .option("Job", jobToJson)
                .none(_.constant2[JsObject, JMap[String, Any]](JsObject.empty))
            ),
          JsObject.empty
        )

  }

  // This method change the format of audit details when it contains custom field.
  // The custom field type is added to match TheHive 3 webhook format.
  def fixCustomFieldDetails(objectType: String, details: String)(implicit graph: Graph): JsValue = {
    val detailsJson = Json.parse(details)
    objectType match {
      case "Case" | "Alert" | "CaseTemplate" =>
        detailsJson.asOpt[JsObject].fold(detailsJson) { o =>
          JsObject(o.fields.map {
            case keyValue @ (key, value) if key.startsWith("customField.") =>
              val fieldName = key.drop(12)
              customFieldSrv
                .getOrFail(EntityIdOrName(fieldName))
                .fold(_ => keyValue, cf => "customFields" -> Json.obj(fieldName -> Json.obj(cf.`type`.toString -> value)))
            case ("customFields", JsArray(cfs)) =>
              "customFields" -> cfs
                .flatMap { cf =>
                  for {
                    name <- (cf \ "name").asOpt[String]
                    tpe  <- (cf \ "type").asOpt[String]
                    value = (cf \ "value").asOpt[JsValue]
                    order = (cf \ "order").asOpt[Int]
                  } yield Json.obj(name -> Json.obj(tpe -> value, "order" -> order))
                }
                .foldLeft(JsObject.empty)(_ ++ _)
            case keyValue => keyValue
          })
        }
      case _ => detailsJson
    }
  }

  def buildMessage(version: Int, audit: Audit with Entity)(implicit graph: Graph): Try[JsObject] =
    version match {
      case 0 =>
        auditSrv.get(audit).richAuditWithCustomRenderer(v0.auditRenderer).getOrFail("Audit").map {
          case (audit, obj) =>
            val objectType = audit.objectType.getOrElse(audit.context._label)
            Json.obj(
              "operation"  -> v0Action(audit.action),
              "details"    -> audit.details.fold[JsValue](JsObject.empty)(fixCustomFieldDetails(objectType, _)),
              "objectType" -> fromObjectType(objectType),
              "objectId"   -> audit.objectId,
              "base"       -> audit.mainAction,
              "startDate"  -> audit._createdAt,
              "rootId"     -> audit.context._id,
              "requestId"  -> audit.requestId,
              "object"     -> obj
            )
        }
      case 1 =>
        auditSrv.get(audit).richAuditWithCustomRenderer(v1.auditRenderer).getOrFail("Audit").map {
          case (audit, obj) =>
            val objectType = audit.objectType.getOrElse(audit.context._label)
            Json.obj(
              "operation"  -> audit.action,
              "details"    -> audit.details.fold[JsValue](JsObject.empty)(fixCustomFieldDetails(objectType, _)),
              "objectType" -> objectType,
              "objectId"   -> audit.objectId,
              "base"       -> audit.mainAction,
              "startDate"  -> audit._createdAt,
              "rootId"     -> audit.context._id,
              "requestId"  -> audit.requestId,
              "object"     -> obj
            )
        }
      case _ => Failure(BadConfigurationError(s"Message version $version in webhook is not supported"))
    }

  def v0Action(action: String): String =
    action match {
      case Audit.merge => Audit.update
      case action      => action
    }

  override def execute(
      audit: Audit with Entity,
      context: Option[Map[String, Seq[Any]] with Entity],
      `object`: Option[Map[String, Seq[Any]] with Entity],
      organisation: Organisation with Entity,
      user: Option[User with Entity]
  )(implicit graph: Graph): Future[Unit] =
    if (!config.organisationAuthorised(organisation))
      Future.failed(BadConfigurationError(s"The organisation ${organisation.name} is not authorised to use the webhook ${config.name}"))
    else if (user.isDefined)
      Future.failed(BadConfigurationError("The notification webhook must not be applied on user"))
    else {
      val ws = new ProxyWS(config.wsConfig, mat)
      val async = for {
        message <- Future.fromTry(
          buildMessage(config.version, audit).map(
            _ + ("organisationId" -> JsString(organisation._id.toString)) + ("organisation" -> JsString(organisation.name))
          )
        )
        _ = logger.debug(s"Request webhook with message $message")
        resp <- config.auth(ws.url(config.url)).post(message)
      } yield if (resp.status >= 400) logger.warn(s"Webhook call on ${config.url} returns ${resp.status} ${resp.statusText}") else ()
      async.andThen { case _ => ws.close() }
    }

}
