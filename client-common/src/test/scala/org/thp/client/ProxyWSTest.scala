package org.thp.client

import play.api.libs.json.{JsObject, Json}
import play.api.test.PlaySpecification

class ProxyWSTest extends PlaySpecification {
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
