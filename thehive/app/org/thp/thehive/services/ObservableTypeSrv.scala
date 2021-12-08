package org.thp.thehive.services

import akka.actor.ActorRef
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.{BadRequestError, CreateError, EntityIdOrName}
import org.thp.thehive.models._
import org.thp.thehive.services.ObservableTypeOps._

import javax.inject.{Inject, Named, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class ObservableTypeSrv @Inject() (@Named("integrity-check-actor") integrityCheckActor: ActorRef) extends VertexSrv[ObservableType] {

  val observableObservableTypeSrv = new EdgeSrv[ObservableObservableType, Observable, ObservableType]

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[ObservableType] =
    startTraversal.getByName(name)

  override def exists(e: ObservableType)(implicit graph: Graph): Boolean = startTraversal.getByName(e.name).exists

  override def createEntity(e: ObservableType)(implicit graph: Graph, authContext: AuthContext): Try[ObservableType with Entity] = {
    integrityCheckActor ! EntityAdded("ObservableType")
    super.createEntity(e)
  }

  def create(observableType: ObservableType)(implicit graph: Graph, authContext: AuthContext): Try[ObservableType with Entity] =
    if (exists(observableType))
      Failure(CreateError(s"Observable type ${observableType.name} already exists"))
    else
      createEntity(observableType)

  def remove(idOrName: EntityIdOrName)(implicit graph: Graph): Try[Unit] =
    if (!isUsed(idOrName)) Success(get(idOrName).remove())
    else Failure(BadRequestError(s"Observable type $idOrName is used"))

  def isUsed(idOrName: EntityIdOrName)(implicit graph: Graph): Boolean = get(idOrName).inE[ObservableObservableType].exists

  def useCount(idOrName: EntityIdOrName)(implicit graph: Graph): Long =
    get(idOrName).in[ObservableObservableType].getCount
}

object ObservableTypeOps {

  implicit class ObservableTypeObs(traversal: Traversal.V[ObservableType]) {

    def get(idOrName: EntityIdOrName): Traversal.V[ObservableType] =
      idOrName.fold(traversal.getByIds(_), getByName)

    def getByName(name: String): Traversal.V[ObservableType] = traversal.has(_.name, name)

    def observables: Traversal.V[Observable] = traversal.in[ObservableObservableType].v[Observable]
  }
}

class ObservableTypeIntegrityCheckOps @Inject() (val db: Database, val service: ObservableTypeSrv) extends IntegrityCheckOps[ObservableType] {
  override def resolve(entities: Seq[ObservableType with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }

  override def globalCheck(): Map[String, Int] = Map.empty
}
