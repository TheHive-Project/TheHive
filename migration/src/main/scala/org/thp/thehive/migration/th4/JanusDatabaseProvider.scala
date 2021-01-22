package org.thp.thehive.migration.th4

import akka.actor.ActorSystem
import org.thp.scalligraph.SingleInstance
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, UpdatableSchema}
import play.api.Configuration

import javax.inject.{Inject, Provider, Singleton}
import scala.collection.immutable

@Singleton
class JanusDatabaseProvider @Inject() (configuration: Configuration, system: ActorSystem, schemas: immutable.Set[UpdatableSchema])
    extends Provider[Database] {
  override lazy val get: Database = {
    val db = new JanusDatabase(
      JanusDatabase.openDatabase(configuration, system),
      configuration,
      system,
      new SingleInstance(true)
    )
    schemas.foreach(schema => db.createSchemaFrom(schema)(schema.authContext))
    db
  }
}
