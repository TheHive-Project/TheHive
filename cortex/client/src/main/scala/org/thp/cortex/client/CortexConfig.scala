package org.thp.cortex.client

import com.google.inject.ProvidedBy
import javax.inject.{Inject, Provider, Singleton}
import play.api.Configuration

import scala.concurrent.duration._

/**
  * Injected CortexConfig containing all client instances
  *
  * @param instances the CortexClient instances
  */
@ProvidedBy(classOf[CortexConfigProvider])
case class CortexConfig(instances: Map[String, CortexClient], refreshDelay: FiniteDuration, maxRetryOnError: Int)

/**
  * The cortex config provider
  */
@Singleton
class CortexConfigProvider @Inject()(configuration: Configuration, globalWS: CustomWSAPI) extends Provider[CortexConfig] {
  override def get(): CortexConfig = CortexConfig(
    getInstances(configuration, globalWS),
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
  def getInstances(configuration: Configuration, globalWS: CustomWSAPI): Map[String, CortexClient] =
    (for {
      cfg <- configuration.getOptional[Configuration]("cortex").toSeq
      cortexWS = globalWS.withConfig(cfg)
      cfgs <- cfg.getOptional[Seq[Configuration]]("servers").toSeq
      c    <- cfgs
      instanceWS = cortexWS.withConfig(c)
      cic <- getCortexClient(c, instanceWS)
    } yield cic.name -> cic).toMap

  /**
    * Tries to get a CortexClient according to configuration
    *
    * @param configuration the .conf
    * @param ws custom or not web service client
    * @return
    */
  def getCortexClient(configuration: Configuration, ws: CustomWSAPI): Option[CortexClient] = {
    val url = configuration.getOptional[String]("url").getOrElse(sys.error("url is missing")).replaceFirst("/*$", "")

    configuration
      .getOptional[String]("key")
      .map(KeyAuthentication)
      .orElse {
        for {
          basicEnabled <- configuration.getOptional[Boolean]("basicAuth")
          if basicEnabled
          username <- configuration.getOptional[String]("username")
          password <- configuration.getOptional[String]("password")
        } yield PasswordAuthentication(username, password)
      }
      .map(
        auth =>
          new CortexClient(
            configuration.getOptional[String]("name").getOrElse("no name"),
            url,
            configuration.getOptional[Seq[String]]("includedTheHiveOrganisations").getOrElse(List("*")),
            configuration.getOptional[Seq[String]]("excludedTheHiveOrganisations").getOrElse(Nil)
          )(ws, auth)
      )
  }
}
