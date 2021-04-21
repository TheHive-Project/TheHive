package org.thp.client

import org.specs2.mutable.Specification
import play.api.libs.json.{JsObject, Json}

class ProxyWSTest extends Specification {
  "WS config" should {
    "be serializable" in {
      val proxyWSConfig = JsObject.empty.as[ProxyWSConfig]
      val json          = Json.toJson(proxyWSConfig)
      json.as[ProxyWSConfig]
      ok
    }

    "accept proxy configuration" in {
      val proxyWSConfig = Json
        .obj("proxy" -> Json.obj("host" -> "127.0.0.1", "port" -> 3128, "protocol" -> "http"))
        .as[ProxyWSConfig]
      val json = Json.toJson(proxyWSConfig)
      json.as[ProxyWSConfig].proxyConfig.map(_.host) must beSome("127.0.0.1")
    }

    "accept ssl config" in {
      val proxyWSConfig = Json
        .obj("ssl" -> Json.obj("protocol" -> "TLSv1.0"))
        .as[ProxyWSConfig]
      val json = Json.toJson(proxyWSConfig)
      json.as[ProxyWSConfig].wsConfig.wsClientConfig.ssl.protocol must beEqualTo("TLSv1.0")
    }
  }
}
