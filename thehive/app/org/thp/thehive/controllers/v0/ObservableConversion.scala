package org.thp.thehive.controllers.v0

import java.util.Date

import gremlin.scala.{Key, Vertex}
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PublicProperty.readonly
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputObservable, OutputObservable}
import org.thp.thehive.models.{Observable, ObservableData, RichObservable}
import org.thp.thehive.services.ObservableSteps

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.services._
import scala.language.implicitConversions

trait ObservableConversion {
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
        .transform)

  def observableProperties(implicit db: Database): List[PublicProperty[Vertex, _, _]] =
    // format: off
    PublicPropertyListBuilder[ObservableSteps, Vertex]
      .property[String]("status").derived(_.constant("Ok"))(readonly)
      .property[Date]("startDate").derived(_.value(Key[Date]("_createdAt")))(readonly)
      .property[Boolean]("ioc").simple
      .property[Boolean]("sighted").simple
      .property[Seq[String]]("tags").simple
      .property[String]("message").simple
      .property[String]("dataType").rename("type")
      .property[Option[String]]("data").derived(_.outTo[ObservableData].value(Key[String]("data")))(readonly)
      // TODO add attachment ?
      .build
  // format: on

}
