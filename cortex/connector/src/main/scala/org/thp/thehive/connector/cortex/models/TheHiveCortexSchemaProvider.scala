package org.thp.thehive.connector.cortex.models

import javax.inject.{Inject, Provider, Singleton}
import org.thp.scalligraph.models.Schema
import org.thp.thehive.models.TheHiveSchemaDefinition

@Singleton
class TheHiveCortexSchemaProvider @Inject() (thehiveSchema: TheHiveSchemaDefinition, cortexSchema: CortexSchemaDefinition) extends Provider[Schema] {
  override lazy val get: Schema = thehiveSchema + cortexSchema
}
