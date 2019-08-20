package org.thp.thehive.connector.misp.servives

import gremlin.scala.{Key, P}
import org.thp.client.Authentication
import org.thp.misp.client.{MispClient, MispPurpose}
import org.thp.thehive.services.OrganisationSteps
import play.api.libs.ws.WSClient

import scala.concurrent.duration.Duration

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

  /*
    case template:    ${caseTemplate.getOrElse("<not set>")}
  artifact tags:    ${artifactTags.mkString}
    purpose:          $purpose

   */
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
}
