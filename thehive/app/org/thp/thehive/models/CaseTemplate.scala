package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[CaseTemplate, Organisation]
case class CaseTemplateOrganisation()

@EdgeEntity[CaseTemplate, CustomField]
case class CaseTemplateCustomField(
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Float] = None,
    dateValue: Option[Date] = None
) extends CustomFieldValue[CaseTemplateCustomField] {
  override def setStringValue(value: String): CaseTemplateCustomField   = copy(stringValue = Some(value))
  override def setBooleanValue(value: Boolean): CaseTemplateCustomField = copy(booleanValue = Some(value))
  override def setIntegerValue(value: Int): CaseTemplateCustomField     = copy(integerValue = Some(value))
  override def setFloatValue(value: Float): CaseTemplateCustomField     = copy(floatValue = Some(value))
  override def setDateValue(value: Date): CaseTemplateCustomField       = copy(dateValue = Some(value))
}

@EdgeEntity[CaseTemplate, Task]
case class CaseTemplateTask()

@VertexEntity
@DefineIndex(IndexType.unique, "name")
case class CaseTemplate(
    name: String,
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int],
    tags: Set[String],
    flag: Boolean,
    tlp: Option[Int],
    pap: Option[Int],
    summary: Option[String]
)

case class RichCaseTemplate(
    caseTemplate: CaseTemplate with Entity,
    organisation: String,
    tasks: Seq[Task with Entity],
    customFields: Seq[CustomFieldWithValue]
) {
  val _id: String                 = caseTemplate._id
  val _createdBy: String          = caseTemplate._createdBy
  val _updatedBy: Option[String]  = caseTemplate._updatedBy
  val _createdAt: Date            = caseTemplate._createdAt
  val _updatedAt: Option[Date]    = caseTemplate._updatedAt
  val name: String                = caseTemplate.name
  val titlePrefix: Option[String] = caseTemplate.titlePrefix
  val description: Option[String] = caseTemplate.description
  val severity: Option[Int]       = caseTemplate.severity
  val tags: Set[String]           = caseTemplate.tags
  val flag: Boolean               = caseTemplate.flag
  val tlp: Option[Int]            = caseTemplate.tlp
  val pap: Option[Int]            = caseTemplate.pap
  val summary: Option[String]     = caseTemplate.summary
}
