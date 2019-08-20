package org.thp.cortex.client

import com.google.inject.ProvidedBy
import javax.inject.{Inject, Provider, Singleton}
import org.thp.client.{Authentication, CustomWSAPI}
import play.api.Configuration

import scala.concurrent.duration._

/**
  * Injected CortexConfig containing all client instances
  *
  * @param clients the CortexClient instances
  */
@ProvidedBy(classOf[CortexConfigProvider])
case class CortexConfig(clients: Map[String, CortexClient], refreshDelay: FiniteDuration, maxRetryOnError: Int)

/**
  * The cortex config provider
  */
@Singleton
class CortexConfigProvider @Inject()(configuration: Configuration, globalWS: CustomWSAPI) extends Provider[CortexConfig] {
  override def get(): CortexConfig = CortexConfig(
    getClients(configuration, globalWS),
    configuration.getOptional[FiniteDuration]("cortex.refreshDelay").getOrElse(1.minute),
    configuration.getOptional[Int]("cortex.maxRetryOnError").getOrElse(3)
  )

  /**
    * Gets the list of client instances
    *
    * @param configuration the conf for each client and web service client
    * @param globalWS the overridden or not web service client framework WSClient
    * @return
    */
  def getClients(configuration: Configuration, globalWS: CustomWSAPI): Map[String, CortexClient] = {
    val cortexWS = globalWS.withConfig(configuration.get[Configuration]("cortex"))
    configuration
      .getOptional[Seq[Configuration]]("cortex.servers")
      .getOrElse(Nil)
      .map(getCortexClient(_, cortexWS))
      .map(client => client.name -> client)
      .toMap
  }

  /**
    * Tries to get a CortexClient according to configuration
    *
    * @param configuration the .conf
    * @param globalWS custom or not web service client
    * @return
    */
  def getCortexClient(configuration: Configuration, globalWS: CustomWSAPI): CortexClient = {
    val name = configuration.get[String]("name")
    val url = configuration
      .get[String]("url")
      .replaceFirst("/*$", "")

    val auth = configuration.get[Authentication](".")
    val ws   = globalWS.withConfig(configuration)
    val        includedTheHiveOrganisations =      configuration.getOptional[Seq[String]]("includedTheHiveOrganisations").getOrElse(List("*")),
    val excludedTheHiveOrganisations = configuration.getOptional[Seq[String]]("excludedTheHiveOrganisations").getOrElse(Nil)

    new CortexClient(name, url, includedTheHiveOrganisations, excludedTheHiveOrganisations)(ws, auth)
  }
}
