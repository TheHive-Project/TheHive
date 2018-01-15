package services

import play.api.libs.json.{ JsPath, Reads }
import play.api.libs.functional.syntax._
import play.api.libs.json._

package object mappers {

  implicit val simpleJsonUserReads: Reads[SimpleJsonUser] = (
    (JsPath \ "name").read[String] and
    (JsPath \ "username").read[String])(SimpleJsonUser.apply _)

  implicit val simpleJsonGroupReads: Reads[SimpleJsonGroups] = (JsPath \ "groups").read[List[String]].map { re ⇒ SimpleJsonGroups(re) }

}
