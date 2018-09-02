package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.{EntitySteps, PrivateField}
import org.thp.thehive.models.Log

@Singleton
class LogSrv @Inject()(implicit db: Database) extends VertexSrv[Log] {
  override def steps(implicit graph: Graph): LogSteps = new LogSteps(graph.V.hasLabel(model.label))
}

@EntitySteps[Log]
class LogSteps(raw: GremlinScala[Vertex])(implicit db: Database) extends BaseVertexSteps[Log, LogSteps](raw) {

  @PrivateField override def newInstance(raw: GremlinScala[Vertex]): LogSteps = new LogSteps(raw)

  val task = new TaskSteps(raw.in("TaskLog"))
}
