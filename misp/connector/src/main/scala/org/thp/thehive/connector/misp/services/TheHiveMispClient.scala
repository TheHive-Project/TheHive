package org.thp.thehive.connector.misp.services

import akka.stream.Materializer
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.client.{Authentication, ProxyWS, ProxyWSConfig}
import org.thp.misp.client.{MispClient, MispPurpose}
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat
import org.thp.scalligraph.traversal.{Traversal, TraversalOps}
import org.thp.thehive.models.{HealthStatus, Organisation}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClientConfig

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class TheHiveMispClientConfig(
    name: String,
    url: String,
    auth: Authentication,
    wsConfig: ProxyWSConfig = ProxyWSConfig(AhcWSClientConfig(), None),
    maxAttributes: Option[Int],
    maxAge: Option[Duration],
    excludedOrganisations: Seq[String] = Nil,
    whitelistOrganisations: Seq[String] = Nil,
    excludedTags: Set[String] = Set.empty,
    whitelistTags: Set[String] = Set.empty,
    purpose: MispPurpose.Value = MispPurpose.ImportAndExport,
    caseTemplate: Option[String],
    observableTags: Seq[String] = Nil,
    exportCaseTags: Boolean = false,
    exportObservableTags: Boolean = false,
    includedTheHiveOrganisations: Seq[String] = Seq("*"),
    excludedTheHiveOrganisations: Seq[String] = Nil
)

object TheHiveMispClientConfig {
  implicit val purposeFormat: Format[MispPurpose.Value] = Json.formatEnum(MispPurpose)

  val reads: Reads[TheHiveMispClientConfig] = {
    for {
      name                         <- (JsPath \ "name").read[String]
      url                          <- (JsPath \ "url").read[String]
      auth                         <- (JsPath \ "auth").read[Authentication]
      wsConfig                     <- (JsPath \ "wsConfig").readWithDefault[ProxyWSConfig](ProxyWSConfig(AhcWSClientConfig(), None))
      maxAttributes                <- (JsPath \ "max-attributes").readNullable[Int]
      maxAge                       <- (JsPath \ "maxAge").readNullable[Duration]
      excludedOrganisations        <- (JsPath \ "exclusion" \ "organisations").readWithDefault[Seq[String]](Nil)
      whitelistOrganisations       <- (JsPath \ "whitelist" \ "organisations").readWithDefault[Seq[String]](Nil)
      excludedTags                 <- (JsPath \ "exclusion" \ "tags").readWithDefault[Set[String]](Set.empty)
      whitelistTags                <- (JsPath \ "whitelist" \ "tags").readWithDefault[Set[String]](Set.empty)
      purpose                      <- (JsPath \ "purpose").readWithDefault[MispPurpose.Value](MispPurpose.ImportAndExport)
      caseTemplate                 <- (JsPath \ "caseTemplate").readNullable[String]
      observableTags               <- (JsPath \ "tags").readWithDefault[Seq[String]](Nil)
      exportCaseTags               <- (JsPath \ "exportCaseTags").readWithDefault[Boolean](false)
      exportObservableTags         <- (JsPath \ "exportObservableTags").readWithDefault[Boolean](false)
      includedTheHiveOrganisations <- (JsPath \ "includedTheHiveOrganisations").readWithDefault[Seq[String]](Seq("*"))
      excludedTheHiveOrganisations <- (JsPath \ "excludedTheHiveOrganisations").readWithDefault[Seq[String]](Nil)
    } yield TheHiveMispClientConfig(
      name,
      url,
      auth,
      wsConfig,
      maxAttributes,
      maxAge,
      excludedOrganisations,
      whitelistOrganisations,
      excludedTags,
      whitelistTags,
      purpose,
      caseTemplate,
      observableTags,
      exportCaseTags,
      exportObservableTags,
      includedTheHiveOrganisations,
      excludedTheHiveOrganisations
    )
  }
  val writes: Writes[TheHiveMispClientConfig] = Writes[TheHiveMispClientConfig] { cfg =>
    Json.obj(
      "name"                         -> cfg.name,
      "url"                          -> cfg.url,
      "auth"                         -> cfg.auth,
      "wsConfig"                     -> cfg.wsConfig,
      "maxAttributes"                -> cfg.maxAttributes,
      "maxAge"                       -> cfg.maxAge,
      "exclusion"                    -> Json.obj("organisations" -> cfg.excludedOrganisations, "tags" -> cfg.excludedTags),
      "whitelistTags"                -> Json.obj("whitelist" -> cfg.whitelistTags),
      "purpose"                      -> cfg.purpose,
      "caseTemplate"                 -> cfg.caseTemplate,
      "tags"                         -> cfg.observableTags,
      "exportCaseTags"               -> cfg.exportCaseTags,
      "includedTheHiveOrganisations" -> cfg.includedTheHiveOrganisations,
      "excludedTheHiveOrganisations" -> cfg.excludedTheHiveOrganisations
    )
  }
  implicit val format: Format[TheHiveMispClientConfig] = Format[TheHiveMispClientConfig](reads, writes)
}

class TheHiveMispClient(
    name: String,
    baseUrl: String,
    auth: Authentication,
    ws: WSClient,
    maxAttributes: Option[Int],
    maxAge: Option[Duration],
    excludedOrganisations: Seq[String],
    whitelistOrganisations: Seq[String],
    excludedTags: Set[String],
    whitelistTags: Set[String],
    purpose: MispPurpose.Value,
    val caseTemplate: Option[String],
    val observableTags: Seq[String],
    val exportCaseTags: Boolean,
    val exportObservableTags: Boolean,
    includedTheHiveOrganisations: Seq[String],
    excludedTheHiveOrganisations: Seq[String]
) extends MispClient(
      name,
      baseUrl,
      auth,
      ws,
      maxAttributes,
      maxAge,
      excludedOrganisations,
      whitelistOrganisations,
      excludedTags,
      whitelistTags
    )
    with TraversalOps {

  def this(config: TheHiveMispClientConfig, mat: Materializer) =
    this(
      config.name,
      config.url,
      config.auth,
      new ProxyWS(config.wsConfig, mat),
      config.maxAttributes,
      config.maxAge,
      config.excludedOrganisations,
      config.whitelistOrganisations,
      config.excludedTags,
      config.whitelistTags,
      config.purpose,
      config.caseTemplate,
      config.observableTags,
      config.exportCaseTags,
      config.exportObservableTags,
      config.includedTheHiveOrganisations,
      config.excludedTheHiveOrganisations
    )

  val (canImport, canExport) = purpose match {
    case MispPurpose.ImportAndExport => (true, true)
    case MispPurpose.ImportOnly      => (true, false)
    case MispPurpose.ExportOnly      => (false, true)
  }

  def organisationFilter(organisationSteps: Traversal.V[Organisation]): Traversal.V[Organisation] = {
    val includedOrgs =
      if (includedTheHiveOrganisations.contains("*") || includedTheHiveOrganisations.isEmpty) organisationSteps
      else organisationSteps.has(_.name, P.within(includedTheHiveOrganisations: _*))
    if (excludedTheHiveOrganisations.isEmpty) includedOrgs
    else includedOrgs.has(_.name, P.without(excludedTheHiveOrganisations: _*))
  }

  override def getStatus(implicit ec: ExecutionContext): Future[JsObject] =
    super.getStatus.map(_ + ("purpose" -> JsString(purpose.toString)) + ("url" -> JsString(baseUrl)))

  def getHealth(implicit ec: ExecutionContext): Future[HealthStatus.Value] =
    getVersion
      .map(_ => HealthStatus.Ok)
      .recover { case _ => HealthStatus.Error }
}
