package org.thp.thehive.connector.cortex.services

import gremlin.scala.{Graph, Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger

import scala.util.{Failure, Try}

@Singleton
class EntityHelper @Inject()(
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    alertSrv: AlertSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    db: Database,
    schema: TheHiveSchema,
    userSrv: UserSrv
) {

  lazy val logger = Logger(getClass)

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
  def get(objectType: String, objectId: String, permission: Permission)(implicit graph: Graph, authContext: AuthContext): Try[Entity] =
    objectType match {
      case "Task"       => taskSrv.getByIds(objectId).can(permission).getOrFail()
      case "Case"       => caseSrv.get(objectId).can(permission).getOrFail()
      case "Observable" => observableSrv.getByIds(objectId).can(permission).getOrFail()
      case "Log"        => logSrv.getByIds(objectId).can(permission).getOrFail()
      case "Alert"      => alertSrv.getByIds(objectId).can(permission).getOrFail()
      case _            => Failure(BadRequestError(s"objectType $objectType is not recognised"))
    }

  /**
    * Retrieves an optional parent Case
    * @param entity the child entity
    * @param graph db traversal
    * @return
    */
  def parentCase(entity: Entity)(implicit graph: Graph): Option[Case with Entity] = entity match {
    case t: Task  => taskSrv.get(t).`case`.headOption()
    case c: Case  => Some(c)
    case l: Log   => logSrv.get(l).`case`.headOption()
    case _: Alert => None
    case _        => None
  }

  /**
    * Retrieves an optional parent Case
    * @param entity the child entity
    * @param graph db traversal
    * @return
    */
  def parentTask(entity: Entity)(implicit graph: Graph): Option[Task with Entity] = entity match {
    case l: Log => logSrv.get(l).task.headOption()
    case _      => None
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
      case t: Task  => taskSrv.get(t).visible.`case`.getOrFail().map(c => (s"${t.title} (${t.status}", c.tlp, c.pap))
      case c: Case  => caseSrv.get(c).visible.getOrFail().map(c => (s"#${c.number} ${c.title}", c.tlp, c.pap))
      case l: Log   => logSrv.get(l).visible.`case`.getOrFail().map(c => (s"${l.message} from ${l._createdBy}", c.tlp, c.pap))
      case a: Alert => alertSrv.get(a).visible.getOrFail().map(a => (s"[${a.source}:${a.sourceRef}] ${a.title}", a.tlp, a.pap))
      case o: Observable =>
        for {
          ro <- observableSrv.get(o).visible.richObservable.getOrFail()
          c  <- observableSrv.get(o).`case`.getOrFail()
        } yield (s"[${ro.`type`}] ${ro.data.map(_.data)}", ro.tlp, c.pap) // TODO add attachment info
    }

  /**
    * Returns the filtered organisations according to the supplied lists (mainly conf based
    *
    * @param organisationSteps the organisation steps graph instance
    * @param includedTheHiveOrganisations the allowed organisation
    * @param excludedTheHiveOrganisations the excluded ones
    * @return
    */
  def organisationFilter(
      organisationSteps: OrganisationSteps,
      includedTheHiveOrganisations: Seq[String],
      excludedTheHiveOrganisations: Seq[String]
  ): OrganisationSteps = {
    val includedOrgs =
      if (includedTheHiveOrganisations.contains("*") || includedTheHiveOrganisations.isEmpty) organisationSteps
      else organisationSteps.has(Key[String]("name"), P.within(includedTheHiveOrganisations))
    if (excludedTheHiveOrganisations.isEmpty) includedOrgs
    else includedOrgs.has(Key[String]("name"), P.without(excludedTheHiveOrganisations))
  }
}
