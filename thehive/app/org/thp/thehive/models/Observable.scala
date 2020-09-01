package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity}

@BuildEdgeEntity[Observable, KeyValue]
case class ObservableKeyValue()

@BuildEdgeEntity[Observable, Attachment]
case class ObservableAttachment()

@BuildEdgeEntity[Observable, Data]
case class ObservableData()

@BuildEdgeEntity[Observable, Tag]
case class ObservableTag()

@BuildVertexEntity
case class Observable(message: Option[String], tlp: Int, ioc: Boolean, sighted: Boolean)

case class RichObservable(
    observable: Observable with Entity,
    `type`: ObservableType with Entity,
    data: Option[Data with Entity],
    attachment: Option[Attachment with Entity],
    tags: Seq[Tag with Entity],
    seen: Option[Boolean],
    extensions: Seq[KeyValue with Entity],
    reportTags: Seq[ReportTag with Entity]
) {
  def _id: String                = observable._id
  def _createdBy: String         = observable._createdBy
  def _updatedBy: Option[String] = observable._updatedBy
  def _createdAt: Date           = observable._createdAt
  def _updatedAt: Option[Date]   = observable._updatedAt
  def message: Option[String]    = observable.message
  def tlp: Int                   = observable.tlp
  def ioc: Boolean               = observable.ioc
  def sighted: Boolean           = observable.sighted
}

@DefineIndex(IndexType.unique, "data")
@BuildVertexEntity
case class Data(data: String)
