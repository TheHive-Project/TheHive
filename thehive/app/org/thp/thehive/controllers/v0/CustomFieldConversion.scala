package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.{Entity, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputCustomField, InputCustomFieldValue, OutputCustomField, OutputCustomFieldValue}
import org.thp.thehive.models.{CustomField, CustomFieldString, CustomFieldWithValue}
import org.thp.thehive.services.CustomFieldSteps

object CustomFieldConversion {

  def fromInputCustomField(inputCustomFieldValue: InputCustomFieldValue): (String, Option[Any]) =
    inputCustomFieldValue.name -> inputCustomFieldValue.value

  implicit def toOutputCustomField(customFieldValue: CustomFieldWithValue): Output[OutputCustomFieldValue] =
    Output[OutputCustomFieldValue](
      customFieldValue
        .into[OutputCustomFieldValue]
        .withFieldComputed(_.value, _.value.map {
          case d: Date => d.getTime.toString
          case other   => other.toString
        })
        .withFieldComputed(_.tpe, _.typeName)
        .transform
    )

  implicit def fromInputCField(inputCustomField: InputCustomField): CustomField =
    inputCustomField
      .into[CustomField]
      .withFieldComputed(_.`type`, icf => CustomField.fromString(icf.`type`).getOrElse(CustomFieldString))
      .withFieldComputed(_.mandatory, _.mandatory.getOrElse(false))
      .withFieldComputed(_.name, _.reference)
      .withFieldComputed(_.displayName, _.name)
      .transform

  implicit def toOutputCustomField(customField: CustomField with Entity): Output[OutputCustomField] =
    Output[OutputCustomField](
      customField
        .asInstanceOf[CustomField]
        .into[OutputCustomField]
        .withFieldComputed(_.`type`, _.`type`.name)
        .withFieldComputed(_.reference, _.name)
        .withFieldComputed(_.name, _.displayName)
        .withFieldConst(_.id, customField._id)
        .transform
    )

  def customFieldProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CustomFieldSteps]
      .property("name", UniMapping.string)(_.simple.readonly)
      .property("description", UniMapping.string)(_.simple.updatable)
      .property("reference", UniMapping.string)(_.simple.readonly)
      .property("mandatory", UniMapping.boolean)(_.simple.updatable)
      .property("type", UniMapping.string)(_.simple.readonly)
      .property("options", UniMapping.json.sequence)(_.simple.updatable)
      .build
}
