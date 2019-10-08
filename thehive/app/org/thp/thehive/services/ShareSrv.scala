package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.models._
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}

@Singleton
class ShareSrv @Inject()(implicit val db: Database, auditSrv: AuditSrv) extends VertexSrv[Share, ShareSteps] {

  val organisationShareSrv = new EdgeSrv[OrganisationShare, Organisation, Share]
  val shareProfileSrv      = new EdgeSrv[ShareProfile, Share, Profile]
  val shareCaseSrv         = new EdgeSrv[ShareCase, Share, Case]
  val shareTaskSrv         = new EdgeSrv[ShareTask, Share, Task]
  val shareObservableSrv   = new EdgeSrv[ShareObservable, Share, Observable]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ShareSteps = new ShareSteps(raw)

  /**
    * Shares a case (creates a share entity) for a precise organisation
    * according to the given profile.
    * If share already exists for another profile, removes
    * the previous one and create a new one
    *
    * @param `case` the case to share
    * @param organisation the organisation to share for
    * @param profile the related share profile
    * @return
    */
  def shareCase(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Share with Entity] =
    if (get(`case`, organisation).profile.exists()) {
      get(`case`, organisation).profile.getOrFail().flatMap { existingProfile =>
        if (existingProfile.name != profile.name) for {
          share <- get(`case`, organisation).getOrFail()
          _     <- updateProfile(share, existingProfile, profile)
          _     <- auditSrv.share.update(share, `case`, Json.obj("profile" -> profile.name, "organisation" -> organisation.name, "case" -> `case`._id))
        } yield share
        else get(`case`, organisation).getOrFail()
      }
    } else {
      for {
        createdShare <- createEntity(Share())
        _            <- organisationShareSrv.create(OrganisationShare(), organisation, createdShare)
        _            <- shareCaseSrv.create(ShareCase(), createdShare, `case`)
        _            <- shareProfileSrv.create(ShareProfile(), createdShare, profile)
        _            <- auditSrv.share.create(createdShare, `case`)
      } yield createdShare
    }

  def get(`case`: Case with Entity, organisation: Organisation with Entity)(implicit graph: Graph): ShareSteps =
    initSteps.relatedTo(`case`).relatedTo(organisation)

  /**
    * Removes and creates a new ShareProfile
    * @param share the share to connect the profile to
    * @param prevProfile the previous profile
    * @param profile the new one
    * @return
    */
  def updateProfile(share: Share with Entity, prevProfile: Profile with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[ShareProfile with Entity] = {
    get(share)
      .outToE[ShareProfile]
      .filter(_.inV().hasId(prevProfile._id))
      .remove()

    shareProfileSrv.create(ShareProfile(), share, profile)
  }

  /**
    * Shares all the tasks for an already shared case
    * @param share the associated share
    * @return
    */
  def shareCaseTasks(
      share: Share with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[List[ShareTask with Entity]] = {
    val (successes, failures) = {
      for {
        task <- get(share).`case`.tasks.toList
      } yield shareTaskSrv.create(ShareTask(), share, task)
    } partition (_.isSuccess)

    if (failures.nonEmpty) Failure(failures.map(_.failed.get).head)
    else Success(successes.map(_.get))
  }

  /**
    * Shares all the observables for an already shared case
    * @param share the associated share
    * @return
    */
  def shareCaseObservables(
      share: Share with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[List[ShareObservable with Entity]] = {
    val (successes, failures) = {
      for {
        obs <- get(share).`case`.observables.toList
      } yield shareObservableSrv.create(ShareObservable(), share, obs)
    } partition (_.isSuccess)

    if (failures.nonEmpty) Failure(failures.map(_.failed.get).head)
    else Success(successes.map(_.get))
  }

}

@EntitySteps[Share]
class ShareSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Share](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): ShareSteps = new ShareSteps(newRaw)
  override def newInstance(): ShareSteps                             = new ShareSteps(raw.clone())

  def relatedTo(`case`: Case with Entity): ShareSteps = this.filter(_.`case`.get(`case`._id))

  def `case`: CaseSteps = new CaseSteps(raw.outTo[ShareCase])

  def relatedTo(organisation: Organisation with Entity): ShareSteps = this.filter(_.organisation.get(organisation._id))

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[OrganisationShare])

  def tasks = new TaskSteps(raw.outTo[ShareTask])

  def observables = new ObservableSteps(raw.outTo[ShareObservable])

  def profile: ProfileSteps = new ProfileSteps(raw.outTo[ShareProfile])

  def richShare: Traversal[RichShare, RichShare] = Traversal(
    raw
      .project(
        _.apply(By[Vertex]())
          .and(By(__[Vertex].inTo[OrganisationShare].values[String]("name").fold))
          .and(By(__[Vertex].outTo[ShareCase].id().fold))
          .and(By(__[Vertex].outTo[ShareProfile].values[String]("name").fold))
      )
      .map {
        case (share, organisationName, caseId, profileName) =>
          RichShare(
            share.as[Share],
            onlyOneOf[AnyRef](caseId).toString,
            onlyOneOf[String](organisationName),
            onlyOneOf[String](profileName)
          )
      }
  )
}
