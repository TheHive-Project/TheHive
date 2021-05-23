package org.thp.thehive.models

import org.thp.scalligraph._
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}
import play.api.libs.json.{Format, Json}

import java.util.Date

object CaseStatus extends Enumeration {
  val Open, Resolved, Duplicated = Value

  implicit val format: Format[CaseStatus.Value] = Json.formatEnum(CaseStatus)
}

@BuildVertexEntity
@DefineIndex(IndexType.unique, "value")
case class ResolutionStatus(value: String) {
  require(value.nonEmpty, "ResolutionStatus can't be empty")
}

object ResolutionStatus {
  val indeterminate: ResolutionStatus = ResolutionStatus("Indeterminate")
  val falsePositive: ResolutionStatus = ResolutionStatus("FalsePositive")
  val truePositive: ResolutionStatus  = ResolutionStatus("TruePositive")
  val other: ResolutionStatus         = ResolutionStatus("Other")
  val duplicated: ResolutionStatus    = ResolutionStatus("Duplicated")

  val initialValues = Seq(indeterminate, falsePositive, truePositive, other, duplicated)
}

@BuildEdgeEntity[Case, ResolutionStatus]
case class CaseResolutionStatus()

@BuildVertexEntity
@DefineIndex(IndexType.unique, "value")
case class ImpactStatus(value: String) {
  require(value.nonEmpty, "ImpactStatus can't be empty")
}

object ImpactStatus {
  val noImpact: ImpactStatus           = ImpactStatus("NoImpact")
  val withImpact: ImpactStatus         = ImpactStatus("WithImpact")
  val notApplicable: ImpactStatus      = ImpactStatus("NotApplicable")
  val initialValues: Seq[ImpactStatus] = Seq(noImpact, withImpact, notApplicable)
}

@BuildEdgeEntity[Case, ImpactStatus]
case class CaseImpactStatus()

@BuildEdgeEntity[Case, Tag]
case class CaseTag()

@BuildEdgeEntity[Case, Case]
case class MergedFrom()

@BuildEdgeEntity[Case, CustomField]
case class CaseCustomField(
    order: Option[Int] = None,
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Double] = None,
    dateValue: Option[Date] = None
) extends CustomFieldValue[CaseCustomField] {
  override def order_=(value: Option[Int]): CaseCustomField            = copy(order = value)
  override def stringValue_=(value: Option[String]): CaseCustomField   = copy(stringValue = value)
  override def booleanValue_=(value: Option[Boolean]): CaseCustomField = copy(booleanValue = value)
  override def integerValue_=(value: Option[Int]): CaseCustomField     = copy(integerValue = value)
  override def floatValue_=(value: Option[Double]): CaseCustomField    = copy(floatValue = value)
  override def dateValue_=(value: Option[Date]): CaseCustomField       = copy(dateValue = value)
}

@BuildEdgeEntity[Case, User]
case class CaseUser()

@BuildEdgeEntity[Case, CaseTemplate]
case class CaseCaseTemplate()

@BuildEdgeEntity[Case, Procedure]
case class CaseProcedure()

@BuildVertexEntity
@DefineIndex(IndexType.unique, "number")
@DefineIndex(IndexType.fulltext, "title")
@DefineIndex(IndexType.fulltextOnly, "description")
@DefineIndex(IndexType.standard, "severity")
@DefineIndex(IndexType.standard, "startDate")
@DefineIndex(IndexType.standard, "endDate")
@DefineIndex(IndexType.standard, "flag")
@DefineIndex(IndexType.standard, "tlp")
@DefineIndex(IndexType.standard, "pap")
@DefineIndex(IndexType.standard, "status")
@DefineIndex(IndexType.fulltextOnly, "summary")
@DefineIndex(IndexType.standard, "tags")
@DefineIndex(IndexType.standard, "assignee")
@DefineIndex(IndexType.standard, "organisationIds")
@DefineIndex(IndexType.standard, "impactStatus")
@DefineIndex(IndexType.standard, "resolutionStatus")
@DefineIndex(IndexType.standard, "caseTemplate")
case class Case(
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date],
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: CaseStatus.Value,
    summary: Option[String],
    tags: Seq[String],
    impactStatus: Option[String] = None,
    resolutionStatus: Option[String] = None,
    /* filled by the service */
    assignee: Option[String] = None,
    number: Int = 0,
    organisationIds: Set[EntityId] = Set.empty,
    caseTemplate: Option[String] = None,
    owningOrganisation: EntityId = EntityId.empty
)

case class RichCase(
    `case`: Case with Entity,
    customFields: Seq[RichCustomField],
    userPermissions: Set[Permission]
) {
  def _id: EntityId                    = `case`._id
  def _createdBy: String               = `case`._createdBy
  def _updatedBy: Option[String]       = `case`._updatedBy
  def _createdAt: Date                 = `case`._createdAt
  def _updatedAt: Option[Date]         = `case`._updatedAt
  def number: Int                      = `case`.number
  def title: String                    = `case`.title
  def description: String              = `case`.description
  def severity: Int                    = `case`.severity
  def startDate: Date                  = `case`.startDate
  def endDate: Option[Date]            = `case`.endDate
  def flag: Boolean                    = `case`.flag
  def tlp: Int                         = `case`.tlp
  def pap: Int                         = `case`.pap
  def status: CaseStatus.Value         = `case`.status
  def summary: Option[String]          = `case`.summary
  def tags: Seq[String]                = `case`.tags
  def assignee: Option[String]         = `case`.assignee
  def impactStatus: Option[String]     = `case`.impactStatus
  def resolutionStatus: Option[String] = `case`.resolutionStatus
  def caseTemplate: Option[String]     = `case`.caseTemplate
}

object RichCase {

  def apply(
      __id: EntityId,
      __createdBy: String,
      __updatedBy: Option[String],
      __createdAt: Date,
      __updatedAt: Option[Date],
      number: Int,
      title: String,
      description: String,
      severity: Int,
      startDate: Date,
      endDate: Option[Date],
      tags: Seq[String],
      flag: Boolean,
      tlp: Int,
      pap: Int,
      status: CaseStatus.Value,
      summary: Option[String],
      impactStatus: Option[String],
      resolutionStatus: Option[String],
      assignee: Option[String],
      customFields: Seq[RichCustomField],
      userPermissions: Set[Permission],
      organisationIds: Set[EntityId],
      caseTemplate: Option[String] = None,
      owningOrganisation: EntityId = EntityId.empty
  ): RichCase = {
    val `case`: Case with Entity =
      new Case(
        number = number,
        title = title,
        description = description,
        severity = severity,
        startDate = startDate,
        endDate = endDate,
        flag = flag,
        tlp = tlp,
        pap = pap,
        status = status,
        summary = summary,
        organisationIds = organisationIds,
        tags = tags,
        assignee = assignee,
        impactStatus = impactStatus,
        resolutionStatus = resolutionStatus,
        caseTemplate = caseTemplate,
        owningOrganisation = owningOrganisation
      ) with Entity {
        override val _id: EntityId              = __id
        override val _label: String             = "Case"
        override val _createdBy: String         = __createdBy
        override val _updatedBy: Option[String] = __updatedBy
        override val _createdAt: Date           = __createdAt
        override val _updatedAt: Option[Date]   = __updatedAt
      }
    RichCase(`case`, customFields, userPermissions)
  }
}

case class SimilarStats(observable: (Int, Int), ioc: (Int, Int), types: Map[String, Long])
