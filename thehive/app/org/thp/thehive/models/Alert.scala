package org.thp.thehive.models

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph._
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}

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

@EdgeEntity[Alert, Case]
case class AlertCase()

@EdgeEntity[Alert, CaseTemplate]
case class AlertCaseTemplate()

@VertexEntity
@DefineIndex(IndexType.unique, "type", "source", "sourceRef")
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
    read: Boolean,
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
      .withFieldConst(_.alert, alert)
      .withFieldConst(_.user, user)
      .withFieldConst(_.organisation, organisation)
      .withFieldConst(_.customFields, customFields)
      .transform
}
