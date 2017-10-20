package connectors.misp

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.libs.json.{ JsObject, Json }
import play.api.libs.ws.WSRequest

import akka.actor.ActorSystem
import services.CustomWSAPI

import org.elastic4play.utils.RichFuture

case class MispConnection(
    name: String,
    baseUrl: String,
    key: String,
    ws: CustomWSAPI,
    caseTemplate: Option[String],
    artifactTags: Seq[String]) {

  private[MispConnection] lazy val logger = Logger(getClass)

  logger.info(
    s"""Add MISP connection $name
       |\turl:           $baseUrl
       |\tproxy:         ${ws.proxy}
       |\tcase template: ${caseTemplate.getOrElse("<not set>")}
       |\tartifact tags: ${artifactTags.mkString}""".stripMargin)

  private[misp] def apply(url: String): WSRequest =
    ws.url(s"$baseUrl/$url")
      .withHttpHeaders(
        "Authorization" → key,
        "Accept" → "application/json")

  def status()(implicit system: ActorSystem, ec: ExecutionContext): Future[JsObject] = apply("servers/getVersion").get
    .map {
      case resp if resp.status / 100 == 2 ⇒ (resp.json \ "version").asOpt[String]
      case _                              ⇒ None
    }
    .recover { case _ ⇒ None }
    .withTimeout(1.seconds, None)
    .map {
      case Some(version) ⇒ Json.obj(
        "name" → name,
        "version" → version,
        "status" → "OK")
      case None ⇒ Json.obj(
        "name" → name,
        "version" → "",
        "status" → "ERROR")
    }
}