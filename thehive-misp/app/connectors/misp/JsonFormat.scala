package connectors.misp

import java.util.Date

import org.elastic4play.models.JsonFormat.enumFormat
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.{ JsSuccess, JsValue, Json, Reads }

object JsonFormat {
  implicit val eventStatusFormat = enumFormat(EventStatus)
  implicit val eventReads = Reads(json ⇒
    for {
      uuid ← (json \ "uuid").validate[String]
      org ← (json \ "Orgc" \ "name").validate[String]
      info ← (json \ "info").validate[String]
      eventId ← (json \ "id").validate[String]
      optTags ← (json \ "EventTag").validateOpt[Seq[JsValue]]
      tags = optTags.toSeq.flatten.flatMap(t ⇒ (t \ "Tag" \ "name").asOpt[String])
      attrCountStr ← (json \ "attribute_count").validate[String].recover { case _ ⇒ "0" }
      attrCount = attrCountStr.toInt
      timestamp ← (json \ "timestamp").validate[String]
      date = new Date(timestamp.toLong * 1000)
      publishTimestamp ← (json \ "publish_timestamp").validate[String]
      publishDate = new Date(publishTimestamp.toLong * 1000)
      threatLevel ← (json \ "threat_level_id").validate[String]
      analysis ← (json \ "analysis").validate[String]
    } yield MispEvent(uuid, "", eventId.toLong, org, info, tags, date, publishDate, attrCount, threatLevel.toInt, analysis.toInt))

  implicit val eventWrites = Json.writes[MispEvent]

  implicit val attributeReads = Reads(json ⇒
    for {
      id ← (json \ "id").validate[String]
      tpe ← (json \ "type").validate[String]
      category ← (json \ "category").validate[String]
      uuid ← (json \ "uuid").validate[String]
      eventId ← (json \ "id").validate[String]
      timestamp ← (json \ "timestamp").validate[String]
      date = new Date(timestamp.toLong * 1000)
      comment ← (json \ "comment").validate[String].orElse(JsSuccess(""))
      value ← (json \ "value").validate[String]
    } yield MispAttribute(id, tpe, category, uuid, eventId.toLong, date, comment, value))
}