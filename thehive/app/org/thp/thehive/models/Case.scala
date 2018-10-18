package org.thp.thehive.models

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph._
import org.thp.scalligraph.models.{DefineIndex, Entity, IndexType}

object CaseStatus extends Enumeration {
  val open, resolved, deleted = Value
}

@VertexEntity
case class ResolutionStatus(value: String) {
  require(!value.isEmpty, "ResolutionStatus can't be empty")
}

object ResolutionStatus {
  val initialValues = Seq(
    ResolutionStatus("Indeterminate"),
    ResolutionStatus("FalsePositive"),
    ResolutionStatus("TruePositive"),
    ResolutionStatus("Other"),
    ResolutionStatus("Duplicated")
  )
}

@EdgeEntity[Case, ResolutionStatus]
case class CaseResolutionStatus()

@VertexEntity
case class ImpactStatus(value: String) {
  require(!value.isEmpty, "ImpactStatus can't be empty")
}

@EdgeEntity[Case, ImpactStatus]
case class CaseImpactStatus()

@EdgeEntity[Case, CustomField]
case class CaseCustomField(
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Float] = None,
    dateValue: Option[Date] = None)

@EdgeEntity[Case, Indicator]
case class CaseIndicator()

@EdgeEntity[Case, User]
case class CaseUser()

@EdgeEntity[Case, Observable]
case class CaseObservable()

@EdgeEntity[Case, Organisation]
case class CaseOrganisation()

@VertexEntity
@DefineIndex(IndexType.unique, "number")
case class Case(
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
    summary: Option[String])

case class CustomFieldValue(name: String, description: String, tpe: String, value: Any)

case class RichCase(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String],
    _createdAt: Date,
    _updatedAt: Option[Date],
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
    user: String,
    organisation: String,
    customFields: Seq[CustomFieldValue]
)

object RichCase {
  def apply(
      `case`: Case with Entity,
      caseImpactStatus: Option[String],
      user: String,
      organisation: String,
      customFields: Seq[CustomFieldValue]): RichCase =
    `case`
      .asInstanceOf[Case]
      .into[RichCase]
      .withFieldConst(_._id, `case`._id)
      .withFieldConst(_._createdAt, `case`._createdAt)
      .withFieldConst(_._createdBy, `case`._createdBy)
      .withFieldConst(_._updatedAt, `case`._updatedAt)
      .withFieldConst(_._updatedBy, `case`._updatedBy)
      .withFieldConst(_.impactStatus, caseImpactStatus)
      .withFieldConst(_.organisation, organisation)
      .withFieldConst(_.user, user)
      .withFieldConst(_.customFields, customFields)
      .transform
}
