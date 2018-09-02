package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, PrivateField}
import org.thp.thehive.models.{Task, TaskLog, TaskUser}

@Singleton
class TaskSrv @Inject()(implicit db: Database) extends VertexSrv[Task] {
  override def steps(implicit graph: Graph): TaskSteps = new TaskSteps(graph.V.hasLabel(model.label))
}

@EntitySteps[Task]
class TaskSteps(raw: GremlinScala[Vertex])(implicit db: Database) extends BaseVertexSteps[Task, TaskSteps](raw) {
  @PrivateField override def newInstance(raw: GremlinScala[Vertex]): TaskSteps = new TaskSteps(raw)

  def logs  = new LogSteps(raw.outTo[TaskLog])
  def owner = new UserSteps(raw.outTo[TaskUser])
}
