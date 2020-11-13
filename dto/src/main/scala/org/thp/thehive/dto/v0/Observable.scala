package org.thp.thehive.dto.v0

import java.util.Date

import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.controllers._
import play.api.libs.json.{JsObject, Json, OFormat, Writes}

case class InputObservable(
    dataType: String,
    @WithParser(InputObservable.fp)
    data: Seq[String] = Nil,
    message: Option[String] = None,
    startDate: Option[Date] = None,
    attachment: Option[FFile] = None,
    tlp: Option[Int] = None,
    tags: Set[String] = Set.empty,
    ioc: Option[Boolean] = None,
    sighted: Option[Boolean] = None,
    ignoreSimilarity: Option[Boolean] = None
)

object InputObservable {
  implicit val writes: Writes[InputObservable] = Json.writes[InputObservable]

  val fp: FieldsParser[Seq[String]] = FieldsParser[Seq[String]]("data") {
    case (_, FString(s)) => Good(Seq(s))
    case (_, FAny(s))    => Good(s)
    case (_, FSeq(a))    => a.validatedBy(FieldsParser.string(_))
    case (_, FUndefined) => Good(Nil)
  }
}

case class OutputObservable(
    _id: String,
    id: String, // _id
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    dataType: String,
    data: Option[String],
    startDate: Date,
    attachment: Option[OutputAttachment],
    tlp: Int,
    tags: Set[String],
    ioc: Boolean,
    sighted: Boolean,
    message: Option[String],
    reports: JsObject,
    stats: JsObject,
    seen: Option[Boolean],
    `case`: Option[OutputCase],
    ignoreSimilarity: Option[Boolean]
)

object OutputObservable {
  implicit val format: OFormat[OutputObservable] = Json.format[OutputObservable]
}
