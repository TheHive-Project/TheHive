package org.thp.thehive.services

import gremlin.scala.{Graph, GremlinScala, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class DataSrv @Inject()()(implicit db: Database) extends VertexSrv[Data, DataSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): DataSteps = new DataSteps(raw)
}

class DataSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Data, DataSteps](raw) {
  def remove(): Unit = {
    raw.drop().iterate()
    ()
  }

  def observables = new ObservableSteps(raw.inTo[ObservableData])

  def notShared: DataSteps = newInstance(raw.filter(_.inTo[ObservableData].limit(2).count().is(P.lte(1))))

  override def newInstance(raw: GremlinScala[Vertex]): DataSteps = new DataSteps(raw)
}
