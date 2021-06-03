package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Database, Entity, Model, UMapping}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.Converter.Identity
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition._
import org.thp.scalligraph.{EntityId, EntityIdOrName}
import org.thp.thehive.models.{TaskStatus, _}
import play.api.libs.json.{JsNull, JsObject, Json}

import java.lang.{Boolean => JBoolean}
import java.util.{Date, Map => JMap}
import scala.util.{Failure, Success, Try}

class TaskSrv(
//    caseSrv: CaseSrv,
    auditSrv: AuditSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    shareSrv: ShareSrv,
    logSrv: LogSrv
) extends VertexSrv[Task]
    with TheHiveOpsNoDeps {

  val caseTemplateTaskSrv = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]
  val taskUserSrv         = new EdgeSrv[TaskUser, Task, User]
  val taskLogSrv          = new EdgeSrv[TaskLog, Task, Log]

  def create(task: Task, assignee: Option[User with Entity])(implicit graph: Graph, authContext: AuthContext): Try[RichTask] =
    for {
      createdTask <- createEntity(task.copy(assignee = assignee.map(_.login)))
      _           <- assignee.map(taskUserSrv.create(TaskUser(), createdTask, _)).flip
    } yield RichTask(createdTask)

  def unassign(task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(task).update(_.assignee, None).outE[TaskUser].remove()
    auditSrv.task.update(task, Json.obj("assignee" -> JsNull))
  }

  def remove(task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    get(task).caseTemplate.headOption match {
      case None => Try(get(task).remove())
      case Some(caseTemplate) =>
        auditSrv
          .caseTemplate
          .update(caseTemplate, JsObject.empty)
          .map(_ => get(task).remove())
    }

  override def delete(t: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    if (get(t).isShared.head)
      for {
        orga <- organisationSrv.getOrFail(authContext.organisation)
        _    <- shareSrv.unshareTask(t, orga)
      } yield ()
    else
      for {
        _ <- get(t).logs.toSeq.toTry(logSrv.delete(_))
        _ <- remove(t)
      } yield ()

  override def update(
      traversal: Traversal.V[Task],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Task], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (taskSteps, updatedFields) =>
        for {
          t <- taskSteps.clone().getOrFail("Task")
          _ <- auditSrv.task.update(t, updatedFields)
        } yield ()
    }

  /**
    * Tries to update the status of a task with related fields
    * according the status value if empty
    * @param task the task to update
    * @param status the status to set
    * @param graph db
    * @param authContext auth db
    * @return
    */
  def updateStatus(task: Task with Entity, status: TaskStatus.Value)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Task with Entity] = {
    def setStatus(): Traversal.V[Task] = get(task).update(_.status, status)

    status match {
      case TaskStatus.Cancel | TaskStatus.Waiting => setStatus().getOrFail("Task")
      case TaskStatus.Completed                   => setStatus().when(task.endDate.isEmpty)(_.update(_.endDate, Some(new Date()))).getOrFail("Task")
      case TaskStatus.InProgress =>
        setStatus()
          .when(task.startDate.isEmpty)(_.update(_.startDate, Some(new Date())))
          .getOrFail("Task")
          .when(task.assignee.isEmpty) { updatedTask =>
            for {
              t        <- updatedTask
              assignee <- userSrv.current.getOrFail("User")
              _        <- assign(t, assignee)
            } yield t
          }
      case _ => Failure(new Exception(s"Invalid TaskStatus $status for update"))
    }
  }

  def assign(task: Task with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(task).update(_.assignee, Some(user.login)).outE[TaskUser].remove()
    for {
      _ <- taskUserSrv.create(TaskUser(), task, user)
      _ <- auditSrv.task.update(task, Json.obj("assignee" -> user.login))
    } yield ()
  }

  def actionRequired(
      task: Task with Entity,
      organisation: Organisation with Entity,
      actionRequired: Boolean
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val details = Json.obj(s"actionRequired.${organisation.name}" -> actionRequired)
    auditSrv.task.update(task, details).map { _ =>
      organisationSrv
        .get(organisation)
        .out[OrganisationShare]
        .outE[ShareTask]
        .filter(_.inV.v[Task].hasId(task._id))
        .update(_.actionRequired, actionRequired)
        .iterate()
    }
  }
}

trait TaskOpsNoDeps { _: TheHiveOpsNoDeps =>
  implicit class TaskOpsDefs(traversal: Traversal.V[Task]) {

    def get(idOrName: EntityIdOrName): Traversal.V[Task] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.empty)

    def assignTo(login: String): Traversal.V[Task] = traversal.has(_.assignee, login)

    def relatedTo(caseId: EntityId): Traversal.V[Task] =
      traversal.has(_.relatedId, caseId)

    def inOrganisation(organisationId: EntityId): Traversal.V[Task] =
      traversal.has(_.organisationIds, organisationId)

    def active: Traversal.V[Task] = traversal.hasNot(_.status, TaskStatus.Cancel)

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Task] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.shares.filter(_.profile.has(_.permissions, permission)).organisation.current)
      else
        traversal.empty

    def inCase: Traversal.V[Task] = traversal.filter(_.inE[ShareTask])

    def `case`: Traversal.V[Case] = traversal.in[ShareTask].out[ShareCase].dedup.v[Case]

    def caseTemplate: Traversal.V[CaseTemplate] = traversal.in[CaseTemplateTask].v[CaseTemplate]

    def logs: Traversal.V[Log] = //traversal.out[TaskLog].v[Log]
      traversal.graph.V()(Model.vertex[Log]).has(_.taskId, P.within(traversal._id.toSeq: _*))

    def assignee: Traversal.V[User] = traversal.out[TaskUser].v[User]

    def unassigned: Traversal.V[Task] = traversal.filterNot(_.outE[TaskUser])

    def organisations: Traversal.V[Organisation] = traversal.in[ShareTask].in[OrganisationShare].v[Organisation]

    def organisations(permission: Permission): Traversal.V[Organisation] =
      shares.filter(_.profile.has(_.permissions, permission)).organisation

    def origin: Traversal.V[Organisation] = shares.has(_.owner, true).organisation

    def assignableUsers(implicit authContext: AuthContext): Traversal.V[User] =
      organisations(Permissions.manageTask)
        .visible
        .users(Permissions.manageTask)
        .dedup

    def isShared: Traversal[Boolean, Boolean, Identity[Boolean]] =
      traversal.choose(_.inE[ShareTask].count.is(P.gt(1)), true, false)

    def actionRequired(implicit authContext: AuthContext): Traversal[Boolean, JBoolean, Converter[Boolean, JBoolean]] =
      traversal.inE[ShareTask].filter(_.outV.v[Share].organisation.current).value(_.actionRequired)

    def actionRequiredMap(implicit
        authContext: AuthContext
    ): Traversal[(String, Boolean), JMap[String, Any], Converter[(String, Boolean), JMap[String, Any]]] =
      traversal
        .inE[ShareTask]
        .filter(_.outV.v[Share].organisation.visible)
        .project(
          _.by(_.outV.v[Share].organisation.value(_.name))
            .byValue(_.actionRequired)
        )

    def richTask: Traversal[RichTask, Vertex, Converter[RichTask, Vertex]] =
      traversal.identity.domainMap(RichTask) // FIXME add actionRequired ?

    def richTaskWithoutActionRequired: Traversal[RichTask, Vertex, Converter[RichTask, Vertex]] =
      traversal.identity.domainMap(RichTask)

    def richTaskWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Task] => Traversal[D, G, C]
    ): Traversal[(RichTask, D), JMap[String, Any], Converter[(RichTask, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(entityRenderer)
        )
        .domainMap {
          case (task, renderedEntity) =>
            RichTask(task) -> renderedEntity
        }

    def shares: Traversal.V[Share] = traversal.in[ShareTask].v[Share]

    def share(implicit authContext: AuthContext): Traversal.V[Share] = share(authContext.organisation)

    def share(organisation: EntityIdOrName): Traversal.V[Share] =
      traversal.in[ShareTask].filter(_.in[OrganisationShare].v[Organisation].get(organisation)).v[Share]
  }
}

trait TaskOps { _: TheHiveOpsNoDeps =>
  protected val organisationSrv: OrganisationSrv
  implicit class TaskOpsNoDepsDefs(traversal: Traversal.V[Task]) {
    def visible(implicit authContext: AuthContext): Traversal.V[Task] =
      traversal.has(_.organisationIds, organisationSrv.currentId(traversal.graph, authContext))
  }
}

class TaskIntegrityCheckOps(val db: Database, val service: TaskSrv, organisationSrv: OrganisationSrv)
    extends IntegrityCheckOps[Task]
    with TheHiveOpsNoDeps {
  override def resolve(entities: Seq[Task with Entity])(implicit graph: Graph): Try[Unit] = Success(())

  override def globalCheck(): Map[String, Int] =
    db.tryTransaction { implicit graph =>
      Try {
        service
          .startTraversal
          .project(
            _.by
              .by(_.unionFlat(_.`case`._id, _.caseTemplate._id).fold)
              .by(_.unionFlat(_.organisations._id, _.caseTemplate.organisation._id).fold)
          )
          .toIterator
          .map {
            case (task, relatedIds, organisationIds) =>
              val orgStats = multiIdLink[Organisation]("organisationIds", organisationSrv)(_.remove)
                .check(task, task.organisationIds, organisationIds)

              val removeOrphan: OrphanStrategy[Task, EntityId] = { (a, entity) =>
                service.get(entity).remove()
                Map("Task-relatedId-removeOrphan" -> 1)
              }

              val relatedStats = new SingleLinkChecker[Product, EntityId, EntityId](
                orphanStrategy = removeOrphan,
                setField = (entity, link) => UMapping.entityId.setProperty(service.get(entity), "relatedId", link._id).iterate(),
                entitySelector = _ => EntitySelector.firstCreatedEntity,
                removeLink = (_, _) => (),
                getLink = id => graph.VV(id).entity.head,
                Some(_)
              ).check(task, task.relatedId, relatedIds)

              orgStats <+> relatedStats
          }
          .reduceOption(_ <+> _)
          .getOrElse(Map.empty)
      }
    }.getOrElse(Map("globalFailure" -> 1))
}
