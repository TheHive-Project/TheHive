package org.thp.misp.dto

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

case class User(id: String, orgId: String, email: String)

object User {
  implicit val reads: Reads[User] =
    ((JsPath \ "id").read[String] and
      (JsPath \ "org_id").read[String] and
      (JsPath \ "email").read[String])(User.apply _)
}
