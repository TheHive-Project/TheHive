package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity}

@BuildEdgeEntity[CaseTemplate, Organisation]
case class CaseTemplateOrganisation()

@BuildEdgeEntity[CaseTemplate, CustomField]
case class CaseTemplateCustomField(
    order: Option[Int] = None,
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Double] = None,
    dateValue: Option[Date] = None
) extends CustomFieldValue[CaseTemplateCustomField] {
  override def order_=(value: Option[Int]): CaseTemplateCustomField            = copy(order = value)
  override def stringValue_=(value: Option[String]): CaseTemplateCustomField   = copy(stringValue = value)
  override def booleanValue_=(value: Option[Boolean]): CaseTemplateCustomField = copy(booleanValue = value)
  override def integerValue_=(value: Option[Int]): CaseTemplateCustomField     = copy(integerValue = value)
  override def floatValue_=(value: Option[Double]): CaseTemplateCustomField    = copy(floatValue = value)
  override def dateValue_=(value: Option[Date]): CaseTemplateCustomField       = copy(dateValue = value)
}

@BuildEdgeEntity[CaseTemplate, Tag]
case class CaseTemplateTag()

@BuildEdgeEntity[CaseTemplate, Task]
case class CaseTemplateTask()

@BuildVertexEntity
case class CaseTemplate(
    name: String,
    displayName: String,
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int],
    flag: Boolean,
    tlp: Option[Int],
    pap: Option[Int],
    summary: Option[String]
)

case class RichCaseTemplate(
    caseTemplate: CaseTemplate with Entity,
    organisation: String,
    tags: Seq[Tag with Entity],
    tasks: Seq[RichTask],
    customFields: Seq[RichCustomField]
) {
  def _id: String                 = caseTemplate._id
  def _createdBy: String          = caseTemplate._createdBy
  def _updatedBy: Option[String]  = caseTemplate._updatedBy
  def _createdAt: Date            = caseTemplate._createdAt
  def _updatedAt: Option[Date]    = caseTemplate._updatedAt
  def name: String                = caseTemplate.name
  def displayName: String         = caseTemplate.displayName
  def titlePrefix: Option[String] = caseTemplate.titlePrefix
  def description: Option[String] = caseTemplate.description
  def severity: Option[Int]       = caseTemplate.severity
  def flag: Boolean               = caseTemplate.flag
  def tlp: Option[Int]            = caseTemplate.tlp
  def pap: Option[Int]            = caseTemplate.pap
  def summary: Option[String]     = caseTemplate.summary
}
