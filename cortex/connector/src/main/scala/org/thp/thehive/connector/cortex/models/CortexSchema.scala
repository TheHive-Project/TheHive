package org.thp.thehive.connector.cortex.models

import scala.collection.JavaConverters._

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.ReflectionSchema

@Singleton
class CortexSchema @Inject()() extends ReflectionSchema(getClass.getClassLoader, "org.thp.thehive.connector.cortex.models")
