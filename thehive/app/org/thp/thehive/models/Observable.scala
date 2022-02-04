package org.thp.thehive.models

import org.apache.tinkerpop.gremlin.structure.{Vertex, VertexProperty}
import org.thp.scalligraph.janus.{ImmenseStringTermFilter, ImmenseTermProcessor}
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType, UMapping}
import org.thp.scalligraph.utils.Hasher
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

@DefineIndex(IndexType.fulltextOnly, "message")
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
    relatedId: EntityId = EntityId.empty,
    organisationIds: Set[EntityId] = Set.empty
)

case class RichObservable(
    observable: Observable with Entity,
    fullData: Option[Data with Entity],
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
  def dataOrAttachment: Either[String, Attachment with Entity] = data.toLeft(attachment.get)
  def dataType: String                                         = observable.dataType
  def data: Option[String]                                     = fullData.map(d => d.fullData.getOrElse(d.data)).orElse(observable.data)
  def tags: Seq[String]                                        = observable.tags
}

@DefineIndex(IndexType.unique, "data")
@BuildVertexEntity
case class Data(data: String, fullData: Option[String])

object UseHashToIndex extends ImmenseTermProcessor with ImmenseStringTermFilter {
  override val termSizeLimit: Int = 8191
  private val hasher: Hasher      = Hasher("SHA-256")

  def hashToIndex(value: String): Option[String] =
    if (value.length > termSizeLimit) Some("sha256/" + hasher.fromString(value).head.toString)
    else None

  override def apply[V](vertex: Vertex, property: VertexProperty[V]): Boolean = {
    if (property.key() == "data")
      vertex.label() match {
        case "Observable" =>
          collect(vertex, property).foreach { strProp =>
            val currentValue = strProp.value()
            logger.info(s"""Use hash for observable ~${vertex.id()}:
                           |  dataType=${UMapping.string.getProperty(vertex, "dataType")}
                           |  data=$currentValue
                           |  message=${UMapping.string.optional.getProperty(vertex, "message").getOrElse("<not set>")}
                           |  tags=${UMapping.string.sequence.getProperty(vertex, "message").mkString(", ")}""".stripMargin)
            strProp.remove()
            vertex.property(strProp.key(), "sha256/" + hasher.fromString(currentValue).head.toString)
          }

        case "Data" =>
          collect(vertex, property).foreach { strProp =>
            val currentValue = strProp.value()
            logger.info(s"Use hash and move data for $vertex/${strProp.key()}: $currentValue")
            strProp.remove()
            vertex.property(strProp.key(), hasher.fromString(currentValue).head.toString)
            vertex.property("fullData", currentValue)
          }

        case _ =>
      }
    false
  }
}
