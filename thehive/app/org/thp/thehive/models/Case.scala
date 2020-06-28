package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph._
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType, Model}
import play.api.libs.json.{Format, Json}

object CaseStatus extends Enumeration {
  val Open, Resolved, Deleted, Duplicated = Value

  implicit val format: Format[CaseStatus.Value] = Json.formatEnum(CaseStatus)
}

@VertexEntity
case class ResolutionStatus(value: String) {
  require(!value.isEmpty, "ResolutionStatus can't be empty")
}

object ResolutionStatus {
  val indeterminate: ResolutionStatus = ResolutionStatus("Indeterminate")
  val falsePositive: ResolutionStatus = ResolutionStatus("FalsePositive")
  val truePositive: ResolutionStatus  = ResolutionStatus("TruePositive")
  val other: ResolutionStatus         = ResolutionStatus("Other")
  val duplicated: ResolutionStatus    = ResolutionStatus("Duplicated")

  val initialValues = Seq(indeterminate, falsePositive, truePositive, other, duplicated)
}

@EdgeEntity[Case, ResolutionStatus]
case class CaseResolutionStatus()

@VertexEntity
case class ImpactStatus(value: String) {
  require(!value.isEmpty, "ImpactStatus can't be empty")
}

object ImpactStatus {
  val noImpact: ImpactStatus           = ImpactStatus("NoImpact")
  val withImpact: ImpactStatus         = ImpactStatus("WithImpact")
  val notApplicable: ImpactStatus      = ImpactStatus("NotApplicable")
  val initialValues: Seq[ImpactStatus] = Seq(noImpact, withImpact, notApplicable)
}

@EdgeEntity[Case, ImpactStatus]
case class CaseImpactStatus()

@EdgeEntity[Case, Tag]
case class CaseTag()

@EdgeEntity[Case, Case]
case class MergedFrom()

@EdgeEntity[Case, CustomField]
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

@EdgeEntity[Case, User]
case class CaseUser()

@EdgeEntity[Case, CaseTemplate]
case class CaseCaseTemplate()

@VertexEntity
@DefineIndex(IndexType.unique, "number")
//@DefineIndex(IndexType.fulltext, "title")
//@DefineIndex(IndexType.fulltext, "description")
//@DefineIndex(IndexType.standard, "startDate")
@DefineIndex(IndexType.basic, "status")
case class Case(
    number: Int,
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date],
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: CaseStatus.Value,
    summary: Option[String]
)

case class RichCase(
    `case`: Case with Entity,
    tags: Seq[Tag with Entity],
    impactStatus: Option[String],
    resolutionStatus: Option[String],
    assignee: Option[String],
    customFields: Seq[RichCustomField],
    userPermissions: Set[Permission]
) {
  def _id: String                = `case`._id
  def _createdBy: String         = `case`._createdBy
  def _updatedBy: Option[String] = `case`._updatedBy
  def _createdAt: Date           = `case`._createdAt
  def _updatedAt: Option[Date]   = `case`._updatedAt
  def number: Int                = `case`.number
  def title: String              = `case`.title
  def description: String        = `case`.description
  def severity: Int              = `case`.severity
  def startDate: Date            = `case`.startDate
  def endDate: Option[Date]      = `case`.endDate
  def flag: Boolean              = `case`.flag
  def tlp: Int                   = `case`.tlp
  def pap: Int                   = `case`.pap
  def status: CaseStatus.Value   = `case`.status
  def summary: Option[String]    = `case`.summary
}

object RichCase {

  def apply(
      __id: String,
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
      tags: Seq[Tag with Entity],
      flag: Boolean,
      tlp: Int,
      pap: Int,
      status: CaseStatus.Value,
      summary: Option[String],
      impactStatus: Option[String],
      resolutionStatus: Option[String],
      user: Option[String],
      customFields: Seq[RichCustomField],
      userPermissions: Set[Permission]
  ): RichCase = {
    val `case`: Case with Entity = new Case(number, title, description, severity, startDate, endDate, flag, tlp, pap, status, summary) with Entity {
      override val _id: String                = __id
      override val _model: Model              = Model.vertex[Case]
      override val _createdBy: String         = __createdBy
      override val _updatedBy: Option[String] = __updatedBy
      override val _createdAt: Date           = __createdAt
      override val _updatedAt: Option[Date]   = __updatedAt
    }
    RichCase(`case`, tags, impactStatus, resolutionStatus, user, customFields, userPermissions)
  }
}
