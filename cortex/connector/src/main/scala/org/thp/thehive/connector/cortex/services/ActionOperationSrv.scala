package org.thp.thehive.connector.cortex.services

import javax.inject.Inject
import org.thp.scalligraph.models.{Database, Entity}

class ActionOperationSrv @Inject()(
    implicit db: Database
) {
  def execute(entity: Entity) = ???
}
