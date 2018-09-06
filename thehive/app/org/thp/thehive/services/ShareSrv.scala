package org.thp.thehive.services

import java.util.{ List => JList }

import gremlin.scala._
import javax.inject.{ Inject, Singleton }
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class ShareSrv @Inject()(implicit val db: Database) extends VertexSrv[Share, ShareSteps] {

  val shareOrganisationSrv = new EdgeSrv[ShareOrganisation, Share, Organisation]
  val shareCaseSrv = new EdgeSrv[ShareCase, Share, Case]

  override def steps(raw: GremlinScala[Vertex]): ShareSteps = new ShareSteps(raw)

  def create(`case`: Case with Entity, organisation: Organisation with Entity)(implicit graph: Graph, authContext: AuthContext): RichShare = {
    val createdShare = create(Share())
    shareOrganisationSrv.create(ShareOrganisation(), createdShare, organisation)
    shareCaseSrv.create(ShareCase(), createdShare, `case`)
    RichShare(`case`, organisation)
  }
}

@EntitySteps[Share]
class ShareSteps(raw: GremlinScala[Vertex])(implicit db: Database) extends BaseVertexSteps[Share, ShareSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ShareSteps = new ShareSteps(raw)

  def richShare: GremlinScala[RichShare] =
  raw
    .project[Any]("share", "case", "organisation")
    .by()
    .by(__[Vertex].outTo[ShareCase].values[String]("_id").fold.traversal)
    .by(__[Vertex].outTo[ShareOrganisation].values[String]("name").fold.traversal)
    .map {
      case ValueMap(m) ⇒
        RichShare(
          onlyOneOf[String](m.get[JList[String]]("case")),
          onlyOneOf[String](m.get[JList[String]]("organisation"))
        )
    }

  def organisation: OrganisationSteps = new OrganisationSteps(raw.outTo[ShareOrganisation])

  def `case`: CaseSteps = new CaseSteps(raw.outTo[ShareCase])
}
