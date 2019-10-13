package org.thp.thehive.connector.misp.services

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import org.thp.client.{Authentication, ProxyWS, ProxyWSConfig}
import org.thp.misp.client.{MispClient, MispPurpose}
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.OrganisationSteps
import play.api.libs.json.{Format, JsObject, JsString, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClientConfig

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class TheHiveMispClientConfig(
    name: String,
    url: String,
    auth: Authentication,
    wsConfig: ProxyWSConfig = ProxyWSConfig(AhcWSClientConfig(), None),
    maxAge: Option[Duration],
    excludedOrganisations: Seq[String] = Nil,
    excludedTags: Set[String] = Set.empty,
    whitelistTags: Set[String] = Set.empty,
    purpose: MispPurpose.Value = MispPurpose.ImportAndExport,
    caseTemplate: Option[String],
    artifactTags: Seq[String] = Nil,
    exportCaseTags: Boolean = false,
    includedTheHiveOrganisations: Seq[String] = Seq("*"),
    excludedTheHiveOrganisations: Seq[String] = Nil
)

object TheHiveMispClientConfig {
  implicit val purposeFormat: Format[MispPurpose.Value] = Json.formatEnum(MispPurpose)
  implicit val format: Format[TheHiveMispClientConfig]  = Json.using[Json.WithDefaultValues].format[TheHiveMispClientConfig]
}

class TheHiveMispClient(
    name: String,
    baseUrl: String,
    auth: Authentication,
    ws: WSClient,
    maxAge: Option[Duration],
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
      excludedOrganisations,
      excludedTags,
      whitelistTags
    ) {

  def this(config: TheHiveMispClientConfig, mat: Materializer) = this(
    config.name,
    config.url,
    config.auth,
    new ProxyWS(config.wsConfig, mat),
    config.maxAge,
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
