package org.thp.thehive.models

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph._
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}

@BuildEdgeEntity[Alert, CustomField]
case class AlertCustomField(
    order: Option[Int] = None,
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Double] = None,
    dateValue: Option[Date] = None
) extends CustomFieldValue[AlertCustomField] {
  override def order_=(value: Option[Int]): AlertCustomField            = copy(order = value)
  override def stringValue_=(value: Option[String]): AlertCustomField   = copy(stringValue = value)
  override def booleanValue_=(value: Option[Boolean]): AlertCustomField = copy(booleanValue = value)
  override def integerValue_=(value: Option[Int]): AlertCustomField     = copy(integerValue = value)
  override def floatValue_=(value: Option[Double]): AlertCustomField    = copy(floatValue = value)
  override def dateValue_=(value: Option[Date]): AlertCustomField       = copy(dateValue = value)
}

@BuildEdgeEntity[Alert, Observable]
case class AlertObservable()

@BuildEdgeEntity[Alert, Organisation]
case class AlertOrganisation()

@BuildEdgeEntity[Alert, Case]
case class AlertCase()

@BuildEdgeEntity[Alert, CaseTemplate]
case class AlertCaseTemplate()

@BuildEdgeEntity[Alert, Tag]
case class AlertTag()

@BuildVertexEntity
@DefineIndex(IndexType.basic, "type", "source", "sourceRef")
case class Alert(
    `type`: String,
    source: String,
    sourceRef: String,
    externalLink: Option[String],
    title: String,
    description: String,
    severity: Int,
    date: Date,
    lastSyncDate: Date,
    tlp: Int,
    pap: Int,
    read: Boolean,
    follow: Boolean
)

case class RichAlert(
    alert: Alert with Entity,
    organisation: String,
    tags: Seq[Tag with Entity],
    customFields: Seq[RichCustomField],
    caseId: Option[String],
    caseTemplate: Option[String],
    observableCount: Long
) {
  def _id: String                  = alert._id
  def _createdAt: Date             = alert._createdAt
  def _createdBy: String           = alert._createdBy
  def _updatedAt: Option[Date]     = alert._updatedAt
  def _updatedBy: Option[String]   = alert._updatedBy
  def `type`: String               = alert.`type`
  def source: String               = alert.source
  def sourceRef: String            = alert.sourceRef
  def externalLink: Option[String] = alert.externalLink
  def title: String                = alert.title
  def description: String          = alert.description
  def severity: Int                = alert.severity
  def date: Date                   = alert.date
  def lastSyncDate: Date           = alert.lastSyncDate
  def tlp: Int                     = alert.tlp
  def pap: Int                     = alert.pap
  def read: Boolean                = alert.read
  def follow: Boolean              = alert.follow
}

object RichAlert {

  def apply(
      alert: Alert with Entity,
      organisation: String,
      tags: Seq[Tag with Entity],
      customFields: Seq[RichCustomField],
      caseId: Option[String],
      caseTemplate: Option[String],
      observableCount: Long
  ): RichAlert =
    alert
      .asInstanceOf[Alert]
      .into[RichAlert]
      .withFieldConst(_.alert, alert)
      .withFieldConst(_.organisation, organisation)
      .withFieldConst(_.tags, tags)
      .withFieldConst(_.customFields, customFields)
      .withFieldConst(_.caseId, caseId)
      .withFieldConst(_.caseTemplate, caseTemplate)
      .withFieldConst(_.observableCount, observableCount)
      .transform
}
