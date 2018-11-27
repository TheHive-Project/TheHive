package org.thp.thehive.controllers

import java.util.Date

import scala.language.implicitConversions
import gremlin.scala.Vertex
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services.RichGremlinScala
import org.thp.thehive.dto.v1._
import org.thp.thehive.models._
import org.thp.thehive.services.CaseSteps

package object v1 {
  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    new Output[OutputCase](
      richCase
        .into[OutputCase]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.status, _.status.toString)
        .transform)

  implicit def fromInputCase(inputCase: InputCase): Case =
    inputCase
      .into[Case]
      .withFieldComputed(_.severity, _.severity.getOrElse(2))
      .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.pap, _.pap.getOrElse(2))
      .withFieldConst(_.status, CaseStatus.open)
      .withFieldConst(_.number, 0)
      .transform

  def outputCaseProperties(implicit db: Database): List[PublicProperty[Vertex, _]] =
    // format: off
    PublicPropertyListBuilder[CaseSteps, Vertex]
      .property[String]("title").simple
      .property[String]("description").simple
      .property[String]("severity").simple
      .property[Date]("startDate").simple
      .property[Option[Date]]("endDate") .simple
      .property[Set[String]]("tags").simple
      .property[Boolean]("flag").simple
      .property[Int]("tlp").simple
      .property[Int]("pap").simple
      .property[String]("status").simple
      .property[Option[String]]("summary").simple
      .property[String]("user").simple
      .property[String]("customFieldName").derived(_ ⇒ _.outTo[CaseCustomField], "name")
      .property[String]("customFieldDescription").derived(_ ⇒ _.outTo[CaseCustomField], "description")
      .property[String]("customFieldType").derived(_ ⇒ _.outTo[CaseCustomField], "type")
      .property[String]("customFieldValue").seq(
        _.derived(_ ⇒ _.outToE[CaseCustomField], "stringValue")
         .derived(_ ⇒ _.outToE[CaseCustomField], "booleanValue")
         .derived(_ ⇒ _.outToE[CaseCustomField], "integerValue")
         .derived(_ ⇒ _.outToE[CaseCustomField], "floatValue")
         .derived(_ ⇒ _.outToE[CaseCustomField], "dateValue"))
      .build
  // format: on

  implicit def fromInputTask(inputTask: InputTask): Task =
    inputTask
      .into[Task]
      .withFieldComputed(_.status, _.status.fold(TaskStatus.waiting)(TaskStatus.withName))
      .withFieldComputed(_.order, _.order.getOrElse(0))
      .transform

  implicit def toOutputTask(task: Task with Entity): Output[OutputTask] =
    new Output[OutputTask](
      task
        .asInstanceOf[Task]
        .into[OutputTask]
        .withFieldComputed(_.status, _.status.toString)
        .transform
    )

  implicit def toOutputAudit(audit: RichAudit): Output[OutputAudit] =
    new Output[OutputAudit](
      audit
        .into[OutputAudit]
        .withFieldComputed(_.operation, _.operation.toString)
        .withFieldComputed(_._id, _._id)
        .withFieldComputed(_._createdAt, _._createdAt)
        .withFieldComputed(_._createdBy, _._createdBy)
        .withFieldComputed(_.obj, a ⇒ OutputEntity(a.obj))
        .withFieldComputed(
          _.summary,
          _.summary.mapValues(opCount ⇒
            opCount.map {
              case (op, count) ⇒ op.toString → count
          }))
        .transform
    )

  def fromInputCustomField(inputCustomFieldValue: InputCustomFieldValue): (String, Any) = inputCustomFieldValue.name → inputCustomFieldValue.value

  implicit def toOutputCustomField(customFieldValue: CustomFieldWithValue): Output[OutputCustomFieldValue] =
    new Output[OutputCustomFieldValue](
      customFieldValue
        .into[OutputCustomFieldValue]
        .withFieldComputed(_.value, _.value.toString)
        .transform
    )

  implicit def toOutputCustomField(customField: CustomField with Entity): Output[OutputCustomField] =
    new Output[OutputCustomField](
      customField
        .asInstanceOf[CustomField]
        .into[OutputCustomField]
        .withFieldComputed(_.`type`, _.`type`.name)
        .transform
    )

  implicit def toOutputAlert(richAlert: RichAlert): Output[OutputAlert] =
    new Output[OutputAlert](
      richAlert
        .into[OutputAlert]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.status, _.status.toString)
        .transform)

  implicit def fromInputAlert(inputAlert: InputAlert): Alert =
    inputAlert
      .into[Alert]
      .withFieldComputed(_.severity, _.severity.getOrElse(2))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.pap, _.pap.getOrElse(2))
      .withFieldConst(_.status, AlertStatus.`new`)
      .withFieldConst(_.lastSyncDate, new Date)
      .withFieldConst(_.follow, true)
      .transform
}
