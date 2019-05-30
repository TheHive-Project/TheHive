package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.thehive.models._

@Singleton
class LogSrv @Inject()(attachmentSrv: AttachmentSrv)(implicit db: Database) extends VertexSrv[Log, LogSteps] {
  val taskLogSrv                                                                 = new EdgeSrv[TaskLog, Task, Log]
  val logAttachmentSrv                                                           = new EdgeSrv[LogAttachment, Log, Attachment]
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): LogSteps = new LogSteps(raw)

  def create(log: Log, task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Log with Entity = {
    val createdLog = create(log)
    taskLogSrv.create(TaskLog(), task, createdLog)
    createdLog
  }

  def addAttachment(log: Log with Entity, file: FFile)(implicit graph: Graph, authContext: AuthContext): Attachment with Entity = {
    val attachment = attachmentSrv.create(file)
    addAttachment(log, attachment)
    attachment
  }

  def addAttachment(
      log: Log with Entity,
      attachment: Attachment with Entity
  )(implicit graph: Graph, authContext: AuthContext): LogAttachment with Entity =
    logAttachmentSrv.create(LogAttachment(), log, attachment)
}

@EntitySteps[Log]
class LogSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Log, LogSteps](raw) {

  override def newInstance(raw: GremlinScala[Vertex]): LogSteps = new LogSteps(raw)

  def task = new TaskSteps(raw.in("TaskLog"))
}
