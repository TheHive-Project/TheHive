package org.thp.client

import play.api.http.HeaderNames
import play.api.libs.json._
import play.api.libs.ws.{WSAuthScheme, WSRequest}

trait Authentication {
  def apply(request: WSRequest): WSRequest
}

object Authentication {

  val reads: Reads[Authentication] = Reads[Authentication] { json =>
    (json \ "type").asOpt[String].fold[JsResult[Authentication]](JsSuccess(NoAuthentication)) {
      case "basic" =>
        for {
          username <- (json \ "username").validate[String]
          password <- (json \ "password").validate[String]
        } yield PasswordAuthentication(username, password)
      case "bearer" => (json \ "key").validate[String].map(KeyAuthentication(_, "Bearer "))
      case "key"    => (json \ "key").validate[String].map(KeyAuthentication(_, ""))
      case other    => JsError(s"Unknown authentication type: $other")
    }
  }

  val writes: Writes[Authentication] = Writes[Authentication] {
    case PasswordAuthentication(username, password) => Json.obj("type" -> "basic", "username" -> username, "password" -> password)
    case KeyAuthentication(key, "")                 => Json.obj("type" -> "key", "key"        -> key)
    case KeyAuthentication(key, "Bearer ")          => Json.obj("type" -> "bearer", "key"     -> key)
  }
  implicit val format: Format[Authentication] = Format(reads, writes)
}

case class PasswordAuthentication(username: String, password: String) extends Authentication {
  override def apply(request: WSRequest): WSRequest = request.withAuth(username, password, WSAuthScheme.BASIC)
}

case class KeyAuthentication(key: String, prefix: String) extends Authentication {
  override def apply(request: WSRequest): WSRequest = request.addHttpHeaders(HeaderNames.AUTHORIZATION -> s"$prefix$key")
}

object NoAuthentication extends Authentication {
  override def apply(request: WSRequest): WSRequest = request
}
