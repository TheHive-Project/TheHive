package org.thp.thehive.controllers

import org.thp.scalligraph.EntityId
import org.thp.scalligraph.query.PublicProperty
import org.thp.scalligraph.utils.Hash
import org.thp.thehive.services.{EntityDescription, PropertyDescription}
import play.api.Logger
import play.api.libs.json.JsValue

import java.lang.{Boolean => JBoolean}
import java.util.Date

trait ModelDescription { self =>
  val logger: Logger
  def entityDescriptions: Seq[EntityDescription]

  def customDescription(model: String, propertyName: String): Option[Seq[PropertyDescription]]

  final def propToDesc(model: String, prop: PublicProperty): Seq[PropertyDescription] =
    customDescription(model, prop.propertyName).getOrElse {
      prop.mapping.domainTypeClass match {
        case c if c == classOf[Boolean] || c == classOf[JBoolean] => Seq(PropertyDescription(prop.propertyName, "boolean"))
        case c if c == classOf[Date]                              => Seq(PropertyDescription(prop.propertyName, "date"))
        case c if c == classOf[Hash]                              => Seq(PropertyDescription(prop.propertyName, "string"))
        case c if classOf[Number].isAssignableFrom(c)             => Seq(PropertyDescription(prop.propertyName, "number"))
        case c if c == classOf[String]                            => Seq(PropertyDescription(prop.propertyName, "string"))
        case c if c == classOf[EntityId]                          => Seq(PropertyDescription(prop.propertyName, "string"))
        case c if c == classOf[JsValue]                           => Seq(PropertyDescription(prop.propertyName, "string"))
        case _ =>
          logger.warn(s"Unrecognized property $prop. Add a custom description")
          Seq(PropertyDescription(prop.propertyName, "unknown"))
      }
    }

  def ++(other: ModelDescription): ModelDescription =
    new ModelDescription {
      override val logger: Logger                             = self.logger
      override def entityDescriptions: Seq[EntityDescription] = self.entityDescriptions ++ other.entityDescriptions
      override def customDescription(model: String, propertyName: String): Option[Seq[PropertyDescription]] =
        self.customDescription(model, propertyName) orElse other.customDescription(model, propertyName)
    }
}

object ModelDescription {
  def empty: ModelDescription =
    new ModelDescription {
      override val logger: Logger                                                                           = Logger(getClass)
      override def entityDescriptions: Seq[EntityDescription]                                               = Nil
      override def customDescription(model: String, propertyName: String): Option[Seq[PropertyDescription]] = None
    }
}
