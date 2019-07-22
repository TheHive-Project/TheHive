package org.thp.thehive.models

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json._

import scala.util.Failure

@Singleton
class EntityHelper @Inject()(taskSrv: TaskSrv, caseSrv: CaseSrv, alertSrv: AlertSrv, observableSrv: ObservableSrv, logSrv: LogSrv) {

  lazy val logger = Logger(getClass)

  val writes: Writes[Entity] = {
    case t: Task => Json.toJson(t)
    case _       => ???
  }

  def threatLevels(name: String, id: String)(implicit graph: Graph) = name.trim.toLowerCase match {
    case "task" => taskSrv.initSteps.get(id).`case`.getOrFail().map(c => (c.tlp, c.pap))
    case "case" => caseSrv.initSteps.get(id).getOrFail().map(c => (c.tlp, c.pap))
    case "observable" =>
      for {
        observable <- observableSrv.initSteps.get(id).getOrFail()
        c          <- observableSrv.initSteps.get(id).`case`.getOrFail()
      } yield (observable.tlp, c.pap)
    case "log"   => logSrv.initSteps.get(id).`case`.getOrFail().map(c => (c.tlp, c.pap))
    case "alert" => alertSrv.initSteps.get(id).observables
    case _ =>
      val m = s"Unmatched Entity from string $name - $id"
      logger.error(m)
      Failure(new Exception(m))
  }
}
