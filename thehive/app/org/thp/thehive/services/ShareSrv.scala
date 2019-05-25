package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class ShareSrv @Inject()(implicit val db: Database) extends VertexSrv[Share, ShareSteps] {

  val organisationShareSrv = new EdgeSrv[OrganisationShare, Organisation, Share]
  val shareProfileSrv      = new EdgeSrv[ShareProfile, Share, Profile]
  val shareCaseSrv         = new EdgeSrv[ShareCase, Share, Case]
  val shareTaskSrv         = new EdgeSrv[ShareTask, Share, Task]
  val shareObservableSrv   = new EdgeSrv[ShareObservable, Share, Observable]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ShareSteps = new ShareSteps(raw)

  def create(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Share = {
    val createdShare = create(Share())
    organisationShareSrv.create(OrganisationShare(), organisation, createdShare)
    shareCaseSrv.create(ShareCase(), createdShare, `case`)
    shareProfileSrv.create(ShareProfile(), createdShare, profile)
    createdShare
  }
}

@EntitySteps[Share]
class ShareSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Share, ShareSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ShareSteps = new ShareSteps(raw)

//  def richShare: GremlinScala[RichShare] =
//    raw
//      .project(
//        _.apply(By(__[Vertex].outTo[ShareCase].value[String]("number")))
//          .and(By(__[Vertex].inTo[OrganisationShare]))
//          .and(By(__[Vertex].outTo[ShareProfile])))
//      .map {
//        case (caze, organisation, profile) ⇒
//          RichShare(caze.as[Case], organisation, profile)
////            onlyOneOf[String](m.get[JList[String]]("case")),
////            onlyOneOf[String](m.get[JList[String]]("organisation"))
////          )
//        //def apply(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity): RichShare =
//      }

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[OrganisationShare])

  def `case`: CaseSteps = new CaseSteps(raw.outTo[ShareCase])
}
