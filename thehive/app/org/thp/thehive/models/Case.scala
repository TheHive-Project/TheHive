package org.thp.thehive.models

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph._
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType, Model}
import play.api.libs.json.{Format, Json, OFormat}

object CaseStatus extends Enumeration {
  type Type = Value

  val Open, Resolved, Deleted, Duplicated = Value

  implicit val format: Format[CaseStatus.Type] = Json.formatEnum(CaseStatus)
}

@VertexEntity
case class ResolutionStatus(value: String) {
  require(!value.isEmpty, "ResolutionStatus can't be empty")
}

@EdgeEntity[Case, ResolutionStatus]
case class CaseResolutionStatus()

@VertexEntity
case class ImpactStatus(value: String) {
  require(!value.isEmpty, "ImpactStatus can't be empty")
}

@EdgeEntity[Case, ImpactStatus]
case class CaseImpactStatus()

@EdgeEntity[Case, Case]
case class MergedFrom()

@EdgeEntity[Case, CustomField]
case class CaseCustomField(
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Float] = None,
    dateValue: Option[Date] = None
) extends CustomFieldValue[CaseCustomField] {
  override def setStringValue(value: String): CaseCustomField   = copy(stringValue = Some(value))
  override def setBooleanValue(value: Boolean): CaseCustomField = copy(booleanValue = Some(value))
  override def setIntegerValue(value: Int): CaseCustomField     = copy(integerValue = Some(value))
  override def setFloatValue(value: Float): CaseCustomField     = copy(floatValue = Some(value))
  override def setDateValue(value: Date): CaseCustomField       = copy(dateValue = Some(value))
}

@EdgeEntity[Case, User]
case class CaseUser()

@EdgeEntity[Case, CaseTemplate]
case class CaseCaseTemplate()

@VertexEntity
@DefineIndex(IndexType.unique, "number")
case class Case(
    number: Int,
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date],
    tags: Set[String],
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: CaseStatus.Value,
    summary: Option[String]
)

object Case {
  implicit val format: OFormat[Case] = Json.format[Case]
}

case class RichCase(
    `case`: Case with Entity,
    impactStatus: Option[String],
    resolutionStatus: Option[String],
    user: Option[String],
    customFields: Seq[CustomFieldWithValue]
) {
  val _id: String                = `case`._id
  val _createdBy: String         = `case`._createdBy
  val _updatedBy: Option[String] = `case`._updatedBy
  val _createdAt: Date           = `case`._createdAt
  val _updatedAt: Option[Date]   = `case`._updatedAt
  val number: Int                = `case`.number
  val title: String              = `case`.title
  val description: String        = `case`.description
  val severity: Int              = `case`.severity
  val startDate: Date            = `case`.startDate
  val endDate: Option[Date]      = `case`.endDate
  val tags: Set[String]          = `case`.tags
  val flag: Boolean              = `case`.flag
  val tlp: Int                   = `case`.tlp
  val pap: Int                   = `case`.pap
  val status: CaseStatus.Value   = `case`.status
  val summary: Option[String]    = `case`.summary
}

object RichCase {

  def apply(
      `case`: Case with Entity,
      caseImpactStatus: Option[String],
      resolutionStatus: Option[String],
      user: Option[String],
      customFields: Seq[CustomFieldWithValue]
  ): RichCase =
    `case`
      .asInstanceOf[Case]
      .into[RichCase]
      .withFieldConst(_.`case`, `case`)
      .withFieldConst(_.impactStatus, caseImpactStatus)
      .withFieldConst(_.resolutionStatus, resolutionStatus)
      .withFieldConst(_.user, user)
      .withFieldConst(_.customFields, customFields)
      .transform

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
      tags: Set[String],
      flag: Boolean,
      tlp: Int,
      pap: Int,
      status: CaseStatus.Value,
      summary: Option[String],
      impactStatus: Option[String],
      resolutionStatus: Option[String],
      user: Option[String],
      customFields: Seq[CustomFieldWithValue]
  ): RichCase = {
    val `case` = new Case(number, title, description, severity, startDate, endDate, tags, flag, tlp, pap, status, summary) with Entity {
      override val _id: String                = __id
      override val _model: Model              = Model.vertex[Case]
      override val _createdBy: String         = __createdBy
      override val _updatedBy: Option[String] = __updatedBy
      override val _createdAt: Date           = __createdAt
      override val _updatedAt: Option[Date]   = __updatedAt
    }
    RichCase(`case`, impactStatus, resolutionStatus, user, customFields)
  }
}
