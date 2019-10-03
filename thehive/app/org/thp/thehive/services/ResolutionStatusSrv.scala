package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models.ResolutionStatus

@Singleton
class ResolutionStatusSrv @Inject()(implicit db: Database) extends VertexSrv[ResolutionStatus, ResolutionStatusSteps] {
  override val initialValues = Seq(
    ResolutionStatus("Indeterminate"),
    ResolutionStatus("FalsePositive"),
    ResolutionStatus("TruePositive"),
    ResolutionStatus("Other"),
    ResolutionStatus("Duplicated")
  )
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ResolutionStatusSteps = new ResolutionStatusSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ResolutionStatusSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)
}

@EntitySteps[ResolutionStatus]
class ResolutionStatusSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[ResolutionStatus](raw) {

  override def newInstance(newRaw: GremlinScala[Vertex]): ResolutionStatusSteps = new ResolutionStatusSteps(newRaw)
  override def newInstance(): ResolutionStatusSteps                             = new ResolutionStatusSteps(raw.clone())

  def get(idOrName: String): ResolutionStatusSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ResolutionStatusSteps = new ResolutionStatusSteps(raw.has(Key("value") of name))

}
