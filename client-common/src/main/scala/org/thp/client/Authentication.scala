package org.thp.client

import com.typesafe.config.{Config, ConfigException}
import play.api.ConfigLoader
import play.api.libs.ws.{WSAuthScheme, WSRequest}
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders
import scala.util.control.Exception.catching

trait Authentication {
  def apply(request: WSRequest): WSRequest
}

object Authentication {
  implicit val configLoader: ConfigLoader[Authentication] = (config: Config, path: String) =>
    catching(classOf[ConfigException.Missing])
      .opt(config.getString(s"$path.authType"))
      .fold[Authentication](NoAuthentication) {
        case "basic" => PasswordAuthentication(config.getString(s"$path.username"), config.getString(s"$path.password"))
        case "key"   => KeyAuthentication(config.getString(s"$path.key"))
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
