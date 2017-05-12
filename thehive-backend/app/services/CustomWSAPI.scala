package services

import javax.inject.Inject

import akka.stream.Materializer
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.ahc.{ AhcWSAPI, AhcWSClient, AhcWSClientConfig, AhcWSClientConfigParser }
import play.api.libs.ws.ssl.TrustStoreConfig
import play.api.libs.ws._
import play.api.{ Configuration, Environment, Logger }

object CustomWSAPI {
  private[CustomWSAPI] lazy val logger = Logger(getClass)

  def parseWSConfig(config: Configuration, environment: Environment): AhcWSClientConfig = {
    new AhcWSClientConfigParser(
      new WSConfigParser(config, environment).parse(),
      config,
      environment).parse()
  }

  def parseProxyConfig(config: Configuration): Option[WSProxyServer] = for {
    proxyConfig ← config.getConfig("play.ws.proxy")
    proxyHost ← proxyConfig.getString("host")
    proxyPort ← proxyConfig.getInt("port")
    proxyProtocol = proxyConfig.getString("protocol")
    proxyPrincipal = proxyConfig.getString("user")
    proxyPassword = proxyConfig.getString("password")
    proxyNtlmDomain = proxyConfig.getString("ntlmDomain")
    proxyEncoding = proxyConfig.getString("encoding")
    proxyNonProxyHosts = proxyConfig.getStringSeq("nonProxyHosts")
  } yield DefaultWSProxyServer(proxyHost, proxyPort, proxyProtocol, proxyPrincipal, proxyPassword, proxyNtlmDomain, proxyEncoding, proxyNonProxyHosts)

  def getWS(config: Configuration, environment: Environment, lifecycle: ApplicationLifecycle, mat: Materializer): AhcWSAPI = {
    val clientConfig = parseWSConfig(config, environment)
    val clientConfigWithTruststore = config.getString("play.cert") match {
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
            ssl = clientConfig.wsClientConfig.ssl.copy(
              trustManagerConfig = clientConfig.wsClientConfig.ssl.trustManagerConfig.copy(
                trustStoreConfigs = clientConfig.wsClientConfig.ssl.trustManagerConfig.trustStoreConfigs :+ TrustStoreConfig(filePath = Some(p.toString), data = None)))))
      case None ⇒ clientConfig
    }
    new AhcWSAPI(environment, clientConfigWithTruststore, lifecycle)(mat)
  }

  def getConfig(config: Configuration, path: String): Configuration = {
    Configuration(
      config.getConfig(s"play.$path").getOrElse(Configuration.empty).underlying.withFallback(
        config.getConfig(path).getOrElse(Configuration.empty).underlying))
  }
}

class CustomWSAPI(ws: AhcWSAPI, val proxy: Option[WSProxyServer], config: Configuration, environment: Environment, lifecycle: ApplicationLifecycle, mat: Materializer) extends WSAPI {
  private[CustomWSAPI] lazy val logger = Logger(getClass)

  @Inject() def this(config: Configuration, environment: Environment, lifecycle: ApplicationLifecycle, mat: Materializer) =
    this(
      CustomWSAPI.getWS(config, environment, lifecycle, mat),
      CustomWSAPI.parseProxyConfig(config),
      config, environment, lifecycle, mat)

  override def url(url: String): WSRequest = {
    val req = ws.url(url)
    proxy.fold(req)(req.withProxyServer)
  }

  override def client: AhcWSClient = ws.client

  def withConfig(subConfig: Configuration): CustomWSAPI = {
    logger.debug(s"Override WS configuration using $subConfig")
    new CustomWSAPI(
      Configuration(subConfig.underlying.atKey("play").withFallback(config.underlying)),
      environment, lifecycle, mat)
  }
}
