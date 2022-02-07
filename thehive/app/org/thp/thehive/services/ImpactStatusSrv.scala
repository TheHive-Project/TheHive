package org.thp.thehive.services

import akka.actor.typed.ActorRef
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{DedupCheck, EntitySelector, IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.{CreateError, EntityIdOrName}
import org.thp.thehive.models.ImpactStatus
import org.thp.thehive.services.ImpactStatusOps._

import javax.inject.{Inject, Provider, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class ImpactStatusSrv @Inject() (integrityCheckActorProvider: Provider[ActorRef[IntegrityCheck.Request]]) extends VertexSrv[ImpactStatus] {
  lazy val integrityCheckActor: ActorRef[IntegrityCheck.Request] = integrityCheckActorProvider.get

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[ImpactStatus] =
    startTraversal.getByName(name)

  override def createEntity(e: ImpactStatus)(implicit graph: Graph, authContext: AuthContext): Try[ImpactStatus with Entity] = {
    integrityCheckActor ! IntegrityCheck.EntityAdded("ImpactStatus")
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

class ImpactStatusIntegrityCheck @Inject() (val db: Database, val service: ImpactStatusSrv) extends DedupCheck[ImpactStatus]
