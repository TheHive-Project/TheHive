package org.thp.misp.dto

import java.util.Date

import play.api.libs.json.Reads

case class Event(
    id: String,
    published: Boolean,
    info: String,
    threatLevel: Option[Int],
    analysis: Option[Int],
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
  implicit val reads: Reads[Event] = ???
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
