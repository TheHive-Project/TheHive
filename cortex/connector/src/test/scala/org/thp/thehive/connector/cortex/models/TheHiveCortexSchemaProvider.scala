package org.thp.thehive.connector.cortex.models

import javax.inject.{Inject, Provider, Singleton}
import org.thp.scalligraph.models.Schema
import org.thp.thehive.models.TheHiveSchema

@Singleton
class TheHiveCortexSchemaProvider @Inject()(thehiveSchema: TheHiveSchema, cortexSchema: CortexSchema) extends Provider[Schema] {
  override def get(): Schema = thehiveSchema + cortexSchema
}
