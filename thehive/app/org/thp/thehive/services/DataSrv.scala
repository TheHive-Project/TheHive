package org.thp.thehive.services

import java.lang.{Long => JLong}

import scala.util.{Success, Try}

import gremlin.scala.{Graph, GremlinScala, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.services.{VertexSrv, _}
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

class DataSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Data, DataSteps](raw) {

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

  override def newInstance(raw: GremlinScala[Vertex]): DataSteps = new DataSteps(raw)

  def getByData(data: String): DataSteps = newInstance(raw.has(Key("data") of data))

  def useCount: ScalarSteps[JLong] = ScalarSteps(raw.inTo[ObservableData].count())
}
