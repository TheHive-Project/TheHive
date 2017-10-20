package connectors.misp

import java.util.Date

import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._

import org.elastic4play.services.JsonFormat.attachmentFormat

object JsonFormat {

  implicit val mispAlertReads: Reads[MispAlert] = Reads[MispAlert] { json ⇒
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
      threatLevelString ← (json \ "threat_level_id").validate[String]
      threatLevel = threatLevelString.toLong
      isPublished ← (json \ "published").validate[Boolean]
    } yield MispAlert(
      org,
      eventId,
      date,
      publishDate,
      isPublished,
      s"#$eventId ${info.trim}",
      s"Imported from MISP Event #$eventId, created at $date",
      if (0 < threatLevel && threatLevel < 4) 4 - threatLevel else 2,
      alertTags,
      tlp,
      "")
  }

  implicit val mispAlertWrites: Writes[MispAlert] = Json.writes[MispAlert].transform((_: JsValue).asInstanceOf[JsObject] - "isPublished")

  implicit val attributeReads: Reads[MispAttribute] = Reads(json ⇒
    for {
      id ← (json \ "id").validate[String]
      tpe ← (json \ "type").validate[String]
      timestamp ← (json \ "timestamp").validate[String]
      date = new Date(timestamp.toLong * 1000)
      comment ← (json \ "comment").validate[String].orElse(JsSuccess(""))
      value ← (json \ "value").validate[String]
      category ← (json \ "category").validate[String]
      tags ← JsArray(json \ "EventTag" \\ "name").validate[Seq[String]]
    } yield MispAttribute(
      id,
      category,
      tpe,
      date,
      comment,
      value,
      tags))

  val tlpWrites: Writes[Long] = Writes[Long] {
    case 0 ⇒ JsString("tlp:white")
    case 1 ⇒ JsString("tlp:green")
    case 2 ⇒ JsString("tlp:amber")
    case 3 ⇒ JsString("tlp:red")
    case _ ⇒ JsString("tlp:amber")
  }

  implicit val exportedAttributeWrites: Writes[ExportedMispAttribute] = Writes[ExportedMispAttribute] { attribute ⇒
    Json.obj(
      "category" → attribute.category,
      "type" → attribute.tpe,
      "value" → attribute.value.fold[String](identity, _.name),
      "comment" → attribute.comment,
      "Tag" → Json.arr(
        Json.obj("name" → tlpWrites.writes(attribute.tlp))))
  }

  implicit val mispArtifactWrites: Writes[MispArtifact] = OWrites[MispArtifact] { artifact ⇒
    Json.obj(
      "dataType" → artifact.dataType,
      "message" → artifact.message,
      "tlp" → artifact.tlp,
      "tags" → artifact.tags,
      "startDate" → artifact.startDate) + (artifact.value match {
        case SimpleArtifactData(data)                           ⇒ "data" → JsString(data)
        case RemoteAttachmentArtifact(filename, reference, tpe) ⇒ "remoteAttachment" → Json.obj("filename" → filename, "reference" → reference, "type" → tpe)
        case AttachmentArtifact(attachment)                     ⇒ "attachment" → Json.toJson(attachment)
      })
  }
}