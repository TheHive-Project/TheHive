package org.thp.thehive.models

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[Observable, KeyValue]
case class ObservableKeyValue()

@EdgeEntity[Observable, Attachment]
case class ObservableAttachment()

@EdgeEntity[Observable, Data]
case class ObservableData()

@VertexEntity
case class Observable(`type`: String, tags: Set[String], message: Option[String], tlp: Int, ioc: Boolean, sighted: Boolean)

case class RichObservable(
    observable: Observable with Entity,
    data: Option[Data with Entity],
    attachment: Option[Attachment with Entity],
    extensions: Seq[KeyValue]
) {
  val `type`: String          = observable.`type` // TODO put "type" in dedicated vertex
  val tags: Set[String]       = observable.tags
  val message: Option[String] = observable.message
  val tlp: Int                = observable.tlp
  val ioc: Boolean            = observable.ioc
  val sighted: Boolean        = observable.sighted
}

@DefineIndex(IndexType.unique, "data")
@VertexEntity
case class Data(data: String)
