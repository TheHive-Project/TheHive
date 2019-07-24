package org.thp.thehive.models

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models.{Entity, Model}
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json._

import scala.util.{Failure, Try}

@Singleton
class EntityHelper @Inject()(
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    alertSrv: AlertSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    schema: TheHiveSchema
) {

  lazy val logger = Logger(getClass)

  val writes: Writes[Entity] = {
    case t: Task       => Json.toJson(t)
    case c: Case       => Json.toJson(c)
    case a: Alert      => Json.toJson(a)
    case o: Observable => Json.toJson(o)
    case l: Log        => Json.toJson(l)
    case _             => throw new Exception("Entity writes missing")
  }

  /**
    * Gets an entity from name and id if manageable
    *
    * @param name entity name
    * @param id entity id
    * @param permission the permission to use for access right
    * @param graph graph db
    * @param authContext auth for permission check
    * @return
    */
  def get(name: String, id: String, permission: Permission)(implicit graph: Graph, authContext: AuthContext): Try[Entity] =
    for {
      _ <- fromName(name)
      entity <- name.trim.toLowerCase match {
        case "task"       => taskSrv.initSteps.get(id).visible.can(permission).getOrFail()
        case "case"       => caseSrv.initSteps.get(id).visible.can(permission).getOrFail()
        case "observable" => observableSrv.initSteps.get(id).visible.can(permission).getOrFail()
        case "log"        => logSrv.initSteps.get(id).visible.can(permission).getOrFail()
        case "alert"      => alertSrv.initSteps.get(id).visible.can(permission).getOrFail()
        case _            => matchError(name, id)
      }
    } yield entity

  /**
    * Tries to fetch the tlp and pap associated to the supplied entity
    *
    * @param name the entity name to retrieve
    * @param id the entity id
    * @param graph the necessary traversal graph
    * @param authContext the auth context for visibility check
    * @return
    */
  def threatLevels(name: String, id: String)(implicit graph: Graph, authContext: AuthContext): Try[(Int, Int)] =
    for {
      _ <- fromName(name)
      levels <- name.trim.toLowerCase match {
        case "task" => taskSrv.initSteps.get(id).visible.`case`.getOrFail().map(c => (c.tlp, c.pap))
        case "case" => caseSrv.initSteps.get(id).visible.getOrFail().map(c => (c.tlp, c.pap))
        case "observable" =>
          for {
            observable <- observableSrv.initSteps.get(id).visible.getOrFail()
            c          <- observableSrv.initSteps.get(id).`case`.getOrFail()
          } yield (observable.tlp, c.pap)
        case "log"   => logSrv.initSteps.get(id).visible.`case`.getOrFail().map(c => (c.tlp, c.pap))
        case "alert" => alertSrv.initSteps.get(id).visible.getOrFail().map(a => (a.tlp, a.pap))
        case _       => matchError(name, id)
      }
    } yield levels

  private def fromName(name: String): Try[Model] = Try(schema.modelList.find(_.label.toLowerCase == name.trim.toLowerCase).get)

  private def matchError(name: String, id: String) = {
    val m = s"Unmatched Entity from string $name - $id"
    logger.error(m)
    Failure(new Exception(m))
  }
}
