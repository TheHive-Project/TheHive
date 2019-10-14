package org.thp.thehive.services

import java.lang.{Long => JLong}

import scala.util.{Success, Try}

import gremlin.scala.{Graph, GremlinScala, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{VertexSrv, _}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.models._

@Singleton
class DataSrv @Inject()()(implicit db: Database) extends VertexSrv[Data, DataSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): DataSteps = new DataSteps(raw)

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

  def getByData(data: String): DataSteps = newInstance(raw.has(Key("data") of data))

  def useCount: Traversal[JLong, JLong] = Traversal(raw.inTo[ObservableData].count())
}
