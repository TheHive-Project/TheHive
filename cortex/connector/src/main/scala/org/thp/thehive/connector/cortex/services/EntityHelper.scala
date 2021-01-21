package org.thp.thehive.connector.cortex.services

import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{BadRequestError, EntityIdOrName}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services._
import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Try}

@Singleton
class EntityHelper @Inject() (
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    alertSrv: AlertSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    organisationSrv: OrganisationSrv
) {

  lazy val logger: Logger = Logger(getClass)

  /**
    * Gets an entity from name and id if manageable
    *
    * @param objectType entity name
    * @param objectId entity id
    * @param permission the permission to use for access right
    * @param graph graph db
    * @param authContext auth for permission check
    * @return
    */
  def get(objectType: String, objectId: EntityIdOrName, permission: Permission)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Product with Entity] =
    objectType match {
      case "Task"       => taskSrv.get(objectId).can(permission).getOrFail("Task")
      case "Case"       => caseSrv.get(objectId).can(permission).getOrFail("Case")
      case "Observable" => observableSrv.get(objectId).can(permission).getOrFail("Observable")
      case "Log"        => logSrv.get(objectId).can(permission).getOrFail("Log")
      case "Alert"      => alertSrv.get(objectId).can(organisationSrv, permission).getOrFail("Alert")
      case _            => Failure(BadRequestError(s"objectType $objectType is not recognised"))
    }

  /**
    * Retrieves an optional parent Case
    * @param entity the child entity
    * @param graph db traversal
    * @return
    */
  def parentCase(entity: Entity)(implicit graph: Graph): Option[Case with Entity] =
    entity._label match {
      case "Task"  => taskSrv.get(entity).`case`.headOption
      case "Case"  => caseSrv.get(entity).headOption
      case "Log"   => logSrv.get(entity).`case`.headOption
      case "Alert" => None
      case _       => None
    }

  /**
    * Retrieves an optional parent Task
    * @param entity the child entity
    * @param graph db traversal
    * @return
    */
  def parentTask(entity: Entity)(implicit graph: Graph): Option[Task with Entity] =
    entity._label match {
      case "Log" => logSrv.get(entity).task.headOption
      case _     => None
    }

  /**
    * Tries to fetch the tlp and pap associated to the supplied entity
    *
    * @param entity the entity to retrieve
    * @param graph the necessary traversal graph
    * @param authContext the auth context for visibility check
    * @return
    */
  def entityInfo(entity: Entity)(implicit graph: Graph, authContext: AuthContext): Try[(String, Int, Int)] =
    entity match {
      case t: Task => taskSrv.get(t).visible(organisationSrv).`case`.getOrFail("Case").map(c => (s"${t.title} (${t.status})", c.tlp, c.pap))
      case c: Case => caseSrv.get(c).visible(organisationSrv).getOrFail("Case").map(c => (s"#${c.number} ${c.title}", c.tlp, c.pap))
      case l: Log  => logSrv.get(l).visible(organisationSrv).`case`.getOrFail("Case").map(c => (s"${l.message} from ${l._createdBy}", c.tlp, c.pap))
      case a: Alert =>
        alertSrv.get(a).visible(organisationSrv).getOrFail("Alert").map(a => (s"[${a.source}:${a.sourceRef}] ${a.title}", a.tlp, a.pap))
      case o: Observable =>
        for {
          ro <- observableSrv.get(o).visible(organisationSrv).richObservable.getOrFail("Observable")
          c  <- observableSrv.get(o).`case`.getOrFail("Case")
        } yield (s"[${ro.dataType}] ${ro.data.getOrElse("<no data>")}", ro.tlp, c.pap) // TODO add attachment info
    }
}
