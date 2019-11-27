package org.thp.thehive.services

import scala.util.Try

import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.services.RichVertexGremlinScala
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps

import org.thp.thehive.models.{Observable, ObservableReportTag, ReportTag}

@Singleton
class ReportTagSrv @Inject()(observableSrv: ObservableSrv)(implicit db: Database) extends VertexSrv[ReportTag, ReportTagSteps] {
  val observableReportTagSrv = new EdgeSrv[ObservableReportTag, Observable, ReportTag]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ReportTagSteps = new ReportTagSteps(raw)

  def updateTags(observable: Observable with Entity, origin: String, reportTags: Seq[ReportTag])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    observableSrv.get(observable).reportTags.fromOrigin(origin).remove()
    reportTags
      .toTry { tag =>
        createEntity(tag).flatMap(t => observableReportTagSrv.create(ObservableReportTag(), observable, t))
      }
      .map(_ => ())
  }
}

class ReportTagSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[ReportTag](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): VertexSteps[ReportTag] = new ReportTagSteps(raw)

  def observable: ObservableSteps = new ObservableSteps(raw.inTo[ObservableReportTag])

  def fromOrigin(origin: String): ReportTagSteps = this.has("origin", origin)
}
