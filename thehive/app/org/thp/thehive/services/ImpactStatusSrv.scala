package org.thp.thehive.services

import akka.actor.ActorRef
import gremlin.scala._
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models.ImpactStatus

import scala.util.{Success, Try}

@Singleton
class ImpactStatusSrv @Inject() (@Named("integrity-check-actor") integrityCheckActor: ActorRef)(
    implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[ImpactStatus, ImpactStatusSteps] {

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ImpactStatusSteps = new ImpactStatusSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ImpactStatusSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  override def createEntity(e: ImpactStatus)(implicit graph: Graph, authContext: AuthContext): Try[ImpactStatus with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("ImpactStatus")
    super.createEntity(e)
  }

  def create(impactStatus: ImpactStatus)(implicit graph: Graph, authContext: AuthContext): Try[ImpactStatus with Entity] = createEntity(impactStatus)

  override def exists(e: ImpactStatus)(implicit graph: Graph): Boolean = initSteps.getByName(e.value).exists()
}

@EntitySteps[ImpactStatus]
class ImpactStatusSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph)
    extends VertexSteps[ImpactStatus](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): ImpactStatusSteps = new ImpactStatusSteps(newRaw)
  override def newInstance(): ImpactStatusSteps                             = new ImpactStatusSteps(raw.clone())

  def get(idOrName: String): ImpactStatusSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ImpactStatusSteps = new ImpactStatusSteps(raw.has(Key("value") of name))
}

class ImpactStatusIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: ImpactStatusSrv)
    extends IntegrityCheckOps[ImpactStatus] {
  override def resolve(entities: List[ImpactStatus with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      tail.foreach(service.get(_).remove())
      Success(())
    case _ => Success(())
  }
}
