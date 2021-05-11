package org.thp.thehive.migration.th4

import akka.actor.ActorSystem
import org.janusgraph.core.JanusGraph
import org.thp.scalligraph.SingleInstance
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, UpdatableSchema}
import play.api.Configuration

import javax.inject.Provider
import scala.jdk.CollectionConverters._

class JanusDatabaseProvider(configuration: Configuration, system: ActorSystem, schemas: Set[UpdatableSchema]) extends Provider[Database] {

  def dropOtherConnections(db: JanusGraph): Unit = {
    val mgmt = db.openManagement()
    mgmt
      .getOpenInstances
      .asScala
      .filterNot(_.endsWith("(current)"))
      .foreach(mgmt.forceCloseInstance)
    mgmt.commit()
  }

  override lazy val get: Database = {
    val janusDatabase = JanusDatabase.openDatabase(configuration, system)
    dropOtherConnections(janusDatabase)
    val db = new JanusDatabase(
      janusDatabase,
      configuration,
      system,
      new SingleInstance(true)
    )
    schemas.toTry(schema => schema.update(db)).get
    db
  }
}
