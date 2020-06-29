package org.thp.thehive.models

import org.thp.scalligraph.models.{DefineIndex, IndexType}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[Observable, ObservableType]
case class ObservableObservableType()

@VertexEntity
@DefineIndex(IndexType.unique, "name")
case class ObservableType(name: String, isAttachment: Boolean)

object ObservableType {
  val initialValues: Seq[ObservableType] = Seq(
    ObservableType("url", isAttachment = false),
    ObservableType("other", isAttachment = false),
    ObservableType("user-agent", isAttachment = false),
    ObservableType("regexp", isAttachment = false),
    ObservableType("mail-subject", isAttachment = false),
    ObservableType("registry", isAttachment = false),
    ObservableType("mail", isAttachment = false),
    ObservableType("autonomous-system", isAttachment = false),
    ObservableType("domain", isAttachment = false),
    ObservableType("ip", isAttachment = false),
    ObservableType("uri_path", isAttachment = false),
    ObservableType("filename", isAttachment = false),
    ObservableType("hash", isAttachment = false),
    ObservableType("file", isAttachment = true),
    ObservableType("fqdn", isAttachment = false),
    ObservableType("hostname", isAttachment = false)
  )
}
