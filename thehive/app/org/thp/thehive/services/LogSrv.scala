package org.thp.thehive.services

import java.util
import scala.util.Success
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json.{JsObject, Json}

import scala.util.Try

@Singleton
class LogSrv @Inject() (attachmentSrv: AttachmentSrv, auditSrv: AuditSrv, taskSrv: TaskSrv, userSrv: UserSrv)(implicit
    @Named("with-thehive-schema") db: Database
) extends VertexSrv[Log] {
  val taskLogSrv       = new EdgeSrv[TaskLog, Task, Log]
  val logAttachmentSrv = new EdgeSrv[LogAttachment, Log, Attachment]

  def create(log: Log, task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Log with Entity] =
    for {
      createdLog <- createEntity(log)
      _          <- taskLogSrv.create(TaskLog(), task, createdLog)
      user       <- userSrv.current.getOrFail("User") // user is used only if task status is waiting but the code is cleaner
      _          <- if (task.status == TaskStatus.Waiting) taskSrv.updateStatus(task, user, TaskStatus.InProgress) else Success(())
      _          <- auditSrv.log.create(createdLog, task, RichLog(createdLog, Nil).toJson)
    } yield createdLog

  def addAttachment(log: Log with Entity, file: FFile)(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] =
    for {
      task       <- get(log).task.getOrFail("Task")
      attachment <- attachmentSrv.create(file)
      _          <- addAttachment(log, attachment)
      _          <- auditSrv.log.update(log, task, Json.obj("attachment" -> attachment.name))
    } yield attachment

  def addAttachment(
      log: Log with Entity,
      attachment: Attachment with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] =
    for {
      _    <- logAttachmentSrv.create(LogAttachment(), log, attachment)
      task <- get(log).task.getOrFail("Task")
      _    <- auditSrv.log.update(log, task, Json.obj("attachment" -> attachment.name))
    } yield attachment

  def cascadeRemove(log: Log with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(log).attachments.toIterator.foreach(attachmentSrv.cascadeRemove(_))
    for {
      task <- get(log).task.getOrFail("Task")
      _    <- auditSrv.log.delete(log, Some(task))
    } yield get(log).remove()
  }

  override def update(
      traversal: Traversal.V[Log],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Log], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (logSteps, updatedFields) =>
        logSteps.clone().project(_.by.by(_.task)).getOrFail("Log").flatMap {
          case (log, task) => auditSrv.log.update(log, task, updatedFields)
        }
    }
}

object LogOps {

  implicit class LogOpsDefs(traversal: Traversal.V[Log]) {
    def task: Traversal.V[Task] = traversal.in("TaskLog").v[Task]

    def get(idOrName: EntityIdOrName): Traversal.V[Log] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.limit(0))

    def visible(implicit authContext: AuthContext): Traversal.V[Log] =
      traversal.filter(_.task.visible)

    def attachments: Traversal.V[Attachment] = traversal.out[LogAttachment].v[Attachment]

    def `case`: Traversal.V[Case] = task.`case`

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Log] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.task.can(permission))
      else
        traversal.limit(0)

    def richLog: Traversal[RichLog, util.Map[String, Any], Converter[RichLog, util.Map[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.attachments.fold)
        )
        .domainMap {
          case (log, attachments) =>
            RichLog(
              log,
              attachments
            )
        }

    def richLogWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Log] => Traversal[D, G, C]
    ): Traversal[(RichLog, D), util.Map[String, Any], Converter[(RichLog, D), util.Map[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.attachments.fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (log, attachments, renderedEntity) =>
            RichLog(
              log,
              attachments
            ) -> renderedEntity
        }
  }
}
