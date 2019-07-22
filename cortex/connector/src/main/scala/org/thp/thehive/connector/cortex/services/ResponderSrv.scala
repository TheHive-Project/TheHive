package org.thp.thehive.connector.cortex.services

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.v0.OutputCortexResponder
import org.thp.scalligraph.models.{Database, Schema}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ResponderSrv @Inject()(cortexConfig: CortexConfig, implicit val ex: ExecutionContext, schema: Schema) {

  lazy val logger = Logger(getClass)

//  def getRespondersByType(entityType: String, entityId: String)(implicit graph: Graph, db: Database): Future[Seq[OutputCortexResponder]] =

}
