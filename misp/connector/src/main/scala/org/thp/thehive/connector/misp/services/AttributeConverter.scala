package org.thp.thehive.connector.misp.services

import play.api.libs.json.{Format, Json}

case class AttributeConverter(mispCategory: String, mispType: String, `type`: String, tags: Seq[String])

object AttributeConverter {
  implicit val format: Format[AttributeConverter] = Json.format[AttributeConverter]
}
