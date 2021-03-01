package org.thp.thehive.models

import org.thp.scalligraph.BuildVertexEntity
import org.thp.scalligraph.models.{DefineIndex, IndexType}

@DefineIndex(IndexType.unique, "namespace", "predicate", "value")
@DefineIndex(IndexType.fulltext, "namespace", "predicate", "value", "description")
@BuildVertexEntity
case class Tag(
    namespace: String,
    predicate: String,
    value: Option[String],
    description: Option[String],
    colour: String
) {
  override def hashCode(): Int = 31 * (31 * value.## + predicate.##) + namespace.##

  override def equals(obj: Any): Boolean =
    obj match {
      case Tag(n, p, v, _, _) => n == namespace && p == predicate && v == value
      case _                  => false
    }

  lazy val isFreeTag: Boolean = namespace.startsWith("_freetags_")

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Tag]

  override def toString: String =
    (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + ':') +
      (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
      value.fold("")(v => f"""="$v"""") // #$colour%06X
}
