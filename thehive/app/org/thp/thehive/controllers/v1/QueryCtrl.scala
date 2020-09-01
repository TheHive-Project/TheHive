package org.thp.thehive.controllers.v1

import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query}

case class IdOrName(idOrName: String)

trait QueryableCtrl {
  val entityName: String
  val publicProperties: List[PublicProperty[_, _]]
  val initialQuery: Query
  val pageQuery: ParamQuery[OutputParam]
  val outputQuery: Query
  val getQuery: ParamQuery[IdOrName]
  val extraQueries: Seq[ParamQuery[_]] = Nil
}
