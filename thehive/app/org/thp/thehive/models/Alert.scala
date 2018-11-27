package org.thp.thehive.models

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph._
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}

object AlertStatus extends Enumeration {
  val `new`, updated, ignored, imported = Value
}

@EdgeEntity[Alert, CustomField]
case class AlertCustomField(
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Float] = None,
    dateValue: Option[Date] = None)
    extends CustomFieldValue[AlertCustomField] {
  override def setStringValue(value: String): AlertCustomField   = copy(stringValue = Some(value))
  override def setBooleanValue(value: Boolean): AlertCustomField = copy(booleanValue = Some(value))
  override def setIntegerValue(value: Int): AlertCustomField     = copy(integerValue = Some(value))
  override def setFloatValue(value: Float): AlertCustomField     = copy(floatValue = Some(value))
  override def setDateValue(value: Date): AlertCustomField       = copy(dateValue = Some(value))
}

@EdgeEntity[Alert, User]
case class AlertUser()

@EdgeEntity[Alert, Observable]
case class AlertObservable()

@EdgeEntity[Alert, Organisation]
case class AlertOrganisation()

@VertexEntity
@DefineIndex(IndexType.unique, "number")
case class Alert(
    `type`: String,
    source: String,
    sourceRef: String,
    title: String,
    description: String,
    severity: Int,
    date: Date,
    lastSyncDate: Date,
    tags: Seq[String],
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: AlertStatus.Value,
    follow: Boolean)

case class RichAlert(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String],
    _createdAt: Date,
    _updatedAt: Option[Date],
    `type`: String,
    source: String,
    sourceRef: String,
    title: String,
    description: String,
    severity: Int,
    date: Date,
    lastSyncDate: Date,
    tags: Seq[String],
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    follow: Boolean,
    user: String,
    organisation: String,
    customFields: Seq[CustomFieldWithValue]
)

object RichAlert {
  def apply(alert: Alert with Entity, user: String, organisation: String, customFields: Seq[CustomFieldWithValue]): RichAlert =
    alert
      .asInstanceOf[Alert]
      .into[RichAlert]
      .withFieldConst(_._id, alert._id)
      .withFieldConst(_._createdAt, alert._createdAt)
      .withFieldConst(_._createdBy, alert._createdBy)
      .withFieldConst(_._updatedAt, alert._updatedAt)
      .withFieldConst(_._updatedBy, alert._updatedBy)
      .withFieldComputed(_.status, _.status.toString)
      .withFieldConst(_.organisation, organisation)
      .withFieldConst(_.user, user)
      .withFieldConst(_.customFields, customFields)
      .transform
}
