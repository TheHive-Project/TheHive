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
import org.thp.thehive.services.{CaseSteps, CaseTemplateSteps, TaskSteps}

package object v1 {

  implicit def fromInputUser(inputUser: InputUser): User =
    inputUser
      .into[User]
      .withFieldComputed(_.id, _.login)
      .withFieldConst(_.apikey, None)
      .withFieldConst(_.password, None)
      .withFieldConst(_.status, UserStatus.ok)
      .withFieldComputed(_.permissions, _.permissions.flatMap(Permissions.withName)) // FIXME unkown permissions are ignored
      .transform

  implicit def toOutputUser(user: RichUser): Output[OutputUser] =
    Output[OutputUser](
      user
        .into[OutputUser]
        .withFieldComputed(_.permissions, _.permissions.map(_.name).toSet)
        .transform)

  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    Output[OutputCase](
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

  def fromInputCase(inputCase: InputCase, caseTemplate: Option[RichCaseTemplate]): Case =
    caseTemplate.fold(fromInputCase(inputCase)) { ct ⇒
      inputCase
        .into[Case]
        .withFieldComputed(_.title, ct.titlePrefix.getOrElse("") + _.title)
        .withFieldComputed(_.severity, _.severity.orElse(ct.severity).getOrElse(2))
        .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
        .withFieldComputed(_.flag, _.flag.getOrElse(ct.flag))
        .withFieldComputed(_.tlp, _.tlp.orElse(ct.tlp).getOrElse(2))
        .withFieldComputed(_.pap, _.pap.orElse(ct.pap).getOrElse(2))
        .withFieldComputed(_.tags, _.tags ++ ct.tags)
        .withFieldConst(_.summary, ct.summary)
        .withFieldConst(_.status, CaseStatus.open)
        .withFieldConst(_.number, 0)
        .transform
    }

  def outputCaseProperties(implicit db: Database): List[PublicProperty[Vertex, _, _]] =
    // format: off
    PublicPropertyListBuilder[CaseSteps, Vertex]
      .property[String]("title").simple
      .property[String]("description").simple
      .property[Int]("severity").simple
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

  implicit def fromInputCaseTemplate(inputCaseTemplate: InputCaseTemplate): CaseTemplate =
    inputCaseTemplate
      .into[CaseTemplate]
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .transform

  implicit def toOutputCaseTemplate(richCaseTemplate: RichCaseTemplate): Output[OutputCaseTemplate] =
    Output[OutputCaseTemplate](
      richCaseTemplate
        .into[OutputCaseTemplate]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .transform)

  def outputCaseTemplateProperties(implicit db: Database): List[PublicProperty[Vertex, _, _]] =
    // format: off
    PublicPropertyListBuilder[CaseTemplateSteps, Vertex]
      .property[String]("name").simple
      .property[Option[String]]("titlePrefix").simple
      .property[Option[String]]("description").simple
      .property[Option[Int]]("severity").simple
      .property[Set[String]]("tags").simple
      .property[Boolean]("flag").simple
      .property[Option[Int]]("tlp").simple
      .property[Option[Int]]("pap").simple
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
    Output[OutputTask](
      task
        .asInstanceOf[Task]
        .into[OutputTask]
        .withFieldComputed(_.status, _.status.toString)
        .transform
    )

  def outputTaskProperties(implicit db: Database): List[PublicProperty[Vertex, _, _]] =
    // format: off
    PublicPropertyListBuilder[TaskSteps, Vertex]
      .property[String]("title").simple
      .property[Option[String]]("description").simple
      .property[String]("status").simple
      .property[Boolean]("flag").simple
      .property[Option[Date]]("startDate").simple
      .property[Option[Date]]("endDate").simple
      .property[Int]("order").simple
      .property[Option[Date]]("dueDate").simple
    .build
  // format: on

  implicit def toOutputAudit(audit: RichAudit): Output[OutputAudit] =
    Output[OutputAudit](
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

  def fromInputCustomField(inputCustomFieldValue: InputCustomFieldValue): (String, Option[Any]) =
    inputCustomFieldValue.name → inputCustomFieldValue.value

  implicit def toOutputCustomField(customFieldValue: CustomFieldWithValue): Output[OutputCustomFieldValue] =
    Output[OutputCustomFieldValue](
      customFieldValue
        .into[OutputCustomFieldValue]
        .withFieldComputed(_.value, _.value.map(_.toString))
        .withFieldComputed(_.tpe, _.typeName)
        .transform
    )

  implicit def toOutputCustomField(customField: CustomField with Entity): Output[OutputCustomField] =
    Output[OutputCustomField](
      customField
        .asInstanceOf[CustomField]
        .into[OutputCustomField]
        .withFieldComputed(_.`type`, _.`type`.name)
        .transform
    )

  implicit def toOutputAlert(richAlert: RichAlert): Output[OutputAlert] =
    Output[OutputAlert](
      richAlert
        .into[OutputAlert]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(
          _.status,
          alert ⇒
            (alert.caseId, alert.read) match {
              case (None, true)  ⇒ "Ignored"
              case (None, false) ⇒ "New"
              case (_, true)     ⇒ "Imported"
              case (_, false)    ⇒ "Updated"
          })
        .transform)

  implicit def fromInputAlert(inputAlert: InputAlert): Alert =
    inputAlert
      .into[Alert]
      .withFieldComputed(_.severity, _.severity.getOrElse(2))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.pap, _.pap.getOrElse(2))
      .withFieldConst(_.read, false)
      .withFieldConst(_.lastSyncDate, new Date)
      .withFieldConst(_.follow, true)
      .transform
}
