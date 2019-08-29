package org.thp.client

import play.api.libs.json._
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
import play.api.libs.ws.{DefaultWSProxyServer, WSClient, WSClientConfig, WSProxyServer, WSRequest}

import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.ssl.{KeyStoreConfig, SSLConfigFactory, SSLConfigSettings, SSLDebugConfig, SSLLooseConfig, TrustStoreConfig}
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat

case class ProxyWSConfig(wsConfig: AhcWSClientConfig, proxyConfig: Option[WSProxyServer])

object ProxyWSConfig {
  implicit val defaultWSProxyServerFormat: Format[DefaultWSProxyServer] = Json.format[DefaultWSProxyServer]

  implicit val wsProxyServerFormat: Format[WSProxyServer] =
    Format[WSProxyServer](Reads(json => JsSuccess(json.as[DefaultWSProxyServer])), Writes(duration => JsString(duration.toString)))

  implicit val sslDebugConfigWrites: Writes[SSLDebugConfig] = Writes[SSLDebugConfig](
    conf =>
      Json.obj(
        "all"          -> conf.all,
        "certpath"     -> conf.certpath,
        "defaultctx"   -> conf.defaultctx,
        "handshake"    -> conf.handshake.fold[JsValue](JsNull)(h => Json.obj("data" -> h.data, "verbose" -> h.verbose)),
        "keygen"       -> conf.keygen,
        "keymanager"   -> conf.keymanager,
        "ocsp"         -> conf.ocsp,
        "pluggability" -> conf.pluggability,
        "record"       -> conf.record.fold[JsValue](JsNull)(r => Json.obj("packet" -> r.packet, "plaintext" -> r.plaintext)),
        "session"      -> conf.session,
        "sessioncache" -> conf.sessioncache,
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

  implicit val sslConfigReads: Format[SSLConfigSettings] = Format[SSLConfigSettings](
    Reads[SSLConfigSettings](json => JsSuccess(SSLConfigFactory.parse(ConfigFactory.parseString(json.toString)))),
    Writes[SSLConfigSettings] { c =>
      Json.obj(
        "default"                     -> c.default,
        "protocol"                    -> c.protocol,
        "checkRevocation"             -> c.checkRevocation,
        "revocationLists"             -> c.revocationLists.fold[JsValue](JsNull)(url => JsArray(url.map(u => JsString(u.toString)))),
        "debug"                       -> c.debug,
        "loose"                       -> c.loose,
        "enabledCipherSuites"         -> c.enabledCipherSuites,
        "enabledProtocols"            -> c.enabledProtocols,
        "hostnameVerifierClass"       -> c.hostnameVerifierClass.getCanonicalName,
        "disabledSignatureAlgorithms" -> c.disabledSignatureAlgorithms,
        "disabledKeyAlgorithms"       -> c.disabledKeyAlgorithms,
        "keyManager" -> Json.obj(
          "algorithm" -> c.keyManagerConfig.algorithm,
          "keyStore"  -> c.keyManagerConfig.keyStoreConfigs
        ),
        "trustManager" -> Json.obj(
          "algorithm"  -> c.trustManagerConfig.algorithm,
          "trustStore" -> c.trustManagerConfig.trustStoreConfigs
        ),
        "sslParameters" -> Json.obj(
          "clientAuth" -> c.sslParametersConfig.clientAuth.toString,
          "protocols"  -> c.sslParametersConfig.protocols
        )
      )
    }
  )

  implicit val wsClientConfigReads: Format[WSClientConfig] = Json.format[WSClientConfig]

  implicit val ahcWSClientConfigReads: Format[AhcWSClientConfig] = Json.format[AhcWSClientConfig]

  implicit val reads: Format[ProxyWSConfig] = Json.format[ProxyWSConfig]
}

class ProxyWS(ws: AhcWSClient, val proxy: Option[WSProxyServer]) extends WSClient {

  def this(wsConfig: AhcWSClientConfig, proxyConfig: Option[WSProxyServer], mat: Materializer) = this(AhcWSClient(wsConfig)(mat), proxyConfig)

  def this(config: ProxyWSConfig, mat: Materializer) = this(config.wsConfig, config.proxyConfig, mat)

  override def underlying[T]: T = ws.underlying

  override def url(url: String): WSRequest = {
    val req = ws.url(url)
    proxy.fold(req)(req.withProxyServer)
  }

  override def close(): Unit = ws.close()
}
