package org.thp.thehive.models

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json._

import scala.util.{Failure, Try}

@Singleton
class EntityHelper @Inject()(taskSrv: TaskSrv, caseSrv: CaseSrv, alertSrv: AlertSrv, observableSrv: ObservableSrv, logSrv: LogSrv) {

  lazy val logger = Logger(getClass)

  val writes: Writes[Entity] = {
    case t: Task => Json.toJson(t)
    case _       => ???
  }

  /**
    * Tries to fetch the tlp and pap associated to the supplied entity
    *
    * @param name the entity name to retrieve
    * @param id the entity id
    * @param graph the necessary traversal graph
    * @param authContext the auth context for visibility check
    * @return
    */
  def threatLevels(name: String, id: String)(implicit graph: Graph, authContext: AuthContext): Try[(Int, Int)] = name.trim.toLowerCase match {
    case "task" => taskSrv.initSteps.get(id).visible.`case`.getOrFail().map(c => (c.tlp, c.pap))
    case "case" => caseSrv.initSteps.get(id).visible.getOrFail().map(c => (c.tlp, c.pap))
    case "observable" =>
      for {
        observable <- observableSrv.initSteps.get(id).visible.getOrFail()
        c          <- observableSrv.initSteps.get(id).`case`.getOrFail()
      } yield (observable.tlp, c.pap)
    case "log"   => logSrv.initSteps.get(id).visible.`case`.getOrFail().map(c => (c.tlp, c.pap))
    case "alert" => alertSrv.initSteps.get(id).visible.getOrFail().map(a => (a.tlp, a.pap))
    case _ =>
      val m = s"Unmatched Entity from string $name - $id"
      logger.error(m)
      Failure(new Exception(m))
  }
}
