package org.thp.cortex.client

import scala.concurrent.duration._
import scala.language.postfixOps

import play.api.Configuration

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}

/**
  * Injected CortexConfig containing all client instances
  *
  * @param instances the CortexClient instances
  */
@Singleton
case class CortexConfig(instances: Seq[CortexClient]) {

  @Inject
  def this(configuration: Configuration, globalWS: CustomWSAPI)(
      implicit system: ActorSystem
  ) = this(CortexConfig.getInstances(configuration, globalWS))
}

/**
  * The config's companion helper
  */
object CortexConfig {

  /**
    * Gets the list of client instances
    *
    * @param configuration the conf for each client and web service client
    * @param globalWS the overridden or not web service client framework WSClient
    * @return
    */
  def getInstances(configuration: Configuration, globalWS: CustomWSAPI)(
      implicit system: ActorSystem
  ): Seq[CortexClient] =
    for {
      cfg <- configuration.getOptional[Configuration]("cortex").toSeq
      cortexWS = globalWS.withConfig(cfg)
      cfgs <- cfg.getOptional[Seq[Configuration]]("servers").toSeq
      c    <- cfgs
      instanceWS = cortexWS.withConfig(c)
      cic <- getCortexClient(c, instanceWS)
    } yield cic

  /**
    * Tries to get a CortexClient according to configuration
    *
    * @param configuration the .conf
    * @param ws custom or not web service client
    * @return
    */
  def getCortexClient(configuration: Configuration, ws: CustomWSAPI)(
      implicit system: ActorSystem
  ): Option[CortexClient] = {
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
          // Refresh and maxRetry should not be by client now but global to
          // JobUpdater actor
          new CortexClient(
            configuration.getOptional[String]("name").getOrElse("no name"),
            url,
            configuration.getOptional[FiniteDuration]("refreshDelay").getOrElse(1 minute),
            configuration.getOptional[Int]("maxRetryOnError").getOrElse(3)
          )(ws, auth, system)
      )
  }
}
