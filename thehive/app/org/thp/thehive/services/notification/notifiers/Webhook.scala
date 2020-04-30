package org.thp.thehive.services.notification.notifiers
import akka.stream.Materializer
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.client.{ProxyWS, ProxyWSConfig}
import org.thp.scalligraph.BadConfigurationError
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.AuditRenderer
import org.thp.thehive.controllers.v0.Conversion.fromObjectType
import org.thp.thehive.models.{Audit, Organisation, User}
import org.thp.thehive.services.AuditSrv
import play.api.libs.json.Json.WithDefaultValues
import play.api.libs.json.{Format, JsObject, JsValue, Json}
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class WebhookNotification(
    name: String,
    url: String,
    version: Int = 0,
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

@Singleton
class WebhookProvider @Inject() (appConfig: ApplicationConfig, auditSrv: AuditSrv, ec: ExecutionContext, mat: Materializer) extends NotifierProvider {
  override val name: String = "webhook"

  val webhookConfigs: ConfigItem[Seq[WebhookNotification], Seq[WebhookNotification]] =
    appConfig.item[Seq[WebhookNotification]]("notification.webhook.endpoints", "webhook configuration list")

  override def apply(config: Configuration): Try[Notifier] =
    for {
      name <- config.getOrFail[String]("endpoint")
      config <- webhookConfigs
        .get
        .find(_.name == name)
        .fold[Try[WebhookNotification]](Failure(BadConfigurationError(s"Webhook configuration `$name` not found`")))(Success.apply)

    } yield new Webhook(config, auditSrv, mat, ec)
}

class Webhook(config: WebhookNotification, auditSrv: AuditSrv, mat: Materializer, implicit val ec: ExecutionContext) extends Notifier {
  override val name: String = "webhook"

  lazy val logger: Logger = Logger(getClass)

  def buildMessage(version: Int, audit: Audit with Entity)(implicit graph: Graph): Try[JsObject] =
    version match {
      case 0 =>
        auditSrv.get(audit).richAuditWithCustomRenderer(new AuditRenderer {}.auditRenderer).getOrFail().map {
          case (audit, obj) =>
            Json.obj(
              "operation"  -> audit.action,
              "details"    -> audit.details.fold[JsValue](JsObject.empty)(Json.parse),
              "objectType" -> fromObjectType(audit.objectType.getOrElse(audit.context._model.label)),
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

  override def execute(
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      organisation: Organisation with Entity,
      user: Option[User with Entity]
  )(implicit graph: Graph): Future[Unit] =
    if (!config.organisationAuthorised(organisation))
      Future.failed(BadConfigurationError(s"The organisation ${organisation.name} is not authorised to use the webhook ${config.name}"))
    else if (user.isDefined)
      Future.failed(BadConfigurationError("The notification webhook must not be applied on user"))
    else
      for {
        message <- Future.fromTry(buildMessage(config.version, audit))
        _ = logger.debug(s"Request webhook with message $message")
        resp <- new ProxyWS(config.wsConfig, mat)
          .url(config.url)
          .post(message)
      } yield if (resp.status >= 400) logger.warn(s"Webhook call on ${config.url} returns ${resp.status} ${resp.statusText}") else ()

}
