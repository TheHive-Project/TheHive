package org.thp.thehive.services

import akka.actor.ActorRef
import gremlin.scala._
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.{CreateError, EntitySteps}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models.ResolutionStatus

import scala.util.{Failure, Success, Try}

@Singleton
class ResolutionStatusSrv @Inject() (@Named("integrity-check-actor") integrityCheckActor: ActorRef)(
    implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[ResolutionStatus, ResolutionStatusSteps] {

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ResolutionStatusSteps = new ResolutionStatusSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ResolutionStatusSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  override def createEntity(e: ResolutionStatus)(implicit graph: Graph, authContext: AuthContext): Try[ResolutionStatus with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("Resolution")
    super.createEntity(e)
  }

  def create(resolutionStatus: ResolutionStatus)(implicit graph: Graph, authContext: AuthContext): Try[ResolutionStatus with Entity] =
    if (exists(resolutionStatus))
      Failure(CreateError(s"Resolution status ${resolutionStatus.value} already exists"))
    else
      createEntity(resolutionStatus)

  override def exists(e: ResolutionStatus)(implicit graph: Graph): Boolean = initSteps.getByName(e.value).exists()
}

@EntitySteps[ResolutionStatus]
class ResolutionStatusSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph)
    extends VertexSteps[ResolutionStatus](raw) {

  override def newInstance(newRaw: GremlinScala[Vertex]): ResolutionStatusSteps = new ResolutionStatusSteps(newRaw)
  override def newInstance(): ResolutionStatusSteps                             = new ResolutionStatusSteps(raw.clone())

  def get(idOrName: String): ResolutionStatusSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ResolutionStatusSteps = new ResolutionStatusSteps(raw.has(Key("value") of name))

}

class ResolutionStatusIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: ResolutionStatusSrv)
    extends IntegrityCheckOps[ResolutionStatus] {
  override def resolve(entities: List[ResolutionStatus with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      service.getByIds(tail.map(_._id): _*).remove()
      Success(())
    case _ => Success(())
  }
}
