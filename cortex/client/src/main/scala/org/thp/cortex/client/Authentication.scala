package org.thp.cortex.client

import play.api.libs.ws.{WSAuthScheme, WSRequest}
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders

trait Authentication {
  def apply(request: WSRequest): WSRequest
}

case class PasswordAuthentication(username: String, password: String) extends Authentication {
  override def apply(request: WSRequest): WSRequest = request.withAuth(username, password, WSAuthScheme.BASIC)
}

case class KeyAuthentication(key: String) extends Authentication {
  override def apply(request: WSRequest): WSRequest = request.addHttpHeaders(HttpHeaders.Names.AUTHORIZATION -> s"Bearer $key")
}
