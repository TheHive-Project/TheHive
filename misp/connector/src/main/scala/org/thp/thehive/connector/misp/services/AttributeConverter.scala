package org.thp.thehive.connector.misp.services

import org.thp.scalligraph.EntityName
import play.api.libs.json.{Format, Json}

case class AttributeConverter(mispCategory: String, mispType: String, `type`: EntityName, tags: Seq[String])

object AttributeConverter {
  implicit val format: Format[AttributeConverter] = Json.format[AttributeConverter]
}
