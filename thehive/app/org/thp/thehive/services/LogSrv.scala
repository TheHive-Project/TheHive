package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.Log

@Singleton
class LogSrv @Inject()(implicit db: Database) extends VertexSrv[Log, LogSteps] {
  override def steps(raw: GremlinScala[Vertex]): LogSteps = new LogSteps(raw)
}

@EntitySteps[Log]
class LogSteps(raw: GremlinScala[Vertex])(implicit db: Database) extends BaseVertexSteps[Log, LogSteps](raw) {

  override def newInstance(raw: GremlinScala[Vertex]): LogSteps = new LogSteps(raw)

  val task = new TaskSteps(raw.in("TaskLog"))
}
