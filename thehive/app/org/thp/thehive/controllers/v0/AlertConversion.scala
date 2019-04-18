package org.thp.thehive.controllers.v0

import java.util.Date

import gremlin.scala.{__, By, Key, Vertex}
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PublicProperty.readonly
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputAlert, OutputAlert}
import org.thp.thehive.models.{Alert, AlertCase, AlertCustomField, RichAlert}
import org.thp.thehive.services.AlertSteps

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.services._
import scala.language.implicitConversions

trait AlertConversion extends CustomFieldConversion {
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

  def alertProperties(implicit db: Database): List[PublicProperty[Vertex, _, _]] =
    // format: off
    PublicPropertyListBuilder[AlertSteps, Vertex]
      .property[String]("type").simple
      .property[String]("source").simple
      .property[String]("sourceRef").simple
      .property[String]("title").simple
      .property[String]("description").simple
      .property[Int]("severity").simple
      .property[Date]("date").simple
      .property[Option[Date]]("lastSyncDate") .simple
      .property[Set[String]]("tags").simple
      .property[Boolean]("flag").simple
      .property[Int]("tlp").simple
      .property[Int]("pap").simple
      .property[Boolean]("read").simple
      .property[Boolean]("follow").simple
      .property[String]("status").derived(
      _.project(_.apply(By(Key[Boolean]("read")))
        .and(By(__[Vertex].outToE[AlertCase].limit(1).count())))
        .map {
          case (true,  caseCount) if caseCount == 0 ⇒ "Ignored"
          case (true, caseCount) if caseCount == 1 ⇒ "New"
          case (false, caseCount) if caseCount == 0 ⇒ "Ignored"
          case (false, caseCount) if caseCount ==1 ⇒ "Imported"
        })(readonly)
      .property[Option[String]]("summary").simple
      .property[String]("user").simple
      .property[String]("customFieldName").derived(_.outTo[AlertCustomField].value("name"))(readonly)
      .property[String]("customFieldDescription").derived(_.outTo[AlertCustomField].value("description"))(readonly)
      .property[String]("customFieldType").derived(_.outTo[AlertCustomField].value("type"))(readonly)
      .property[String]("customFieldValue").derived(
      _.outToE[AlertCustomField].value("stringValue"),
      _.outToE[AlertCustomField].value("booleanValue"),
      _.outToE[AlertCustomField].value("integerValue"),
      _.outToE[AlertCustomField].value("floatValue"),
      _.outToE[AlertCustomField].value("dateValue"))(readonly)
      .build
  // format: on
}
