package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.query.QueryExecutor
import org.thp.thehive.controllers.v0.TheHiveQueryExecutor

import javax.inject.{Inject, Provider}

class TheHiveCortexQueryExecutorProvider @Inject() (thehiveQueryExecutor: TheHiveQueryExecutor, cortexQueryExecutor: CortexQueryExecutor)
    extends Provider[QueryExecutor] {
  override def get(): QueryExecutor = thehiveQueryExecutor ++ cortexQueryExecutor
}
