package org.thp.thehive.services

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{BadRequestError, CreateError}
import org.thp.thehive.models._
import org.thp.thehive.services.ObservableTypeOps._

import scala.util.{Failure, Success, Try}

@Singleton
class ObservableTypeSrv @Inject() (@Named("integrity-check-actor") integrityCheckActor: ActorRef)(
    implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[ObservableType] {

  val observableObservableTypeSrv = new EdgeSrv[ObservableObservableType, Observable, ObservableType]

  override def get(idOrName: String)(implicit graph: Graph): Traversal.V[ObservableType] =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else startTraversal.getByName(idOrName)

  override def exists(e: ObservableType)(implicit graph: Graph): Boolean = startTraversal.getByName(e.name).exists

  override def createEntity(e: ObservableType)(implicit graph: Graph, authContext: AuthContext): Try[ObservableType with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("ObservableType")
    super.createEntity(e)
  }

  def create(observableType: ObservableType)(implicit graph: Graph, authContext: AuthContext): Try[ObservableType with Entity] =
    if (exists(observableType))
      Failure(CreateError(s"Observable type ${observableType.name} already exists"))
    else
      createEntity(observableType)

  def remove(idOrName: String)(implicit graph: Graph): Try[Unit] =
    if (useCount(idOrName) == 0) Success(get(idOrName).remove())
    else Failure(BadRequestError(s"Observable type $idOrName is used"))

  def useCount(idOrName: String)(implicit graph: Graph): Long =
    get(idOrName).in[ObservableObservableType].getCount
}

object ObservableTypeOps {

  implicit class ObservableTypeObs(traversal: Traversal.V[ObservableType]) {

    def get(idOrName: String)(implicit db: Database): Traversal.V[ObservableType] =
      if (db.isValidId(idOrName)) traversal.getByIds(idOrName)
      else getByName(idOrName)

    def getByName(name: String): Traversal.V[ObservableType] = traversal.has("name", name).v[ObservableType]
  }
}

class ObservableTypeIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: ObservableTypeSrv)
    extends IntegrityCheckOps[ObservableType] {
  override def resolve(entities: Seq[ObservableType with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      service.getByIds(tail.map(_._id): _*).remove()
      Success(())
    case _ => Success(())
  }
}
