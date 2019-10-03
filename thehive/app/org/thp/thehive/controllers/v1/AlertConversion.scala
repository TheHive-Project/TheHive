package org.thp.thehive.controllers.v1

import java.util.Date

import scala.language.implicitConversions

import play.api.libs.json.Json

import gremlin.scala.{__, By, Key, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v1.{InputAlert, OutputAlert}
import org.thp.thehive.models.{Alert, AlertCase, RichAlert}
import org.thp.thehive.services.{AlertSrv, AlertSteps}

object AlertConversion {
  import CustomFieldConversion._

  implicit def toOutputAlert(richAlert: RichAlert): Output[OutputAlert] =
    Output[OutputAlert](
      richAlert
        .into[OutputAlert]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
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

  // TODO fix properties
  def alertProperties(alertSrv: AlertSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[AlertSteps]
      .property("type", UniMapping.string)(_.simple.updatable)
      .property("source", UniMapping.string)(_.simple.updatable)
      .property("sourceRef", UniMapping.string)(_.simple.updatable)
      .property("title", UniMapping.string)(_.simple.updatable)
      .property("description", UniMapping.string)(_.simple.updatable)
      .property("severity", UniMapping.int)(_.simple.updatable)
      .property("date", UniMapping.date)(_.simple.updatable)
      .property("lastSyncDate", UniMapping.date.optional)(_.simple.updatable)
      .property("tags", UniMapping.string.set)(
        _.derived(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            alertSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(alert => alertSrv.updateTagNames(alert, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.simple.updatable)
      .property("tlp", UniMapping.int)(_.simple.updatable)
      .property("pap", UniMapping.int)(_.simple.updatable)
      .property("read", UniMapping.boolean)(_.simple.updatable)
      .property("follow", UniMapping.boolean)(_.simple.updatable)
      .property("status", UniMapping.string)(
        _.derived(
          _.project(
            _.apply(By(Key[Boolean]("read")))
              .and(By(__[Vertex].outToE[AlertCase].limit(1).count()))
          ).map {
            case (true, caseCount) if caseCount == 0  => "Ignored"
            case (true, caseCount) if caseCount == 1  => "New"
            case (false, caseCount) if caseCount == 0 => "Ignored"
            case (false, caseCount) if caseCount == 1 => "Imported"
          }
        ).readonly
      )
      .property("summary", UniMapping.string.optional)(_.simple.updatable)
      .property("user", UniMapping.string)(_.simple.updatable)
      .property("customFieldName", UniMapping.string)(_.derived(_.customFields.name).readonly)
      .property("customFieldDescription", UniMapping.string)(_.derived(_.customFields.description).readonly)
//      .property("customFieldType", UniMapping.string)(_.derived(_.customFields.`type`).readonly)
//      .property("customFieldValue", UniMapping.string)(_.derived(_.customFieldsValue.map(_.value.toString)).readonly)
      .build
}
