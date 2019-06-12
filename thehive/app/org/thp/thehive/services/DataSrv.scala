package org.thp.thehive.services

import gremlin.scala.{Graph, GremlinScala, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._
import gremlin.scala.{Graph, GremlinScala, Key, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.VertexSrv

@Singleton
class DataSrv @Inject()()(implicit db: Database) extends VertexSrv[Data, DataSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): DataSteps = new DataSteps(raw)

  override def create(e: Data)(implicit graph: Graph, authContext: AuthContext): Data with Entity =
    initSteps
      .getByData(e.data)
      .headOption()
      .getOrElse(super.create(e))
}

class DataSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Data, DataSteps](raw) {
  def remove(): Unit = {
    raw.drop().iterate()
    ()
  }

  def observables = new ObservableSteps(raw.inTo[ObservableData])

  def notShared: DataSteps = newInstance(raw.filter(_.inTo[ObservableData].limit(2).count().is(P.lte(1))))

  def getByData(data: String): DataSteps                         = newInstance(raw.has(Key("data") of data))

  override def newInstance(raw: GremlinScala[Vertex]): DataSteps = new DataSteps(raw)
}
