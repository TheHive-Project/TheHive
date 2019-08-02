package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

import scala.util.Try

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
  ): Try[Share] =
    for {
      createdShare <- create(Share())
      _            <- organisationShareSrv.create(OrganisationShare(), organisation, createdShare)
      _            <- shareCaseSrv.create(ShareCase(), createdShare, `case`)
      _            <- shareProfileSrv.create(ShareProfile(), createdShare, profile)
    } yield createdShare
}

@EntitySteps[Share]
class ShareSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Share, ShareSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ShareSteps = new ShareSteps(raw)

//  def visible(implicit authContext: AuthContext): ShareSteps =
//    newInstance(raw.filter(_.inTo[OrganisationShare].inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId)))

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[OrganisationShare])

  def tasks = new TaskSteps(raw.outTo[ShareTask])

  def observables = new ObservableSteps(raw.outTo[ShareObservable])

  def `case`: CaseSteps = new CaseSteps(raw.outTo[ShareCase])

  def remove(): Unit = {
    raw.drop().iterate()
    ()
  }
}
