package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.{Entity, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputCustomField, InputCustomFieldValue, OutputCustomField, OutputCustomFieldValue}
import org.thp.thehive.models.{CustomField, CustomFieldType, CustomFieldValue, RichCustomField}
import org.thp.thehive.services.CustomFieldSteps

object CustomFieldConversion {

  def fromInputCustomField(inputCustomFieldValue: InputCustomFieldValue): (String, Option[Any]) =
    inputCustomFieldValue.name -> inputCustomFieldValue.value

  implicit def toOutputCustomField(customFieldValue: RichCustomField): Output[OutputCustomFieldValue] =
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
      .withFieldComputed(_.`type`, icf => CustomFieldType.withName(icf.`type`))
      .withFieldComputed(_.mandatory, _.mandatory.getOrElse(false))
      .withFieldComputed(_.name, _.reference)
      .withFieldComputed(_.displayName, _.name)
      .transform

  implicit def toOutputCustomField(customField: CustomField with Entity): Output[OutputCustomField] =
    Output[OutputCustomField](
      customField
        .asInstanceOf[CustomField]
        .into[OutputCustomField]
        .withFieldComputed(_.`type`, _.`type`.toString)
        .withFieldComputed(_.reference, _.name)
        .withFieldComputed(_.name, _.displayName)
        .withFieldConst(_.id, customField._id)
        .transform
    )

  def customFieldProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CustomFieldSteps]
      .property("name", UniMapping.string)(_.field.readonly)
      .property("description", UniMapping.string)(_.field.updatable)
      .property("reference", UniMapping.string)(_.field.readonly)
      .property("mandatory", UniMapping.boolean)(_.field.updatable)
      .property("type", UniMapping.string)(_.field.readonly)
      .property("options", UniMapping.json.sequence)(_.field.updatable)
      .build

  def toOutputValue(value: CustomFieldValue[_]) = {}
}
