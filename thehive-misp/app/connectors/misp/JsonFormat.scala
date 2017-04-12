package connectors.misp

import java.util.Date

import org.elastic4play.JsonFormat.dateFormat
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._

object JsonFormat {

  implicit val mispAlertReads = Reads { json ⇒
    for {
      org ← (json \ "Orgc" \ "name").validate[String]
      info ← (json \ "info").validate[String]
      eventId ← (json \ "id").validate[String]
      optTags ← (json \ "EventTag").validateOpt[Seq[JsValue]]
      tags = optTags.toSeq.flatten.flatMap(t ⇒ (t \ "Tag" \ "name").asOpt[String])
      tlp = tags
        .map(_.toLowerCase)
        .collectFirst {
          case "tlp:white" ⇒ 0L
          case "tlp:green" ⇒ 1L
          case "tlp:amber" ⇒ 2L
          case "tlp:red"   ⇒ 3L
        }
        .getOrElse(2L)
      alertTags = s"src:$org" +: tags.filterNot(_.toLowerCase.startsWith("tlp:"))
      timestamp ← (json \ "timestamp").validate[String]
      date = new Date(timestamp.toLong * 1000)
      publishTimestamp ← (json \ "publish_timestamp").validate[String]
      publishDate = new Date(publishTimestamp.toLong * 1000)
      threatLevel ← (json \ "threat_level_id").validate[String]
    } yield MispAlert(
      org,
      eventId,
      date,
      publishDate,
      s"#$eventId ${info.trim}",
      s"Imported from MISP Event #$eventId, created at $date",
      threatLevel.toLong,
      alertTags,
      tlp,
      "")
  }

  implicit val mispAlertWrites = Json.writes[MispAlert]

  implicit val attributeReads = Reads(json ⇒
    for {
      id ← (json \ "id").validate[String]
      tpe ← (json \ "type").validate[String]
      timestamp ← (json \ "timestamp").validate[String]
      date = new Date(timestamp.toLong * 1000)
      comment = (json \ "comment").asOpt[String].getOrElse("")
      value ← (json \ "value").validate[String]
      category ← (json \ "category").validate[String]
      tags ← JsArray((json \ "EventTag" \\ "name")).validate[Seq[String]]
    } yield MispAttribute(
      id,
      tpe,
      date,
      comment,
      value,
      tags :+ s"MISP:category$category" :+ s"MISP:type=$tpe"))
}
