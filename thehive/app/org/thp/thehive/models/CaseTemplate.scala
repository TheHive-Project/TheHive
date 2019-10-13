package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[CaseTemplate, Organisation]
case class CaseTemplateOrganisation()

@EdgeEntity[CaseTemplate, CustomField]
case class CaseTemplateCustomField(
    order: Option[Int] = None,
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Float] = None,
    dateValue: Option[Date] = None
) extends CustomFieldValue[CaseTemplateCustomField] {
  override def order_=(value: Option[Int]): CaseTemplateCustomField            = copy(order = value)
  override def stringValue_=(value: Option[String]): CaseTemplateCustomField   = copy(stringValue = value)
  override def booleanValue_=(value: Option[Boolean]): CaseTemplateCustomField = copy(booleanValue = value)
  override def integerValue_=(value: Option[Int]): CaseTemplateCustomField     = copy(integerValue = value)
  override def floatValue_=(value: Option[Float]): CaseTemplateCustomField     = copy(floatValue = value)
  override def dateValue_=(value: Option[Date]): CaseTemplateCustomField       = copy(dateValue = value)
}

@EdgeEntity[CaseTemplate, Tag]
case class CaseTemplateTag()

@EdgeEntity[CaseTemplate, Task]
case class CaseTemplateTask()

@VertexEntity
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
    tasks: Seq[Task with Entity],
    customFields: Seq[RichCustomField]
) {
  val _id: String                 = caseTemplate._id
  val _createdBy: String          = caseTemplate._createdBy
  val _updatedBy: Option[String]  = caseTemplate._updatedBy
  val _createdAt: Date            = caseTemplate._createdAt
  val _updatedAt: Option[Date]    = caseTemplate._updatedAt
  val name: String                = caseTemplate.name
  val displayName: String         = caseTemplate.displayName
  val titlePrefix: Option[String] = caseTemplate.titlePrefix
  val description: Option[String] = caseTemplate.description
  val severity: Option[Int]       = caseTemplate.severity
  val flag: Boolean               = caseTemplate.flag
  val tlp: Option[Int]            = caseTemplate.tlp
  val pap: Option[Int]            = caseTemplate.pap
  val summary: Option[String]     = caseTemplate.summary
}
