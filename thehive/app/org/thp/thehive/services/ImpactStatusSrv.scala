package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models.ImpactStatus

@Singleton
class ImpactStatusSrv @Inject()(implicit db: Database) extends VertexSrv[ImpactStatus, ImpactStatusSteps] {
  override val initialValues: Seq[ImpactStatus] = Seq(
    ImpactStatus("NoImpact"),
    ImpactStatus("WithImpact"),
    ImpactStatus("NotApplicable")
  )

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ImpactStatusSteps = new ImpactStatusSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ImpactStatusSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)
}

@EntitySteps[ImpactStatus]
class ImpactStatusSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[ImpactStatus](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): ImpactStatusSteps = new ImpactStatusSteps(newRaw)
  override def newInstance(): ImpactStatusSteps                             = new ImpactStatusSteps(raw.clone())

  def get(idOrName: String): ImpactStatusSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ImpactStatusSteps = new ImpactStatusSteps(raw.has(Key("value") of name))
}
