package org.thp.thehive.models

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}

import java.util.Date

@BuildEdgeEntity[Observable, KeyValue]
case class ObservableKeyValue()

@BuildEdgeEntity[Observable, Attachment]
case class ObservableAttachment()

@BuildEdgeEntity[Observable, Data]
case class ObservableData()

@BuildEdgeEntity[Observable, Tag]
case class ObservableTag()

@DefineIndex(IndexType.fulltext, "message")
@DefineIndex(IndexType.standard, "tlp")
@DefineIndex(IndexType.standard, "ioc")
@DefineIndex(IndexType.standard, "sighted")
@DefineIndex(IndexType.standard, "ignoreSimilarity")
@DefineIndex(IndexType.standard, "dataType")
@DefineIndex(IndexType.standard, "tags")
@DefineIndex(IndexType.standard, "data")
@DefineIndex(IndexType.standard, "attachmentId")
@DefineIndex(IndexType.standard, "relatedId")
@DefineIndex(IndexType.standard, "organisationIds")
@BuildVertexEntity
case class Observable(
    message: Option[String],
    tlp: Int,
    ioc: Boolean,
    sighted: Boolean,
    ignoreSimilarity: Option[Boolean],
    dataType: String,
    tags: Seq[String],
    /* filled by the service */
    data: Option[String] = None,
    attachmentId: Option[String] = None,
    relatedId: EntityId = EntityId(""),
    organisationIds: Seq[EntityId] = Nil
)

case class RichObservable(
    observable: Observable with Entity,
    attachment: Option[Attachment with Entity],
    seen: Option[Boolean],
    reportTags: Seq[ReportTag with Entity]
) {
  def _id: EntityId                                            = observable._id
  def _createdBy: String                                       = observable._createdBy
  def _updatedBy: Option[String]                               = observable._updatedBy
  def _createdAt: Date                                         = observable._createdAt
  def _updatedAt: Option[Date]                                 = observable._updatedAt
  def message: Option[String]                                  = observable.message
  def tlp: Int                                                 = observable.tlp
  def ioc: Boolean                                             = observable.ioc
  def sighted: Boolean                                         = observable.sighted
  def ignoreSimilarity: Option[Boolean]                        = observable.ignoreSimilarity
  def dataOrAttachment: Either[String, Attachment with Entity] = observable.data.toLeft(attachment.get)
  def dataType: String                                         = observable.dataType
  def data: Option[String]                                     = observable.data
  def tags: Seq[String]                                        = observable.tags
}

@DefineIndex(IndexType.unique, "data")
@BuildVertexEntity
case class Data(data: String)
