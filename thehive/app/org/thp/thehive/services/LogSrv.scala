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

  def create(log: Log, task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Log with Entity] =
    for {
      createdLog <- create(log)
      _          <- taskLogSrv.create(TaskLog(), task, createdLog)
      case0      <- get(createdLog).`case`.getOrFail()
      _          <- auditSrv.log.create(createdLog, case0)
    } yield createdLog

  def addAttachment(log: Log with Entity, file: FFile)(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] =
    for {
      case0      <- get(log).`case`.getOrFail()
      attachment <- attachmentSrv.create(file)
      _          <- addAttachment(log, attachment)
      _          <- auditSrv.log.update(log, case0, Json.obj("attachment" -> attachment.name))
    } yield attachment

  def addAttachment(
      log: Log with Entity,
      attachment: Attachment with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] =
    for {
      _     <- logAttachmentSrv.create(LogAttachment(), log, attachment)
      case0 <- get(log).`case`.getOrFail()
      _     <- auditSrv.log.update(log, case0, Json.obj("attachment" -> attachment.name))
    } yield attachment

  def cascadeRemove(log: Log with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      _     <- get(log).attachments.toIterator.toTry(attachmentSrv.cascadeRemove(_))
      case0 <- get(log).`case`.getOrFail()
      _ = get(log).remove()
      _ <- auditSrv.log.delete(log, Some(case0))
    } yield ()
}

@EntitySteps[Log]
class LogSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Log, LogSteps](raw) {

  def task = new TaskSteps(raw.in("TaskLog"))

  def visible(implicit authContext: AuthContext): LogSteps =
    newInstance(
      raw.filter(
        _.inTo[TaskLog]
          .inTo[ShareTask]
          .inTo[OrganisationShare]
          .has(Key("name") of authContext.organisation)
      )
    )

  def attachments = new AttachmentSteps(raw.outTo[LogAttachment])

  def `case` = new CaseSteps(
    raw
      .inTo[TaskLog]
      .inTo[ShareTask]
      .outTo[ShareCase]
  )

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

  def richLog: ScalarSteps[RichLog] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[LogAttachment].fold))
        )
        .map {
          case (log, attachments) =>
            RichLog(
              log.as[Log],
              attachments.asScala.map(_.as[Attachment]).toSeq
            )
        }
    )
}
