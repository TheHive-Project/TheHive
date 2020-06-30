package org.thp.thehive.services

import akka.actor.ActorRef
import gremlin.scala._
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.scalligraph.{BadRequestError, CreateError, EntitySteps}
import org.thp.thehive.models._

import scala.util.{Failure, Success, Try}

@Singleton
class ObservableTypeSrv @Inject() (@Named("integrity-check-actor") integrityCheckActor: ActorRef)(
    implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[ObservableType, ObservableTypeSteps] {

  val observableObservableTypeSrv                                                           = new EdgeSrv[ObservableObservableType, Observable, ObservableType]
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ObservableTypeSteps = new ObservableTypeSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ObservableTypeSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  override def exists(e: ObservableType)(implicit graph: Graph): Boolean = initSteps.getByName(e.name).exists()

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
    get(idOrName).inTo[ObservableObservableType].getCount
}

@EntitySteps[ObservableType]
class ObservableTypeSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph)
    extends VertexSteps[ObservableType](raw) {

  override def newInstance(newRaw: GremlinScala[Vertex]): ObservableTypeSteps = new ObservableTypeSteps(newRaw)
  override def newInstance(): ObservableTypeSteps                             = new ObservableTypeSteps(raw.clone())

  def get(idOrName: String): ObservableTypeSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ObservableTypeSteps = new ObservableTypeSteps(raw.has(Key("name") of name))
}

class ObservableTypeIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: ObservableTypeSrv)
    extends IntegrityCheckOps[ObservableType] {
  override def resolve(entities: List[ObservableType with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      tail.foreach(service.get(_).remove())
      Success(())
    case _ => Success(())
  }
}
