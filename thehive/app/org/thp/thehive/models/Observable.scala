package org.thp.thehive.models

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import play.api.libs.json.{Json, OFormat}

@EdgeEntity[Observable, KeyValue]
case class ObservableKeyValue()

@EdgeEntity[Observable, Attachment]
case class ObservableAttachment()

@EdgeEntity[Observable, Data]
case class ObservableData()

@EdgeEntity[Observable, Tag]
case class ObservableTag()

@VertexEntity
case class Observable(message: Option[String], tlp: Int, ioc: Boolean, sighted: Boolean)

object Observable {
  implicit val format: OFormat[Observable] = Json.format[Observable]
}

case class RichObservable(
    observable: Observable with Entity,
    `type`: ObservableType with Entity,
    data: Option[Data with Entity],
    attachment: Option[Attachment with Entity],
    tags: Seq[Tag with Entity],
    extensions: Seq[KeyValue with Entity]
) {
  val message: Option[String] = observable.message
  val tlp: Int                = observable.tlp
  val ioc: Boolean            = observable.ioc
  val sighted: Boolean        = observable.sighted
}

@DefineIndex(IndexType.unique, "data")
@VertexEntity
case class Data(data: String)
