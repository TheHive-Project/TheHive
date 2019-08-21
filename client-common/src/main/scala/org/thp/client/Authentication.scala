package org.thp.client

import scala.util.control.Exception.catching

import play.api.ConfigLoader
import play.api.libs.ws.{WSAuthScheme, WSRequest}
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders

import com.typesafe.config.{Config, ConfigException}

trait Authentication {
  def apply(request: WSRequest): WSRequest
}

object Authentication {
  implicit val configLoader: ConfigLoader[Authentication] = { (config: Config, path: String) =>
    val pathPrefix = if (path.isEmpty) path else s"$path."
    catching(classOf[ConfigException.Missing])
      .opt(config.getString(s"${pathPrefix}authType"))
      .fold[Authentication](NoAuthentication) {
        case "basic" => PasswordAuthentication(config.getString(s"${pathPrefix}username"), config.getString(s"${pathPrefix}password"))
        case "key"   => KeyAuthentication(config.getString(s"${pathPrefix}key"))
      }
  }
}

case class PasswordAuthentication(username: String, password: String) extends Authentication {
  override def apply(request: WSRequest): WSRequest = request.withAuth(username, password, WSAuthScheme.BASIC)
}

case class KeyAuthentication(key: String) extends Authentication {
  override def apply(request: WSRequest): WSRequest = request.addHttpHeaders(HttpHeaders.Names.AUTHORIZATION -> s"Bearer $key")
}

object NoAuthentication extends Authentication {
  override def apply(request: WSRequest): WSRequest = request
}
