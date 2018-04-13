package services

import scala.util.control.NonFatal

import javax.inject.{ Inject, Singleton }
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws._
import play.api.libs.ws.ahc.{ AhcWSClient, AhcWSClientConfig, AhcWSClientConfigParser }
import play.api.{ Configuration, Environment, Logger }

import akka.stream.Materializer
import com.typesafe.sslconfig.ssl.TrustStoreConfig

object CustomWSAPI {
  private[CustomWSAPI] lazy val logger = Logger(getClass)

  def parseWSConfig(config: Configuration): AhcWSClientConfig = {
    new AhcWSClientConfigParser(
      new WSConfigParser(config.underlying, getClass.getClassLoader).parse(),
      config.underlying,
      getClass.getClassLoader).parse()
  }

  def parseProxyConfig(config: Configuration): Option[WSProxyServer] =
    config.getOptional[Configuration]("play.ws.proxy").map { proxyConfig ⇒
      DefaultWSProxyServer(
        proxyConfig.get[String]("host"),
        proxyConfig.get[Int]("port"),
        proxyConfig.getOptional[String]("protocol"),
        proxyConfig.getOptional[String]("user"),
        proxyConfig.getOptional[String]("password"),
        proxyConfig.getOptional[String]("ntlmDomain"),
        proxyConfig.getOptional[String]("encoding"),
        proxyConfig.getOptional[Seq[String]]("nonProxyHosts"))
    }

  def getWS(config: Configuration)(implicit mat: Materializer): AhcWSClient = {
    val clientConfig = parseWSConfig(config)
    val clientConfigWithTruststore = config.getOptional[String]("play.cert") match {
      case Some(p) ⇒
        logger.warn(
          """Use of "cert" parameter in configuration file is deprecated. Please use:
            | ws.ssl {
            |   trustManager = {
            |     stores = [
            |       { type = "PEM", path = "/path/to/cacert.crt" },
            |       { type = "JKS", path = "/path/to/truststore.jks" }
            |     ]
            |   }
            | }
          """.stripMargin)
        clientConfig.copy(
          wsClientConfig = clientConfig.wsClientConfig.copy(
            ssl = clientConfig.wsClientConfig.ssl.withTrustManagerConfig(
              clientConfig.wsClientConfig.ssl.trustManagerConfig.withTrustStoreConfigs(
                clientConfig.wsClientConfig.ssl.trustManagerConfig.trustStoreConfigs :+ TrustStoreConfig(filePath = Some(p.toString), data = None)))))
      case None ⇒ clientConfig
    }
    AhcWSClient(clientConfigWithTruststore, None)
  }

  def getConfig(config: Configuration, path: String): Configuration = {
    Configuration(
      config.getOptional[Configuration](s"play.$path").getOrElse(Configuration.empty).underlying.withFallback(
        config.getOptional[Configuration](path).getOrElse(Configuration.empty).underlying))
  }
}

@Singleton
class CustomWSAPI(
    ws: AhcWSClient,
    val proxy: Option[WSProxyServer],
    config: Configuration,
    environment: Environment,
    lifecycle: ApplicationLifecycle,
    mat: Materializer) extends WSClient {
  private[CustomWSAPI] lazy val logger = Logger(getClass)

  @Inject() def this(config: Configuration, environment: Environment, lifecycle: ApplicationLifecycle, mat: Materializer) =
    this(
      CustomWSAPI.getWS(config)(mat),
      CustomWSAPI.parseProxyConfig(config),
      config, environment, lifecycle, mat)

  override def close(): Unit = ws.close()

  override def url(url: String): WSRequest = {
    val req = ws.url(url)
    proxy.fold(req)(req.withProxyServer)
  }

  override def underlying[T]: T = ws.underlying[T]

  def withConfig(subConfig: Configuration): CustomWSAPI = {
    logger.debug(s"Override WS configuration using $subConfig")
    try {
      new CustomWSAPI(
        Configuration(subConfig.underlying.atKey("play").withFallback(config.underlying)),
        environment, lifecycle, mat)
    }
    catch {
      case NonFatal(e) ⇒
        logger.error(s"WSAPI configuration error, use default values", e)
        this
    }
  }
}
