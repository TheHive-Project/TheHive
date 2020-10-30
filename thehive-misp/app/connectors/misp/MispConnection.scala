package connectors.misp

import java.util.Date

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSRequest

import models.HealthStatus
import services.CustomWSAPI

object MispPurpose extends Enumeration {
  val ImportOnly, ExportOnly, ImportAndExport = Value
}
case class MispConnection(
    name: String,
    baseUrl: String,
    key: String,
    ws: CustomWSAPI,
    caseTemplate: Option[String],
    artifactTags: Seq[String],
    maxAge: Option[Duration],
    maxAttributes: Option[Int],
    maxSize: Option[Long],
    excludedOrganisations: Seq[String],
    excludedTags: Set[String],
    whitelistTags: Set[String],
    purpose: MispPurpose.Value,
    exportCaseTags: Boolean
) {

  private[MispConnection] lazy val logger = Logger(getClass)

  logger.info(s"""Add MISP connection $name
                 |  url:              $baseUrl
                 |  proxy:            ${ws.proxy}
                 |  case template:    ${caseTemplate.getOrElse("<not set>")}
                 |  artifact tags:    ${artifactTags.mkString}
                 |  filters:
                 |    max age:        ${maxAge.fold("<not set>")(_.toCoarsest.toString)}
                 |    max attributes: ${maxAttributes.getOrElse("<not set>")}
                 |    max size:       ${maxSize.getOrElse("<not set>")}
                 |    excluded orgs:  ${excludedOrganisations.mkString}
                 |    excluded tags:  ${excludedTags.mkString}
                 |    whitelist tags: ${whitelistTags.mkString}
                 |  purpose:          $purpose
                 |""".stripMargin)

  private[misp] def apply(url: String): WSRequest =
    ws.url(s"$baseUrl/$url")
      .withHttpHeaders("Authorization" -> key, "Accept" -> "application/json")

  val (canImport, canExport) = purpose match {
    case MispPurpose.ImportAndExport => (true, true)
    case MispPurpose.ImportOnly      => (true, false)
    case MispPurpose.ExportOnly      => (false, true)
  }

  def syncFrom(date: Date): Date =
    maxAge.fold(date) { age =>
      val now           = new Date
      val dateThreshold = new Date(now.getTime - age.toMillis)

      if (date after dateThreshold) date
      else dateThreshold
    }

  def isExcluded(event: MispAlert): Boolean =
    if (whitelistTags.nonEmpty && whitelistTags.intersect(event.tags.toSet).isEmpty) {
      logger.debug(s"event ${event.sourceRef} is ignored because it doesn't contain any of whitelist tags (${whitelistTags.mkString(",")})")
      true
    } else if (excludedOrganisations.contains(event.source)) {
      logger.debug(s"event ${event.sourceRef} is ignored because its organisation (${event.source}) is excluded")
      true
    } else {
      val t = excludedTags.intersect(event.tags.toSet)
      if (t.nonEmpty) {
        logger.debug(s"event ${event.sourceRef} is ignored because one of its tags (${t.mkString(",")}) is excluded")
        true
      } else false
    }

  def getVersion()(implicit ec: ExecutionContext): Future[Option[String]] =
    apply("servers/getVersion")
      .get
      .map {
        case resp if resp.status / 100 == 2 => (resp.json \ "version").asOpt[String]
        case _                              => None
      }
      .recover { case _ => None }

  def status()(implicit ec: ExecutionContext): Future[JsObject] =
    getVersion()
      .map {
        case Some(version) => Json.obj("name" -> name, "version" -> version, "status" -> "OK", "url"    -> baseUrl, "purpose" -> purpose.toString)
        case None          => Json.obj("name" -> name, "version" -> "", "status"      -> "ERROR", "url" -> baseUrl, "purpose" -> purpose.toString)
      }

  def healthStatus()(implicit ec: ExecutionContext): Future[HealthStatus.Type] =
    getVersion()
      .map {
        case None => HealthStatus.Error
        case _    => HealthStatus.Ok
      }
}
