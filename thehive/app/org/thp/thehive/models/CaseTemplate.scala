package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}

import java.util.Date

@BuildEdgeEntity[CaseTemplate, Organisation]
case class CaseTemplateOrganisation()

@BuildEdgeEntity[CaseTemplate, CustomFieldValue]
case class CaseTemplateCustomFieldValue()

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
    tags: Seq[String],
    severity: Option[Int],
    flag: Boolean,
    tlp: Option[Int],
    pap: Option[Int],
    summary: Option[String]
)

case class RichCaseTemplate(
    caseTemplate: CaseTemplate with Entity,
    organisation: String,
    tasks: Seq[RichTask],
    customFields: Seq[RichCustomField]
) {
  def _id: EntityId               = caseTemplate._id
  def _createdBy: String          = caseTemplate._createdBy
  def _updatedBy: Option[String]  = caseTemplate._updatedBy
  def _createdAt: Date            = caseTemplate._createdAt
  def _updatedAt: Option[Date]    = caseTemplate._updatedAt
  def name: String                = caseTemplate.name
  def displayName: String         = caseTemplate.displayName
  def titlePrefix: Option[String] = caseTemplate.titlePrefix
  def description: Option[String] = caseTemplate.description
  def tags: Seq[String]           = caseTemplate.tags
  def severity: Option[Int]       = caseTemplate.severity
  def flag: Boolean               = caseTemplate.flag
  def tlp: Option[Int]            = caseTemplate.tlp
  def pap: Option[Int]            = caseTemplate.pap
  def summary: Option[String]     = caseTemplate.summary
}
