package org.thp.thehive.controllers

import java.util.Date

import scala.language.implicitConversions

import gremlin.scala.Vertex
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services.RichGremlinScala
import org.thp.thehive.dto.v1.{InputCase, InputTask, OutputCase, OutputTask}
import org.thp.thehive.models._
import org.thp.thehive.services.CaseSteps

package object v1 {
  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    new Output[OutputCase](
      richCase
        .into[OutputCase]
        .withFieldComputed(_.customFields, _.customFields.map(CustomFieldXfrm.toOutput).toSet)
        .withFieldComputed(_.status, _.status.toString)
        .transform)

//  implicit val publicCase: ToPublic[RichCase, OutputCase] = ToPublic[RichCase, OutputCase](
//    _.into[OutputCase]
//      .withFieldComputed(_.customFields, _.customFields.map(CustomFieldXfrm.toOutput).toSet)
//      .withFieldComputed(_.status, _.status.toString)
//      .transform)

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
    PublicPropertyListBuilder[CaseSteps, Vertex]
      .property[String]("title")
      .simple
      .property[String]("description")
      .simple
      .property[String]("severity")
      .simple
      .property[Date]("startDate")
      .simple
      .property[Option[Date]]("endDate")
      .simple
      .property[Set[String]]("tags")
      .simple
      .property[Boolean]("flag")
      .simple
      .property[Int]("tlp")
      .simple
      .property[Int]("pap")
      .simple
      .property[String]("status")
      .simple
      .property[Option[String]]("summary")
      .simple
      .property[String]("user")
      .simple
      .property[String]("customFieldName")
      .derived(_ ⇒ _.outTo[CaseCustomField].value("name"))
      .property[String]("customFieldDescription")
      .derived(_ ⇒ _.outTo[CaseCustomField].value("description"))
      .property[String]("customFieldType")
      .derived(_ ⇒ _.outTo[CaseCustomField].value("type"))
      .property[String]("customFieldValue")
      .seq(_ ⇒
        Seq(
          _.outToE[CaseCustomField].value("stringValue"),
          _.outToE[CaseCustomField].value("booleanValue"),
          _.outToE[CaseCustomField].value("integerValue"),
          _.outToE[CaseCustomField].value("floatValue"),
          _.outToE[CaseCustomField].value("dateValue")
      ))
      .build

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
}
