package org.thp.thehive.services

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json.JsObject

import java.util
import javax.inject.{Inject, Singleton}
import scala.util.{Success, Try}

@Singleton
class LogSrv @Inject() (attachmentSrv: AttachmentSrv, auditSrv: AuditSrv, taskSrv: TaskSrv) extends VertexSrv[Log] {
  val taskLogSrv       = new EdgeSrv[TaskLog, Task, Log]
  val logAttachmentSrv = new EdgeSrv[LogAttachment, Log, Attachment]

  def create(log: Log, task: Task with Entity, file: Option[FFile])(implicit graph: Graph, authContext: AuthContext): Try[RichLog] =
    for {
      createdLog <- createEntity(log.copy(taskId = task._id, organisationIds = task.organisationIds))
      _          <- taskLogSrv.create(TaskLog(), task, createdLog)
      _          <- if (task.status == TaskStatus.Waiting) taskSrv.updateStatus(task, TaskStatus.InProgress) else Success(())
      attachment <- file.map(attachmentSrv.create).flip
      _          <- attachment.map(logAttachmentSrv.create(LogAttachment(), createdLog, _)).flip
      richLog = RichLog(createdLog, Nil)
      _ <- auditSrv.log.create(createdLog, task, richLog.toJson)
    } yield richLog

  override def delete(log: Log with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    get(log).attachments.toSeq.toTry(attachmentSrv.delete(_)).map { _ =>
      get(log).task.headOption.foreach(task => auditSrv.log.delete(log, task))
      get(log).remove()
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
      idOrName.fold(traversal.getByIds(_), _ => traversal.empty)

    def organisations: Traversal.V[Organisation] =
      task.organisations

    def visible(organisationSrv: OrganisationSrv)(implicit authContext: AuthContext): Traversal.V[Log] =
      traversal.has(_.organisationIds, organisationSrv.currentId(traversal.graph, authContext))

    def attachments: Traversal.V[Attachment] = traversal.out[LogAttachment].v[Attachment]

    def `case`: Traversal.V[Case] = task.`case`

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Log] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.task.can(permission))
      else
        traversal.empty

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

class LogIntegrityCheckOps @Inject() (val db: Database, val service: LogSrv, taskSrv: TaskSrv) extends IntegrityCheckOps[Log] {
  override def resolve(entities: Seq[Log with Entity])(implicit graph: Graph): Try[Unit] = Success(())

  override def globalCheck(): Map[String, Long] = {
    implicit val authContext: AuthContext = LocalUserSrv.getSystemAuthContext

    db.tryTransaction { implicit graph =>
      Try {
        service
          .startTraversal
          .project(_.by.by(_.task.fold))
          .toIterator
          .flatMap {
            case (log, tasks) =>
              val (extraLinks, extraTasks) = tasks.partition(_._id == log.taskId)
              if (extraLinks.nonEmpty)
                (if (extraLinks.length == 1) Nil
                 else {
                   service.get(log).inE[TaskLog].flatMap(_.range(1, 100)).remove()
                   Seq("extraTaskLink")
                 }) ++
                  (if (extraTasks.isEmpty) Nil
                   else {
                     service.get(log).inE[TaskLog].filterNot(_.outV.hasId(log.taskId)).remove()
                     Seq("extraTask")
                   }) ++
                  (if (log.organisationIds != extraLinks.head.organisationIds) {
                     service.get(log).update(_.organisationIds, extraLinks.head.organisationIds).iterate()
                     Seq("invalidOrganisationIds")
                   } else Nil)
              else if (extraTasks.nonEmpty)
                if (extraTasks.size == 1) {
                  service.get(log).update(_.taskId, extraTasks.head._id).update(_.organisationIds, extraTasks.head.organisationIds).iterate()
                  Seq("invalidTaskId")
                } else {
                  service.get(log).remove()
                  Seq("incoherent")
                }
              else {
                taskSrv.getOrFail(log.taskId) match {
                  case Success(task) =>
                    service
                      .taskLogSrv
                      .create(TaskLog(), task, log)
                    service.get(log).update(_.organisationIds, task.organisationIds).iterate()
                    Seq("taskMissing")
                  case _ => Seq("nonExistentTask")
                }
                service.get(log).remove()
                Seq("incoherent")
              }
          }
          .toSeq
      }
    }.getOrElse(Seq("globalFailure"))
      .groupBy(identity)
      .mapValues(_.size.toLong)
  }
}
