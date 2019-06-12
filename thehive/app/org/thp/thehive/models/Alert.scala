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
    dateValue: Option[Date] = None
) extends CustomFieldValue[AlertCustomField] {
  override def setStringValue(value: String): AlertCustomField   = copy(stringValue = Some(value))
  override def setBooleanValue(value: Boolean): AlertCustomField = copy(booleanValue = Some(value))
  override def setIntegerValue(value: Int): AlertCustomField     = copy(integerValue = Some(value))
  override def setFloatValue(value: Float): AlertCustomField     = copy(floatValue = Some(value))
  override def setDateValue(value: Date): AlertCustomField       = copy(dateValue = Some(value))
}

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
    tags: Set[String],
    flag: Boolean,
    tlp: Int,
    pap: Int,
    read: Boolean,
    follow: Boolean
)

case class RichAlert(
    alert: Alert with Entity,
    organisation: String,
    customFields: Seq[CustomFieldWithValue],
    caseId: Option[String],
    caseTemplate: Option[String]
) {
  val _id: String                = alert._id
  val _createdAt: Date           = alert._createdAt
  val _createdBy: String         = alert._createdBy
  val _updatedAt: Option[Date]   = alert._updatedAt
  val _updatedBy: Option[String] = alert._updatedBy
  val `type`: String             = alert.`type`
  val source: String             = alert.source
  val sourceRef: String          = alert.sourceRef
  val title: String              = alert.title
  val description: String        = alert.description
  val severity: Int              = alert.severity
  val date: Date                 = alert.date
  val lastSyncDate: Date         = alert.lastSyncDate
  val tags: Set[String]          = alert.tags
  val flag: Boolean              = alert.flag
  val tlp: Int                   = alert.tlp
  val pap: Int                   = alert.pap
  val read: Boolean              = alert.read
  val follow: Boolean            = alert.follow
}

object RichAlert {

  def apply(
      alert: Alert with Entity,
      organisation: String,
      customFields: Seq[CustomFieldWithValue],
      caseId: Option[String],
      caseTemplate: Option[String]
  ): RichAlert =
    alert
      .asInstanceOf[Alert]
      .into[RichAlert]
      .withFieldConst(_.alert, alert)
      .withFieldConst(_.organisation, organisation)
      .withFieldConst(_.customFields, customFields)
      .withFieldConst(_.caseId, caseId)
      .withFieldConst(_.caseTemplate, caseTemplate)
      .transform
}
