package org.thp.thehive.services

import java.lang.{Long => JLong}

import akka.actor.ActorRef
import gremlin.scala.{Graph, GremlinScala, P, Vertex}
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{VertexSrv, _}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.models._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Success, Try}

@Singleton
class DataSrv @Inject() (@Named("data-dedup-actor") dataDedupActor: ActorRef)(implicit db: Database) extends VertexSrv[Data, DataSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): DataSteps = new DataSteps(raw)

  override def createEntity(e: Data)(implicit graph: Graph, authContext: AuthContext): Try[Data with Entity] =
    super.createEntity(e).map { data =>
      dataDedupActor ! DedupActor.EntityAdded
      data
    }

  def create(e: Data)(implicit graph: Graph, authContext: AuthContext): Try[Data with Entity] =
    initSteps
      .getByData(e.data)
      .headOption()
      .fold(createEntity(e))(Success(_))
}

@EntitySteps[Data]
class DataSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Data](raw) {

  def observables = new ObservableSteps(raw.inTo[ObservableData])

  def notShared(caseId: String): DataSteps = newInstance(
    raw.filter(
      _.inTo[ObservableData]
        .inTo[ShareObservable]
        .outTo[ShareCase]
        .has(T.id, P.neq(caseId))
        .count()
        .is(P.eq(0))
    )
  )

  override def newInstance(newRaw: GremlinScala[Vertex]): DataSteps = new DataSteps(newRaw)
  override def newInstance(): DataSteps                             = new DataSteps(raw.clone())

  def getByData(data: String): DataSteps = this.has("data", data)

  def useCount: Traversal[JLong, JLong] = Traversal(raw.inTo[ObservableData].count())
}

class DataDedupOps(val db: Database, val service: DataSrv) extends DedupOps[Data] {
  override def resolve(entities: List[Data with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      tail.foreach(service.get(_).remove())
      Success(())
    case _ => Success(())
  }
}

class DataDedupActor @Inject() (db: Database, service: DataSrv) extends DataDedupOps(db, service) with DedupActor {
  override val min: FiniteDuration = 10.seconds
  override val max: FiniteDuration = 1.minute
}

@Singleton
class DataDedupActorProvider extends DedupActorProvider[DataDedupActor]("Data")
