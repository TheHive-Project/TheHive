package org.thp.thehive.dto.v0

import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.controllers._
import org.thp.thehive.dto.{Description, MultiLineString512, String128, String32, Tlp}
import play.api.libs.json.{JsObject, Json, OFormat, Writes}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputObservable(
    dataType: String32,
    @WithParser(InputObservable.dataParser)
    data: Seq[MultiLineString512] = Nil,
    message: Option[Description] = None,
    startDate: Option[Date] = None,
    @WithParser(InputObservable.fileOrAttachmentParser)
    attachment: Seq[Either[FFile, InputAttachment]] = Seq.empty,
    tlp: Option[Tlp] = None,
    tags: Set[String128] = Set.empty,
    ioc: Option[Boolean] = None,
    sighted: Option[Boolean] = None,
    ignoreSimilarity: Option[Boolean] = None
)

object InputObservable {
  implicit val fileOrAttachmentWrites: Writes[Either[FFile, InputAttachment]] = Writes[Either[FFile, InputAttachment]] {
    case Left(file)        => Json.toJson(file)
    case Right(attachment) => Json.toJson(attachment)
  }
  implicit val writes: Writes[InputObservable] = Json.writes[InputObservable]

  val dataParser: FieldsParser[Seq[MultiLineString512]] = FieldsParser[Seq[MultiLineString512]]("data") {
    case (_, FString(s)) => Good(Seq(MultiLineString512("data", s)))
    case (_, FAny(s))    => Good(s.map(MultiLineString512("data", _)))
    case (_, FSeq(a))    => a.validatedBy(d => FieldsParser.string(d).map(MultiLineString512("data", _)))
    case (_, FUndefined) => Good(Nil)
  }

  val fileOrAttachmentParser: FieldsParser[Seq[Either[FFile, InputAttachment]]] =
    FieldsParser[FFile]
      .map("fileOrAttachmentParser")(f => Seq(Left(f)))
      .recover(
        FieldsParser[InputAttachment]
          .map("fileOrAttachmentParser")(a => Seq(Right(a)))
          .recover(
            FieldsParser[InputAttachment]
              .sequence
              .map("fileOrAttachmentParser")(as => as.map(Right(_)))
          )
      )
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
    alert: Option[OutputAlert],
    ignoreSimilarity: Option[Boolean]
)

object OutputObservable {
  implicit val format: OFormat[OutputObservable] = Json.format[OutputObservable]
}
