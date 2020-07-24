package org.thp.client

import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.sslconfig.ssl.{KeyStoreConfig, SSLConfigFactory, SSLConfigSettings, SSLDebugConfig, SSLLooseConfig, TrustStoreConfig}
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat
import play.api.libs.json._
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
import play.api.libs.ws.{DefaultWSProxyServer, WSClient, WSClientConfig, WSProxyServer, WSRequest}

import scala.concurrent.duration.{Duration, DurationInt}

case class ProxyWSConfig(wsConfig: AhcWSClientConfig = AhcWSClientConfig(), proxyConfig: Option[WSProxyServer] = None)

object ProxyWSConfig {
  val defaultWSProxyServerReads: Reads[DefaultWSProxyServer] = Json.reads[DefaultWSProxyServer]

  implicit val wsProxyServerFormat: Format[WSProxyServer] =
    Format[WSProxyServer](
      defaultWSProxyServerReads.widen[WSProxyServer],
      Writes { c =>
        Json.obj(
          "host"          -> c.host,
          "port"          -> c.port,
          "protocol"      -> c.protocol,
          "principal"     -> c.principal,
          "password"      -> c.password,
          "ntlmDomain"    -> c.ntlmDomain,
          "encoding"      -> c.encoding,
          "nonProxyHosts" -> c.nonProxyHosts
        )
      }
    )

  implicit val sslDebugConfigWrites: Writes[SSLDebugConfig] = Writes[SSLDebugConfig](conf =>
    Json.obj(
      "all"          -> conf.all,
      "keymanager"   -> conf.keymanager,
      "ssl"          -> conf.ssl,
      "sslctx"       -> conf.sslctx,
      "trustmanager" -> conf.trustmanager
    )
  )

  implicit val sslLooseConfigWrites: Writes[SSLLooseConfig] = Writes[SSLLooseConfig] { c =>
    Json.obj(
      "acceptAnyCertificate"        -> c.acceptAnyCertificate,
      "allowLegacyHelloMessages"    -> c.allowLegacyHelloMessages,
      "allowUnsafeRenegotiation"    -> c.allowUnsafeRenegotiation,
      "allowWeakCiphers"            -> c.allowWeakCiphers,
      "allowWeakProtocols"          -> c.allowWeakProtocols,
      "disableHostnameVerification" -> c.disableHostnameVerification,
      "disableSNI"                  -> c.disableSNI
    )
  }

  implicit val keyStoreConfigWrites: Writes[KeyStoreConfig] = Writes[KeyStoreConfig] { c =>
    Json.obj(
      "data"      -> c.data,
      "filePath"  -> c.filePath,
      "password"  -> c.password,
      "storeType" -> c.storeType
    )
  }
  implicit val trustStoreConfigWrites: Writes[TrustStoreConfig] = Writes[TrustStoreConfig] { c =>
    Json.obj(
      "data"      -> c.data,
      "filePath"  -> c.filePath,
      "storeType" -> c.storeType
    )
  }

  lazy val defaultSSLConfig: Config = ConfigFactory.load().getConfig("ssl-config")
  implicit val sslConfigFormat: Format[SSLConfigSettings] = Format[SSLConfigSettings](
    Reads[SSLConfigSettings](json => JsSuccess(SSLConfigFactory.parse(ConfigFactory.parseString(json.toString).withFallback(defaultSSLConfig)))),
    Writes[SSLConfigSettings] { c =>
      Json.obj(
        "default"                     -> c.default,
        "protocol"                    -> c.protocol,
        "checkRevocation"             -> c.checkRevocation,
        "revocationLists"             -> c.revocationLists.fold[JsValue](JsArray.empty)(url => JsArray(url.map(u => JsString(u.toString)))),
        "debug"                       -> c.debug,
        "loose"                       -> c.loose,
        "enabledCipherSuites"         -> c.enabledCipherSuites.fold[JsValue](JsArray.empty)(Json.toJson(_)),
        "enabledProtocols"            -> c.enabledProtocols.fold[JsValue](JsArray.empty)(Json.toJson(_)),
        "hostnameVerifierClass"       -> c.hostnameVerifierClass.getCanonicalName,
        "disabledSignatureAlgorithms" -> c.disabledSignatureAlgorithms,
        "disabledKeyAlgorithms"       -> c.disabledKeyAlgorithms,
        "keyManager" -> Json.obj(
          "algorithm" -> c.keyManagerConfig.algorithm,
          "stores"    -> c.keyManagerConfig.keyStoreConfigs,
          "prototype" -> Json.obj(
            "stores" -> Json.obj(
              "type"     -> JsNull,
              "path"     -> JsNull,
              "data"     -> JsNull,
              "password" -> JsNull
            )
          )
        ),
        "trustManager" -> Json.obj(
          "algorithm" -> c.trustManagerConfig.algorithm,
          "stores"    -> c.trustManagerConfig.trustStoreConfigs,
          "prototype" -> Json.obj(
            "stores" -> Json.obj(
              "type" -> JsNull,
              "path" -> JsNull,
              "data" -> JsNull
            )
          )
        ),
        "sslParameters" -> Json.obj(
          "clientAuth" -> c.sslParametersConfig.clientAuth.toString,
          "protocols"  -> c.sslParametersConfig.protocols
        )
      )
    }
  )

  implicit val format: OFormat[ProxyWSConfig] = OFormat[ProxyWSConfig](
    json =>
      for {
        connectionTimeout           <- (json \ "timeout.connection").validateOpt[Duration].map(_.getOrElse(2.minutes))
        idleTimeout                 <- (json \ "timeout.idle").validateOpt[Duration].map(_.getOrElse(2.minutes))
        requestTimeout              <- (json \ "timeout.request").validateOpt[Duration].map(_.getOrElse(2.minutes))
        followRedirects             <- (json \ "followRedirects").validateOpt[Boolean].map(_.getOrElse(true))
        useProxyProperties          <- (json \ "useProxyProperties").validateOpt[Boolean].map(_.getOrElse(true))
        userAgent                   <- (json \ "userAgent").validateOpt[String]
        compressionEnabled          <- (json \ "compressionEnabled").validateOpt[Boolean].map(_.getOrElse(false))
        ssl                         <- (json \ "ssl").validateOpt[SSLConfigSettings].map(_.getOrElse(SSLConfigSettings()))
        maxConnectionsPerHost       <- (json \ "maxConnectionsPerHost").validateOpt[Int].map(_.getOrElse(-1))
        maxConnectionsTotal         <- (json \ "maxConnectionsTotal").validateOpt[Int].map(_.getOrElse(-1))
        maxConnectionLifetime       <- (json \ "maxConnectionLifetime").validateOpt[Duration].map(_.getOrElse(Duration.Inf))
        idleConnectionInPoolTimeout <- (json \ "idleConnectionInPoolTimeout").validateOpt[Duration].map(_.getOrElse(1.minute))
        maxNumberOfRedirects        <- (json \ "maxNumberOfRedirects").validateOpt[Int].map(_.getOrElse(5))
        maxRequestRetry             <- (json \ "maxRequestRetry").validateOpt[Int].map(_.getOrElse(5))
        disableUrlEncoding          <- (json \ "disableUrlEncoding").validateOpt[Boolean].map(_.getOrElse(false))
        keepAlive                   <- (json \ "keepAlive").validateOpt[Boolean].map(_.getOrElse(true))
        useLaxCookieEncoder         <- (json \ "useLaxCookieEncoder").validateOpt[Boolean].map(_.getOrElse(false))
        useCookieStore              <- (json \ "useCookieStore").validateOpt[Boolean].map(_.getOrElse(false))

        host          <- (json \ "proxy" \ "host").validateOpt[String]
        port          <- (json \ "proxy" \ "port").validateOpt[Int]
        protocol      <- (json \ "proxy" \ "protocol").validateOpt[String]
        principal     <- (json \ "proxy" \ "principal").validateOpt[String]
        password      <- (json \ "proxy" \ "password").validateOpt[String]
        ntlmDomain    <- (json \ "proxy" \ "ntlmDomain").validateOpt[String]
        encoding      <- (json \ "proxy" \ "encoding").validateOpt[String]
        nonProxyHosts <- (json \ "proxy" \ "nonProxyHosts").validateOpt[Seq[String]]
      } yield ProxyWSConfig(
        AhcWSClientConfig(
          WSClientConfig(connectionTimeout, idleTimeout, requestTimeout, followRedirects, useProxyProperties, userAgent, compressionEnabled, ssl),
          maxConnectionsPerHost,
          maxConnectionsTotal,
          maxConnectionLifetime,
          idleConnectionInPoolTimeout,
          maxNumberOfRedirects,
          maxRequestRetry,
          disableUrlEncoding,
          keepAlive,
          useLaxCookieEncoder,
          useCookieStore
        ),
        host.map(DefaultWSProxyServer(_, port.getOrElse(3128), protocol, principal, password, ntlmDomain, encoding, nonProxyHosts))
      ), { cfg: ProxyWSConfig =>
      val wsConfig =
        Json.obj(
          "timeout" -> Json.obj(
            "connection" -> cfg.wsConfig.wsClientConfig.connectionTimeout,
            "idle"       -> cfg.wsConfig.wsClientConfig.idleTimeout,
            "request"    -> cfg.wsConfig.wsClientConfig.requestTimeout
          ),
          "followRedirects"             -> cfg.wsConfig.wsClientConfig.followRedirects,
          "useProxyProperties"          -> cfg.wsConfig.wsClientConfig.useProxyProperties,
          "userAgent"                   -> cfg.wsConfig.wsClientConfig.userAgent,
          "compressionEnabled"          -> cfg.wsConfig.wsClientConfig.compressionEnabled,
          "ssl"                         -> cfg.wsConfig.wsClientConfig.ssl,
          "maxConnectionsPerHost"       -> cfg.wsConfig.maxConnectionsPerHost,
          "maxConnectionsTotal"         -> cfg.wsConfig.maxConnectionsTotal,
          "maxConnectionLifetime"       -> cfg.wsConfig.maxConnectionLifetime,
          "idleConnectionInPoolTimeout" -> cfg.wsConfig.idleConnectionInPoolTimeout,
          "maxNumberOfRedirects"        -> cfg.wsConfig.maxNumberOfRedirects,
          "maxRequestRetry"             -> cfg.wsConfig.maxRequestRetry,
          "disableUrlEncoding"          -> cfg.wsConfig.disableUrlEncoding,
          "keepAlive"                   -> cfg.wsConfig.keepAlive,
          "useLaxCookieEncoder"         -> cfg.wsConfig.useLaxCookieEncoder,
          "useCookieStore"              -> cfg.wsConfig.useCookieStore
        )
      cfg
        .proxyConfig
        .fold(wsConfig)(proxyConfig =>
          wsConfig + ("proxy" ->
            Json.obj(
              "host"          -> proxyConfig.host,
              "port"          -> proxyConfig.port,
              "protocol"      -> proxyConfig.protocol,
              "principal"     -> proxyConfig.principal,
              "password"      -> proxyConfig.password,
              "ntlmDomain"    -> proxyConfig.ntlmDomain,
              "encoding"      -> proxyConfig.encoding,
              "nonProxyHosts" -> proxyConfig.nonProxyHosts
            ))
        )
    }
  )
}

class ProxyWS(ws: AhcWSClient, val proxy: Option[WSProxyServer]) extends WSClient {

  def this(wsConfig: AhcWSClientConfig, proxyConfig: Option[WSProxyServer], mat: Materializer) = this(AhcWSClient(wsConfig)(mat), proxyConfig)

  def this(config: ProxyWSConfig, mat: Materializer) = this(config.wsConfig, config.proxyConfig, mat)

  def this(mat: Materializer) = this(AhcWSClient()(mat), None)

  override def underlying[T]: T = ws.underlying

  override def url(url: String): WSRequest = {
    val req = ws.url(url)
    proxy.fold(req)(req.withProxyServer)
  }

  override def close(): Unit = ws.close()
}
