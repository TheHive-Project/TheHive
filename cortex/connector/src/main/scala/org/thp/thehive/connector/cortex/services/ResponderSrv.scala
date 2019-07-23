package org.thp.thehive.connector.cortex.services

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.v0.OutputCortexResponder
import org.thp.scalligraph.models.Database
import org.thp.thehive.models.EntityHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResponderSrv @Inject()(cortexConfig: CortexConfig, implicit val ex: ExecutionContext, entityHelper: EntityHelper) {

  /**
    * Gets a list of OutputCortexResponder from all available CortexClients
    * in relation with the entity type and id passed and
    * filtered by the allowed entity's tlp and pap
    *
    * @param entityType the entity
    * @param entityId its id
    * @param graph necessary graph db
    * @param db necessary db instance
    * @return
    */
  def getRespondersByType(
      entityType: String,
      entityId: String
  )(implicit graph: Graph, db: Database): Future[List[OutputCortexResponder]] =
    for {
      (tlp, pap) <- Future.fromTry(entityHelper.threatLevels(entityType, entityId)).recover { case _ => (0, 0) }
      responders <- Future.traverse(cortexConfig.instances)(client => client._2.getRespondersByType(entityType)).map(_.flatten)
    } yield responders
      .groupBy(_.name)
      .values
      .map(_.reduce(_ join _))
      .toList
      .filter(w => w.maxTlp.fold(true)(_ >= tlp) && w.maxPap.fold(true)(_ >= pap))

}
