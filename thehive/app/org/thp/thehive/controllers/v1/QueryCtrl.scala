package org.thp.thehive.controllers.v1

import scala.reflect.runtime.{universe => ru}

import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, PublicPropertyListBuilder, Query}
import org.thp.scalligraph.steps.BaseVertexSteps

case class IdOrName(idOrName: String)

trait QueryableCtrl {
  val entityName: String
  val publicProperties: List[PublicProperty[_, _]]
  val initialQuery: Query
  val pageQuery: ParamQuery[OutputParam]
  val outputQuery: Query
  val getQuery: ParamQuery[IdOrName]
  val extraQueries: Seq[ParamQuery[_]] = Nil

  def metaProperties[S <: BaseVertexSteps: ru.TypeTag]: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[S]
      .property("_createdBy", UniMapping.string)(_.simple.readonly)
      .property("_createdAt", UniMapping.date)(_.simple.readonly)
      .property("_updatedBy", UniMapping.string)(_.simple.readonly)
      .property("_updatedAt", UniMapping.date)(_.simple.readonly)
      .build
}
