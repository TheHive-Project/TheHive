package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[Observable, KeyValue]
case class ObservableKeyValue()

@EdgeEntity[Observable, Attachment]
case class ObservableAttachment()

@EdgeEntity[Observable, Data]
case class ObservableData()

@EdgeEntity[Observable, Case]
case class ObservableCase()

@VertexEntity
case class Observable(`type`: String, tags: Seq[String], message: Option[String], tlp: Int, pap: Int, ioc: Boolean, sighted: Boolean)

case class RichObservable(
    observable: Observable with Entity,
    data: Option[Data with Entity],
    attachment: Option[Attachment with Entity],
    extensions: Seq[KeyValue]) {
  val `type`: String          = observable.`type`
  val tags: Seq[String]       = observable.tags
  val message: Option[String] = observable.message
  val tlp: Int                = observable.tlp
  val pap: Int                = observable.pap
  val ioc: Boolean            = observable.ioc
  val sighted: Boolean        = observable.sighted
}
@VertexEntity
case class Data(data: String)
