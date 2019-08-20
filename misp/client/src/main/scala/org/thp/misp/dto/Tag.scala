package org.thp.misp.dto

import java.awt.Color

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax._

case class Tag(
                id: Option[String],
                name: String,
                colour: Option[Color],
                exportable: Option[Boolean]
              )

object Tag {
  implicit val reads: Reads[Tag] =
    ((JsPath \ "id").readNullable[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "colour").readNullable[String].map {
        case Some(c) if c.headOption.contains('#') => Some(Color.decode(c.tail))
        case _                                     => None
      } and
      (JsPath \ "exportable").readNullable[Boolean])(Tag.apply _)
}