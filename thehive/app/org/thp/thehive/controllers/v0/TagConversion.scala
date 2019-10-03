package org.thp.thehive.controllers.v0

import scala.language.implicitConversions
import scala.util.Try

import play.api.libs.json.{JsObject, JsValue}

import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.{Entity, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputTag, OutputTag}
import org.thp.thehive.models.Tag
import org.thp.thehive.services.TagSteps

object TagConversion {
  implicit def fromInputTag(inputTag: InputTag): Tag = ??? // FIXME inputTag.into[Tag].transform

  implicit def toOutputTag(tag: Tag with Entity): Output[OutputTag] = ??? // Output(tag.into[OutputTag].transform)

  val tagProperties: List[PublicProperty[_, _]] = PublicPropertyListBuilder[TagSteps]
    .property("namespace", UniMapping.string)(_.simple.readonly)
    .property("predicate", UniMapping.string)(_.simple.readonly)
    .property("value", UniMapping.string.optional)(_.simple.readonly)
    .property("description", UniMapping.string.optional)(_.simple.readonly)
    .build

  def parseColour(colour: String): Int = if (colour(0) == '#') Try(Integer.parseUnsignedInt(colour.tail, 16)).getOrElse(0) else 0

  def parseValues(namespace: String, values: Seq[JsObject]): Seq[Tag] =
    for {
      value     <- values
      predicate <- (value \ "predicate").asOpt[String].toList
      entry     <- (value \ "entry").asOpt[Seq[JsObject]].getOrElse(Nil)
      v         <- (entry \ "value").asOpt[String]
      colour = (entry \ "colour")
        .asOpt[String]
        .map(parseColour)
        .getOrElse(0) // black
      e = (entry \ "description").asOpt[String] orElse (entry \ "expanded").asOpt[String]
    } yield Tag(namespace, predicate, Some(v), e, colour)

  def parsePredicates(namespace: String, predicates: Seq[JsObject]): Seq[Tag] =
    for {
      predicate <- predicates
      v         <- (predicate \ "value").asOpt[String]
      e = (predicate \ "expanded").asOpt[String]
      colour = (predicate \ "colour")
        .asOpt[String]
        .map(parseColour)
        .getOrElse(0) // black
    } yield Tag(namespace, v, None, e, colour)

  def parseTaxonomy(taxonomy: JsValue): Seq[Tag] =
    (taxonomy \ "namespace").asOpt[String].fold(Seq.empty[Tag]) { namespace =>
      (taxonomy \ "values").asOpt[Seq[JsObject]].filter(_.nonEmpty) match {
        case Some(values) => parseValues(namespace, values)
        case _            => (taxonomy \ "predicates").asOpt[Seq[JsObject]].fold(Seq.empty[Tag])(parsePredicates(namespace, _))
      }
    }

}
