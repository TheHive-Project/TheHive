package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions

import gremlin.scala.Key
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputObservable, OutputObservable}
import org.thp.thehive.models.{Observable, ObservableData, RichObservable}
import org.thp.thehive.services.ObservableSteps

trait ObservableConversion extends AttachmentConversion {
  implicit def fromInputObservable(inputObservable: InputObservable): Observable =
    inputObservable
      .into[Observable]
      .withFieldRenamed(_.dataType, _.`type`)
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.ioc, _.ioc.getOrElse(false))
      .withFieldComputed(_.sighted, _.sighted.getOrElse(false))
      .transform

  implicit def toOutputObservable(richObservable: RichObservable): Output[OutputObservable] =
    Output[OutputObservable](
      richObservable
        .into[OutputObservable]
        .withFieldConst(_._type, "case_artifact")
        .withFieldComputed(_.id, _.observable._id)
        .withFieldComputed(_._id, _.observable._id)
        .withFieldComputed(_.updatedAt, _.observable._updatedAt)
        .withFieldComputed(_.updatedBy, _.observable._updatedBy)
        .withFieldComputed(_.createdAt, _.observable._createdAt)
        .withFieldComputed(_.createdBy, _.observable._createdBy)
        .withFieldComputed(_.dataType, _.observable.`type`)
        .withFieldComputed(_.startDate, _.observable._createdAt)
        .withFieldComputed(_.data, _.data.map(_.data))
        .withFieldComputed(_.attachment, _.attachment.map(toOutputAttachment(_).toOutput))
        .transform
    )

  val observableProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ObservableSteps]
      .property[String]("status")(_.derived(_.constant("Ok")).readonly)
      .property[Date]("startDate")(_.derived(_.value(Key[Date]("_createdAt"))).readonly)
      .property[Boolean]("ioc")(_.simple.updatable)
      .property[Boolean]("sighted")(_.simple.updatable)
      .property[Set[String]]("tags")(_.simple.updatable)
      .property[String]("message")(_.simple.updatable)
      .property[Int]("tlp")(_.simple.updatable)
      .property[String]("dataType")(_.rename("type").updatable)
      .property[Option[String]]("data")(_.derived(_.outTo[ObservableData].value(Key[String]("data"))).readonly)
      // TODO add attachment ?
      .build
}
