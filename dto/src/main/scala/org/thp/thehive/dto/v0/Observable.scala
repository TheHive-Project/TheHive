package org.thp.thehive.dto.v0
import java.util.Date

import play.api.libs.json.{Json, OFormat, Writes}

import org.thp.scalligraph.controllers.FFile

case class InputObservable(
    //caseId: String,
    dataType: String,
    data: Option[String] = None,
    message: Option[String] = None,
    startDate: Option[Date] = None,
    attachment: Option[FFile] = None,
    tlp: Option[Int] = None,
    tags: Seq[String] = Nil,
    ioc: Option[Boolean] = None,
    sighted: Option[Boolean] = None
)

object InputObservable {
  implicit val writes: Writes[InputObservable] = Json.writes[InputObservable]
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
//                             attachment: Option[]???
    tlp: Int,
    tags: Seq[String],
    ioc: Boolean,
    sighted: Boolean
)

//RichObservable(
//observable: Observable with Entity,
//data: Option[Data with Entity],
//attachment: Option[Attachment with Entity],
//extensions: Seq[KeyValue]) {
//val `type`: String          = observable.`type` // TODO put "type" in dedicated vertex
//val tags: Seq[String]       = observable.tags
//val message: Option[String] = observable.message
//val tlp: Int                = observable.tlp
//val ioc: Boolean            = observable.ioc
//val sighted: Boolean        = observable.sighted
//}

object OutputObservable {
  implicit val format: OFormat[OutputObservable] = Json.format[OutputObservable]
}
