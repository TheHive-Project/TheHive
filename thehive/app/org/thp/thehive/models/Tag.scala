package org.thp.thehive.models

import scala.util.Try
import scala.util.matching.Regex

import play.api.Logger

import org.thp.scalligraph.VertexEntity

@VertexEntity
case class Tag(
    namespace: String,
    predicate: String,
    value: Option[String],
    description: Option[String],
    colour: Int
) {
  override def hashCode(): Int = 31 * (31 * value.## + predicate.##) + namespace.##

  override def equals(obj: Any): Boolean = obj match {
    case Tag(n, p, v, _, _) => n == namespace && p == predicate && v == value
    case _                  => false
  }

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Tag]

  override def toString: String =
    (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + '.') +
      (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
      value.fold("")(v => f"""="$v"""") // #$colour%06X
}

object Tag {
  lazy val logger: Logger                  = Logger(getClass)
  val namespacePredicateValueColour: Regex = "([^\".:=]+)[.:]([\".=]+)=\"([^\"]+)\"#(\\p{XDigit}{6})".r
  val namespacePredicateValue: Regex       = "([^\".:=]+)[.:]([^\".=]+)=\"?([^\"]+)\"?".r
  val namespacePredicate: Regex            = "([^\".:=]+)[.:]([^\".=]+)".r
  val PredicateValue: Regex                = "([^\".:=]+)=\"([^\"]+)\"".r
  val predicate: Regex                     = "([^\".:=]+)".r

  def fromString(tagName: String, defaultNamespace: String, defaultColour: Int = 0): Tag = {
    val (name, colour) = tagName.split('#') match {
      case Array(n, c) => n -> Try(Integer.parseUnsignedInt(c, 16)).getOrElse(defaultColour)
      case Array(n)    => n -> defaultColour
    }
    name match {
      case namespacePredicateValue(namespace, predicate, value) => Tag(namespace, predicate, Some(value), None, colour)
      case namespacePredicate(namespace, predicate)             => Tag(namespace, predicate, None, None, colour)
      case PredicateValue(predicate, value)                     => Tag(defaultNamespace, predicate, Some(value), None, colour)
      case predicate(predicate)                                 => Tag(defaultNamespace, predicate, None, None, colour)
      case _ =>
        logger.error(s"Invalid tag format: $name")
        Tag(defaultNamespace, name, None, None, colour)
    }
  }
}
