package org.thp.thehive.connector.misp.servives

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

import play.api.Configuration

import com.typesafe.config.ConfigMemorySize
import javax.inject.{Inject, Provider}
import org.thp.client.{Authentication, CustomWSAPI}
import org.thp.misp.client.MispPurpose

case class MispConfig(connections: Map[String, TheHiveMispClient], interval: FiniteDuration)

class MispConfigProvider @Inject()(configuration: Configuration, globalWS: CustomWSAPI) extends Provider[MispConfig] {
  override def get(): MispConfig = MispConfig(
    getClients(configuration, globalWS),
    configuration.getOptional[FiniteDuration]("misp.interval").getOrElse(1.minute)
  )

  /**
    * Gets the list of client instances
    *
    * @param configuration the conf for each client and web service client
    * @param globalWS the overridden or not web service client framework WSClient
    * @return
    */
  def getClients(configuration: Configuration, globalWS: CustomWSAPI): Map[String, TheHiveMispClient] = {
    val mispWS              = globalWS.withConfig(configuration.get[Configuration]("misp"))
    val defaultArtifactTags = configuration.getOptional[Seq[String]]("misp.tags").getOrElse(Nil)
    val defaultCaseTemplate = configuration.getOptional[String]("misp.caseTemplate")
    configuration
      .getOptional[Seq[Configuration]]("misp.servers")
      .getOrElse(Nil)
      .map(getMispClient(_, mispWS, defaultArtifactTags, defaultCaseTemplate))
      .map(client => client.name -> client)
      .toMap
  }

  /**
    * Tries to get a MispClient according to configuration
    *
    * @param configuration the .conf
    * @param globalWS custom or not web service client
    * @return
    */
  def getMispClient(
      configuration: Configuration,
      globalWS: CustomWSAPI,
      defaultArtifactTags: Seq[String],
      defaultCaseTemplate: Option[String]
  ): TheHiveMispClient = {
    val name = configuration.get[String]("name")
    val url = configuration
      .get[String]("url")
      .replaceFirst("/*$", "")

    val auth = configuration.get[Authentication](".")
    val ws   = globalWS.withConfig(configuration)

    val artifactTags          = configuration.getOptional[Seq[String]]("tags").getOrElse(defaultArtifactTags)
    val caseTemplate          = configuration.getOptional[String]("caseTemplate").orElse(defaultCaseTemplate)
    val maxAge                = configuration.getOptional[Duration]("max-age")
    val maxAttributes         = configuration.getOptional[Int]("max-attributes")
    val maxSize               = configuration.getOptional[ConfigMemorySize]("max-size").map(_.toBytes)
    val excludedOrganisations = configuration.getOptional[Seq[String]]("exclusion.organisation").getOrElse(Nil)
    val excludedTags          = configuration.getOptional[Seq[String]]("exclusion.tags").fold(Set.empty[String])(_.toSet)
    val whitelistTags         = configuration.getOptional[Seq[String]]("whitelist.tags").fold(Set.empty[String])(_.toSet)
    val purpose = configuration
      .getAndValidate[Option[String]]("purpose", MispPurpose.values.map(p => Some(p.toString)))
      .fold(MispPurpose.ImportAndExport)(MispPurpose.withName)
    val exportCaseTags               = configuration.getOptional[Boolean]("exportCaseTags").contains(true)
    val includedTheHiveOrganisations = configuration.getOptional[Seq[String]]("thehiveOrganisation.include").getOrElse(Seq("*"))
    val excludedTheHiveOrganisations = configuration.getOptional[Seq[String]]("thehiveOrganisation.exclude").getOrElse(Nil)

    new TheHiveMispClient(
      name,
      url,
      auth,
      ws,
      maxAge,
      maxAttributes,
      maxSize,
      excludedOrganisations,
      excludedTags,
      whitelistTags,
      purpose,
      caseTemplate,
      artifactTags,
      exportCaseTags,
      includedTheHiveOrganisations,
      excludedTheHiveOrganisations
    )
  }
}
