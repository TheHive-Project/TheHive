package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions

import play.api.libs.json.Json

import gremlin.scala.{__, By, Key, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.IdMapping
import org.thp.thehive.dto.v0.{InputAlert, OutputAlert}
import org.thp.thehive.models.{Alert, AlertCase, RichAlert, RichObservable}
import org.thp.thehive.services.{AlertSrv, AlertSteps}

object AlertConversion {
  import CustomFieldConversion._
  import ObservableConversion._

  implicit def toOutputAlert(richAlert: RichAlert): Output[OutputAlert] =
    Output[OutputAlert](
      richAlert
        .into[OutputAlert]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldRenamed(_._id, _.id)
        .withFieldConst(_._type, "alert")
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .withFieldComputed(
          _.status,
          alert =>
            (alert.caseId, alert.read) match {
              case (None, true)  => "Ignored"
              case (None, false) => "New"
              case (_, true)     => "Imported"
              case (_, false)    => "Updated"
            }
        )
        .transform
    )

  implicit def toOutputAlertWithObservables(richAlertWithObservables: (RichAlert, Seq[RichObservable])): Output[OutputAlert] =
    Output[OutputAlert](
      richAlertWithObservables
        ._1
        .into[OutputAlert]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldRenamed(_._id, _.id)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldConst(_._type, "alert")
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .withFieldComputed(
          _.status,
          alert =>
            (alert.caseId, alert.read) match {
              case (None, true)  => "Ignored"
              case (None, false) => "New"
              case (_, true)     => "Imported"
              case (_, false)    => "Updated"
            }
        )
        .withFieldConst(_.artifacts, richAlertWithObservables._2.map(toOutputObservable(_).toOutput))
        .transform
    )

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

  def alertProperties(alertSrv: AlertSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[AlertSteps]
      .property("type", UniMapping.string)(_.field.updatable)
      .property("source", UniMapping.string)(_.field.updatable)
      .property("sourceRef", UniMapping.string)(_.field.updatable)
      .property("title", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string)(_.field.updatable)
      .property("severity", UniMapping.int)(_.field.updatable)
      .property("date", UniMapping.date)(_.field.updatable)
      .property("lastSyncDate", UniMapping.date.optional)(_.field.updatable)
      .property("tags", UniMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            alertSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(alert => alertSrv.updateTagNames(alert, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("tlp", UniMapping.int)(_.field.updatable)
      .property("pap", UniMapping.int)(_.field.updatable)
      .property("read", UniMapping.boolean)(_.field.updatable)
      .property("follow", UniMapping.boolean)(_.field.updatable)
      .property("status", UniMapping.string)(
        _.select(
          _.project(
            _.apply(By(Key[Boolean]("read")))
              .and(By(__[Vertex].outToE[AlertCase].limit(1).count()))
          ).map {
            case (false, caseCount) if caseCount == 0 => "New"
            case (false, _)                           => "Updated"
            case (true, caseCount) if caseCount == 0  => "Ignored"
            case (true, _)                            => "Imported"
          }
        ).readonly
      )
      .property("summary", UniMapping.string.optional)(_.field.updatable)
      .property("user", UniMapping.string)(_.field.updatable)
      .property("customFieldName", UniMapping.string)(_.select(_.customFields.name).readonly)
      .property("customFieldDescription", UniMapping.string)(_.select(_.customFields.description).readonly)
//      .property("customFieldType", UniMapping.string)(_.derived(_.customFields.`type`).readonly)
//      .property("customFieldValue", UniMapping.string)(_.derived(_.customFieldsValue.map(_.value.toString)).readonly)
      .property("case", IdMapping)(_.select(_.`case`._id).readonly)
      .build
}
