package org.thp.misp.dto

import java.time.OffsetDateTime
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.util.{Base64, Date}

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, OWrites, Reads}

case class Attribute(
    id: String,
    `type`: String,
    category: String,
    toIds: Boolean,
    eventId: String,
    distribution: Int,
    date: Date,
    comment: Option[String],
    deleted: Boolean,
    data: Option[(String, String, Source[ByteString, _])],
    value: String, // TODO need check: option for attachment ?
    firstSeen: Option[Date],
    lastSeen: Option[Date],
    tags: Seq[Tag]
)

object Attribute {

  val formatter: DateTimeFormatter = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .appendPattern("XX")
    .toFormatter
  def parseDate(s: String): Date = new Date(OffsetDateTime.parse(s, formatter).toInstant.toEpochMilli)

  implicit val reads: Reads[Attribute] =
    ((JsPath \ "id").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "category").read[String] and
      (JsPath \ "to_ids").read[Boolean] and
      (JsPath \ "event_id").read[String] and
      (JsPath \ "distribution").read[String].map(_.toInt) and
      (JsPath \ "timestamp").read[String].map(t => new Date(t.toLong * 1000)) and
      (JsPath \ "comment").readNullable[String] and
      (JsPath \ "deleted").read[Boolean] and
      (JsPath \ "data").readNullable[String].map(_.map(s => ("", "", Source.single(ByteString(Base64.getDecoder.decode(s)))))) and // TODO need check
      (JsPath \ "value").read[String] and
      (JsPath \ "first_seen").readNullable[String].map(_.map(parseDate)) and //"2019-06-02T22:14:28.711954+00:00"
      (JsPath \ "last_seen").readNullable[String].map(_.map(parseDate)) and
      (JsPath \ "Tag").readWithDefault[Seq[Tag]](Nil))(Attribute.apply _)

  implicit val writes: OWrites[Attribute] = OWrites[Attribute] { attribute =>
    Json.obj(
      "category" -> attribute.category,
      "type"     -> attribute.`type`,
      "value"    -> attribute.value,
      "comment"  -> attribute.comment
//      "Tag"      -> attribute.tags
    )
  }
}
