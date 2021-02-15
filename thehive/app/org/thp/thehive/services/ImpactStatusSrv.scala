package org.thp.thehive.services

import akka.actor.ActorRef
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.{CreateError, EntityIdOrName}
import org.thp.thehive.models.ImpactStatus
import org.thp.thehive.services.ImpactStatusOps._

import javax.inject.{Inject, Named, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class ImpactStatusSrv @Inject() (@Named("integrity-check-actor") integrityCheckActor: ActorRef) extends VertexSrv[ImpactStatus] {

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[ImpactStatus] =
    startTraversal.getByName(name)

  override def createEntity(e: ImpactStatus)(implicit graph: Graph, authContext: AuthContext): Try[ImpactStatus with Entity] = {
    integrityCheckActor ! EntityAdded("ImpactStatus")
    super.createEntity(e)
  }

  def create(impactStatus: ImpactStatus)(implicit graph: Graph, authContext: AuthContext): Try[ImpactStatus with Entity] =
    if (exists(impactStatus))
      Failure(CreateError(s"Impact status ${impactStatus.value} already exists"))
    else
      createEntity(impactStatus)

  override def exists(e: ImpactStatus)(implicit graph: Graph): Boolean = startTraversal.getByName(e.value).exists
}

object ImpactStatusOps {
  implicit class ImpactStatusOpsDefs(traversal: Traversal.V[ImpactStatus]) {
    def get(idOrName: EntityIdOrName): Traversal.V[ImpactStatus] =
      idOrName.fold(traversal.getByIds(_), getByName)

    def getByName(name: String): Traversal.V[ImpactStatus] = traversal.has(_.value, name)
  }
}

class ImpactStatusIntegrityCheckOps @Inject() (val db: Database, val service: ImpactStatusSrv) extends IntegrityCheckOps[ImpactStatus] {
  override def resolve(entities: Seq[ImpactStatus with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }

  override def globalCheck(): Map[String, Long] = Map.empty
}
