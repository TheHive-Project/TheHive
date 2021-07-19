package org.thp.thehive.dto.v1

import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.controllers._
import org.thp.thehive.dto._
import play.api.libs.json._

import java.nio.file.Paths
import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputObservable(
    dataType: String32,
    @WithParser(InputObservable.dataParser)
    data: Seq[String512] = Nil,
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

  val dataParser: FieldsParser[Seq[String]] = FieldsParser[Seq[String]]("data") {
    case (_, FString(s)) => Good(Seq(s))
    case (_, FAny(s))    => Good(s)
    case (_, FSeq(a))    => a.validatedBy(FieldsParser.string(_))
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

  private val readsFFile: Reads[FFile] = Reads[FFile] { json =>
    for {
      filename    <- (json \ "filename").validate[String]
      contentType <- (json \ "contentType").validate[String]
    } yield FFile(filename, Paths.get("/dev/null"), contentType) // avoid reading untrusted paths from json
  }

  implicit val fileOrAttachmentReads: Reads[Either[FFile, InputAttachment]] = Reads.JsObjectReads.flatMap { jsObject =>
    if (jsObject.value.contains("filepath"))
      readsFFile.map(Left(_))
    else
      Json.reads[InputAttachment].map(Right(_))
  }

  implicit val reads: Reads[InputObservable] = Json.reads[InputObservable]
}

case class OutputObservable(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    dataType: String,
    data: Option[String],
    startDate: Date,
    attachment: Option[OutputAttachment],
    tlp: Int,
    tags: Set[String],
    ioc: Boolean,
    sighted: Boolean,
    reports: JsObject,
    message: Option[String],
    extraData: JsObject,
    ignoreSimilarity: Option[Boolean]
)

object OutputObservable {
  implicit val format: OFormat[OutputObservable] = Json.format[OutputObservable]
}
