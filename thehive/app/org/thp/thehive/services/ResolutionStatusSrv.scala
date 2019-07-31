package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services.VertexSrv
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

  override def get(id: String)(implicit graph: Graph): ResolutionStatusSteps =
    if (db.isValidId(id)) super.get(id)
    else initSteps.getByName(id)
}

class ResolutionStatusSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[ResolutionStatus, ResolutionStatusSteps](raw) {

  override def newInstance(raw: GremlinScala[Vertex]): ResolutionStatusSteps = new ResolutionStatusSteps(raw)

  override def get(id: String): ResolutionStatusSteps =
    if (db.isValidId(id)) getById(id)
    else getByName(id)

  def getById(id: String): ResolutionStatusSteps = new ResolutionStatusSteps(raw.hasId(id))

  def getByName(name: String): ResolutionStatusSteps = new ResolutionStatusSteps(raw.has(Key("value") of name))

}
