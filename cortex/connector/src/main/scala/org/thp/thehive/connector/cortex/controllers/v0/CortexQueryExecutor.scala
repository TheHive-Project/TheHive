package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, QueryExecutor}

@Singleton
class CortexQueryExecutor @Inject()(
    implicit val db: Database
) extends QueryExecutor {

  override val publicProperties: List[PublicProperty[_, _]] = Nil

  override val queries: Seq[ParamQuery[_]] = Nil
}
