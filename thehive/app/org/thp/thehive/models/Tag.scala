package org.thp.thehive.models

import org.thp.scalligraph.BuildVertexEntity
import org.thp.scalligraph.models.{DefineIndex, IndexType}
import play.api.Logger

import scala.util.matching.Regex

@DefineIndex(IndexType.unique, "namespace", "predicate", "value")
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

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Tag]

  override def toString: String =
    (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + ':') +
      (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
      value.fold("")(v => f"""="$v"""") // #$colour%06X
}

object Tag {
  lazy val logger: Logger            = Logger(getClass)
  val tagColour: Regex               = "(.*)(#\\p{XDigit}{6})".r
  val namespacePredicateValue: Regex = "([^\".:=]+)[.:]([^\".=]+)=\"?([^\"]+)\"?".r
  val namespacePredicate: Regex      = "([^\".:=]+)[.:]([^\".=]+)".r
  val PredicateValue: Regex          = "([^\".:=]+)[=:]\"?([^\"]+)\"?".r
  val predicate: Regex               = "([^\".:=]+)".r

  def fromString(tagName: String, defaultNamespace: String, defaultColour: String = "#000000"): Tag = {
    val (name, colour) = tagName match {
      case tagColour(n, c) => n       -> c
      case _               => tagName -> defaultColour
    }
    name match {
      case namespacePredicateValue(namespace, predicate, value) if value.exists(_ != '=') =>
        Tag(namespace.trim, predicate.trim, Some(value.trim), None, colour)
      case namespacePredicate(namespace, predicate) => Tag(namespace.trim, predicate.trim, None, None, colour)
      case PredicateValue(predicate, value)         => Tag(defaultNamespace, predicate.trim, Some(value.trim), None, colour)
      case predicate(predicate)                     => Tag(defaultNamespace, predicate.trim, None, None, colour)
      case _                                        => Tag(defaultNamespace, name, None, None, colour)
    }
  }
}
