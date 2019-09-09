package org.thp.misp.dto

import java.util.Date

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsArray, JsObject, JsPath, JsString, Json, OWrites, Reads, Writes}

case class Event(
    id: String,
    published: Boolean,
    info: String,
    threatLevel: Option[Int], // from string
    analysis: Option[Int],    // from String
    date: Date,
    publishDate: Date,
    org: String,
    orgc: String,
    attributeCount: Option[Int],
    distribution: Int,
    attributes: Seq[Attribute],
    tags: Seq[Tag]
)

object Event {

  import java.text.SimpleDateFormat

  val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
  implicit val reads: Reads[Event] =
    ((JsPath \ "id").read[String] and
      (JsPath \ "published").read[Boolean] and
      (JsPath \ "info").read[String] and
      (JsPath \ "threat_level_id").readNullable[String].map(_.map(_.toInt)) and
      (JsPath \ "analysis").readNullable[String].map(_.map(_.toInt)) and
      (JsPath \ "date").read[String].map(simpleDateFormat.parse) and
      (JsPath \ "publish_timestamp").read[String].map(t => new Date(t.toLong * 1000)) and
      (JsPath \ "Org" \ "name").read[String] and
      (JsPath \ "Orgc" \ "name").read[String] and
      (JsPath \ "attribute_count").readNullable[String].map(_.map(_.toInt)) and
      (JsPath \ "distribution").read[String].map(_.toInt) and
      (JsPath \ "attributes").readWithDefault[Seq[Attribute]](Nil) and
      (JsPath \ "EventTag").read[Seq[JsObject]].map(_.map(eventTag => (eventTag \ "Tag").as[Tag])))(Event.apply _)

  implicit val writes: OWrites[Event] = OWrites[Event] { event =>
    Json.obj(
      "distribution"    -> event.distribution,
      "threat_level_id" -> event.threatLevel,
      "analysis"        -> event.analysis,
      "info"            -> event.info,
      "date"            -> simpleDateFormat.format(event.date),
      "published"       -> false,
      "Tag"             -> event.tags.map(t => JsString(t.name))
    )
  }
}

//
//implicit val mispAlertReads: Reads[MispAlert] = Reads[MispAlert] {
//
//
//
//json ⇒
//for {
//org     ← (json \ "Orgc" \ "name").validate[String]
//info    ← (json \ "info").validate[String]
//eventId ← (json \ "id").validate[String]
//optTags ← (json \ "EventTag").validateOpt[Seq[JsValue]]
//tags = optTags.toSeq.flatten.flatMap(t ⇒ (t \ "Tag" \ "name").asOpt[String])
//tlp = tags
//.map(_.toLowerCase)
//.collectFirst {
//case "tlp:white" ⇒ 0L
//case "tlp:green" ⇒ 1L
//case "tlp:amber" ⇒ 2L
//case "tlp:red"   ⇒ 3L
//}
//.getOrElse(2L)
//alertTags = s"src:$org" +: tags.filterNot(_.toLowerCase.startsWith("tlp:"))
//timestamp ← (json \ "timestamp").validate[String]
//date = new Date(timestamp.toLong * 1000)
//publishTimestamp ← (json \ "publish_timestamp").validate[String]
//publishDate = new Date(publishTimestamp.toLong * 1000)
//threatLevelString ← (json \ "threat_level_id").validate[String]
//threatLevel = threatLevelString.toLong
//isPublished ← (json \ "published").validate[Boolean]
//extendsUuid = (json \ "extends_uuid").asOpt[String]
//} yield MispAlert(
//
//)
