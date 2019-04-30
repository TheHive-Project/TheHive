package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions

import gremlin.scala.{__, By, Key, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputAlert, OutputAlert}
import org.thp.thehive.models.{Alert, AlertCase, AlertCustomField, RichAlert}
import org.thp.thehive.services.AlertSteps

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

  val alertProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[AlertSteps]
      .property[String]("type")(_.simple.updatable)
      .property[String]("source")(_.simple.updatable)
      .property[String]("sourceRef")(_.simple.updatable)
      .property[String]("title")(_.simple.updatable)
      .property[String]("description")(_.simple.updatable)
      .property[Int]("severity")(_.simple.updatable)
      .property[Date]("date")(_.simple.updatable)
      .property[Option[Date]]("lastSyncDate")(_.simple.updatable)
      .property[Set[String]]("tags")(_.simple.updatable)
      .property[Boolean]("flag")(_.simple.updatable)
      .property[Int]("tlp")(_.simple.updatable)
      .property[Int]("pap")(_.simple.updatable)
      .property[Boolean]("read")(_.simple.updatable)
      .property[Boolean]("follow")(_.simple.updatable)
      .property[String]("status")(_.derived(_.project(_.apply(By(Key[Boolean]("read")))
        .and(By(__[Vertex].outToE[AlertCase].limit(1).count())))
        .map {
          case (true, caseCount) if caseCount == 0  ⇒ "Ignored"
          case (true, caseCount) if caseCount == 1  ⇒ "New"
          case (false, caseCount) if caseCount == 0 ⇒ "Ignored"
          case (false, caseCount) if caseCount == 1 ⇒ "Imported"
        }).readonly)
      .property[Option[String]]("summary")(_.simple.updatable)
      .property[String]("user")(_.simple.updatable)
      .property[String]("customFieldName")(_.derived(_.outTo[AlertCustomField].value[String]("name")).readonly)
      .property[String]("customFieldDescription")(_.derived(_.outTo[AlertCustomField].value[String]("description")).readonly)
      .property[String]("customFieldType")(_.derived(_.outTo[AlertCustomField].value[String]("type")).readonly)
      .property[String]("customFieldValue")(_.derived(
        _.outToE[AlertCustomField].value[Any]("stringValue"),
        _.outToE[AlertCustomField].value[Any]("booleanValue"),
        _.outToE[AlertCustomField].value[Any]("integerValue"),
        _.outToE[AlertCustomField].value[Any]("floatValue"),
        _.outToE[AlertCustomField].value[Any]("dateValue")
      ).readonly)
      .build
}
