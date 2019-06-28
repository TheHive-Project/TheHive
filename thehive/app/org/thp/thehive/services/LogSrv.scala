package org.thp.thehive.services

import scala.collection.JavaConverters._
import scala.util.Try

import play.api.libs.json.Json

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class LogSrv @Inject()(attachmentSrv: AttachmentSrv, auditSrv: AuditSrv)(implicit db: Database) extends VertexSrv[Log, LogSteps] {
  val taskLogSrv                                                                 = new EdgeSrv[TaskLog, Task, Log]
  val logAttachmentSrv                                                           = new EdgeSrv[LogAttachment, Log, Attachment]
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): LogSteps = new LogSteps(raw)

  def create(log: Log, task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Log with Entity] = {
    val createdLog = create(log)
    taskLogSrv.create(TaskLog(), task, createdLog)
    auditSrv.createLog(createdLog, task).map(_ ⇒ createdLog)
  }

  def addAttachment(log: Log with Entity, file: FFile)(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] =
    attachmentSrv.create(file).flatMap(addAttachment(log, _))

  def addAttachment(
      log: Log with Entity,
      attachment: Attachment with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] = {
    logAttachmentSrv.create(LogAttachment(), log, attachment)
    auditSrv.updateLog(log, Json.obj("attachment" → attachment.name)).map(_ ⇒ attachment)
  }

  def cascadeRemove(log: Log with Entity)(implicit graph: Graph): Try[Unit] =
    for {
      _ ← Try(
        get(log)
          .attachments
          .toList()
          .foreach(a ⇒ attachmentSrv.get(a.attachmentId).remove())
      )
      r ← Try(get(log).remove())
    } yield r
}

@EntitySteps[Log]
class LogSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Log, LogSteps](raw) {

  def task = new TaskSteps(raw.in("TaskLog"))

  def attachments = new AttachmentSteps(raw.outTo[LogAttachment])

  def can(permission: Permission)(implicit authContext: AuthContext): LogSteps =
    newInstance(
      raw.filter(
        _.in("TaskLog")
          .in("ShareTask")
          .filter(_.out("ShareProfile").has(Key("permissions") of permission))
          .in("OrganisationShare")
          .in("RoleOrganisation")
          .filter(_.out("RoleProfile").has(Key("permissions") of permission))
          .in("UserRole")
          .has(Key("login") of authContext.userId)
      )
    )

  override def newInstance(raw: GremlinScala[Vertex]): LogSteps = new LogSteps(raw)

  def remove(): Unit = {
    raw.drop().iterate()
    ()
  }

  def remove(id: String): Unit = {
    raw.has(Key("_id") of id).drop().iterate()
    ()
  }

  def richLog: ScalarSteps[RichLog] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[LogAttachment].fold))
        )
        .map {
          case (log, attachments) ⇒
            RichLog(
              log.as[Log],
              attachments.asScala.map(_.as[Attachment])
            )
        }
    )
}
