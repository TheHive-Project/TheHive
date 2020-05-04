package org.thp.client

import play.api.libs.json.{JsObject, Json}
import play.api.test.PlaySpecification

class ProxyWSTest extends PlaySpecification {
  "WS config" should {
    "be serializable" in {
      val proxyWSConfig = JsObject.empty.as[ProxyWSConfig]
      val json          = Json.toJson(proxyWSConfig)
      println(Json.prettyPrint(json))
      json.as[ProxyWSConfig]
      ok
    }
  }
}
