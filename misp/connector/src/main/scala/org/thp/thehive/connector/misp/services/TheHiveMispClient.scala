package org.thp.thehive.connector.misp.services

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{Format, JsObject, JsString, Json}
import play.api.libs.ws.WSClient

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import org.thp.client.{Authentication, ProxyWS, ProxyWSConfig}
import org.thp.misp.client.{MispClient, MispPurpose}
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.OrganisationSteps
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat

case class TheHiveMispClientConfig(
    name: String,
    baseUrl: String,
    auth: Authentication,
    wsConfig: ProxyWSConfig,
    maxAge: Option[Duration],
    maxAttributes: Option[Int],
    maxSize: Option[Long],
    excludedOrganisations: Seq[String],
    excludedTags: Set[String],
    whitelistTags: Set[String],
    purpose: MispPurpose.Value,
    caseTemplate: Option[String],
    artifactTags: Seq[String],
    exportCaseTags: Boolean,
    includedTheHiveOrganisations: Seq[String],
    excludedTheHiveOrganisations: Seq[String]
)

object TheHiveMispClientConfig {
  implicit val purposeFormat: Format[MispPurpose.Value] = Json.formatEnum(MispPurpose)
  implicit val format: Format[TheHiveMispClientConfig]  = Json.format[TheHiveMispClientConfig]
}

class TheHiveMispClient(
    name: String,
    baseUrl: String,
    auth: Authentication,
    ws: WSClient,
    maxAge: Option[Duration],
    maxAttributes: Option[Int],
    maxSize: Option[Long],
    excludedOrganisations: Seq[String],
    excludedTags: Set[String],
    whitelistTags: Set[String],
    purpose: MispPurpose.Value,
    val caseTemplate: Option[String],
    artifactTags: Seq[String],
    exportCaseTags: Boolean,
    includedTheHiveOrganisations: Seq[String],
    excludedTheHiveOrganisations: Seq[String]
) extends MispClient(
      name,
      baseUrl,
      auth,
      ws,
      maxAge,
      maxAttributes,
      maxSize,
      excludedOrganisations,
      excludedTags,
      whitelistTags
    ) {

  def this(config: TheHiveMispClientConfig, mat: Materializer) = this(
    config.name,
    config.baseUrl,
    config.auth,
    new ProxyWS(config.wsConfig, mat),
    config.maxAge,
    config.maxAttributes,
    config.maxSize,
    config.excludedOrganisations,
    config.excludedTags,
    config.whitelistTags,
    config.purpose,
    config.caseTemplate,
    config.artifactTags,
    config.exportCaseTags,
    config.includedTheHiveOrganisations,
    config.excludedTheHiveOrganisations
  )

  val (canImport, canExport) = purpose match {
    case MispPurpose.ImportAndExport => (true, true)
    case MispPurpose.ImportOnly      => (true, false)
    case MispPurpose.ExportOnly      => (false, true)
  }

  def organisationFilter(organisationSteps: OrganisationSteps): OrganisationSteps = {
    val includedOrgs =
      if (includedTheHiveOrganisations.contains("*") || includedTheHiveOrganisations.isEmpty) organisationSteps
      else organisationSteps.has(Key[String]("name"), P.within(includedTheHiveOrganisations))
    if (excludedTheHiveOrganisations.isEmpty) includedOrgs
    else includedOrgs.has(Key[String]("name"), P.without(excludedTheHiveOrganisations))
  }

  override def getStatus(implicit ec: ExecutionContext): Future[JsObject] =
    super.getStatus.map(_ + ("purpose" -> JsString(purpose.toString)) + ("url" -> JsString(baseUrl)))

  def getHealth(implicit ec: ExecutionContext): Future[HealthStatus.Value] =
    getVersion
      .map(_ => HealthStatus.Ok)
      .recover { case _ => HealthStatus.Error }
}
