package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models.ResolutionStatus

import scala.util.Try

object ResolutionStatusSrv {
  val indeterminate = ResolutionStatus("Indeterminate")
  val falsePositive = ResolutionStatus("FalsePositive")
  val truePositive  = ResolutionStatus("TruePositive")
  val other         = ResolutionStatus("Other")
  val duplicated    = ResolutionStatus("Duplicated")
}

@Singleton
class ResolutionStatusSrv @Inject()(implicit db: Database) extends VertexSrv[ResolutionStatus, ResolutionStatusSteps] {
  override val initialValues = Seq(
    ResolutionStatusSrv.indeterminate,
    ResolutionStatusSrv.falsePositive,
    ResolutionStatusSrv.truePositive,
    ResolutionStatusSrv.other,
    ResolutionStatusSrv.duplicated
  )
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ResolutionStatusSteps = new ResolutionStatusSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ResolutionStatusSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  def create(resolutionStatus: ResolutionStatus)(implicit graph: Graph, authContext: AuthContext): Try[ResolutionStatus with Entity] =
    createEntity(resolutionStatus)
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
