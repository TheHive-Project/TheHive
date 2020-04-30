package org.thp.client

import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.ssl.{KeyStoreConfig, SSLConfigFactory, SSLConfigSettings, SSLDebugConfig, SSLLooseConfig, TrustStoreConfig}
import play.api.libs.json._
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
import play.api.libs.ws.{DefaultWSProxyServer, WSClient, WSClientConfig, WSProxyServer, WSRequest}
import org.thp.scalligraph.services.config.ApplicationConfig.durationFormat

case class ProxyWSConfig(wsConfig: AhcWSClientConfig = AhcWSClientConfig(), proxyConfig: Option[WSProxyServer] = None)

object ProxyWSConfig {
  implicit val defaultWSProxyServerFormat: Format[DefaultWSProxyServer] = Json.format[DefaultWSProxyServer]

  implicit val wsProxyServerFormat: Format[WSProxyServer] =
    Format[WSProxyServer](
      defaultWSProxyServerFormat.widen[WSProxyServer],
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

  implicit val sslConfigReads: Format[SSLConfigSettings] = Format[SSLConfigSettings](
    Reads[SSLConfigSettings](json => JsSuccess(SSLConfigFactory.parse(ConfigFactory.parseString(json.toString)))),
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

  implicit val wsClientConfigReads: Format[WSClientConfig] = Json.using[Json.WithDefaultValues].format[WSClientConfig]

  implicit val ahcWSClientConfigReads: Format[AhcWSClientConfig] = Json.using[Json.WithDefaultValues].format[AhcWSClientConfig]

  implicit val reads: Format[ProxyWSConfig] = Json.using[Json.WithDefaultValues].format[ProxyWSConfig]
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
