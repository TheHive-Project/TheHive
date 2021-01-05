package org.thp.thehive.services

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.models.{Observable, ObservableReportTag, ReportTag}
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.ReportTagOps._

import scala.util.Try

@Singleton
class ReportTagSrv @Inject() (observableSrv: ObservableSrv) extends VertexSrv[ReportTag] {
  val observableReportTagSrv = new EdgeSrv[ObservableReportTag, Observable, ReportTag]

  def updateTags(observable: Observable with Entity, origin: String, reportTags: Seq[ReportTag])(implicit
      graph: Graph,
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

object ReportTagOps {
  implicit class ReportTagOpsDefs(traversal: Traversal.V[ReportTag]) {
    def observable: Traversal.V[Observable] = traversal.in[ObservableReportTag].v[Observable]

    def fromOrigin(origin: String): Traversal.V[ReportTag] = traversal.has(_.origin, origin)
  }
}
