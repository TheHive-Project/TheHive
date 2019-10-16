package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Provider, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._

import scala.util.{Success, Try}

@Singleton
class ShareSrv @Inject()(
    implicit val db: Database,
    auditSrv: AuditSrv,
    caseSrvProvider: Provider[CaseSrv],
    taskSrv: TaskSrv,
    observableSrv: ObservableSrv
) extends VertexSrv[Share, ShareSteps] {
  lazy val caseSrv: CaseSrv = caseSrvProvider.get

  val organisationShareSrv = new EdgeSrv[OrganisationShare, Organisation, Share]
  val shareProfileSrv      = new EdgeSrv[ShareProfile, Share, Profile]
  val shareCaseSrv         = new EdgeSrv[ShareCase, Share, Case]
  val shareTaskSrv         = new EdgeSrv[ShareTask, Share, Task]
  val shareObservableSrv   = new EdgeSrv[ShareObservable, Share, Observable]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ShareSteps = new ShareSteps(raw)

  /**
    * Shares a case (creates a share entity) for a precise organisation
    * according to the given profile.
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
    get(`case`, organisation).headOption() match {
      case Some(existingShare) =>
        // Only addition atm, no update
        /*if (!get(existingShare).profile.has(Key[String]("name"), P.eq[String](profile.name)).exists()) {
          get(existingShare).outToE[ShareProfile].remove()
          shareProfileSrv
            .create(ShareProfile(), existingShare, profile)
            .map(_ => existingShare)
        } else*/
        Success(existingShare)
      case None =>
        for {
          createdShare <- createEntity(Share())
          _            <- organisationShareSrv.create(OrganisationShare(), organisation, createdShare)
          _            <- shareCaseSrv.create(ShareCase(), createdShare, `case`)
          _            <- shareProfileSrv.create(ShareProfile(), createdShare, profile)
          _            <- auditSrv.share.shareCase(`case`, organisation, profile)
        } yield createdShare
    }

  def get(`case`: Case with Entity, organisation: Organisation with Entity)(implicit graph: Graph): ShareSteps =
    initSteps.relatedTo(`case`).relatedTo(organisation)

  def update(
      profile: Profile with Entity,
      share: Share with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[ShareProfile with Entity] = {
    get(share).outToE[ShareProfile].remove()
    for {
      newShareProf <- shareProfileSrv.create(ShareProfile(), share, profile)
      c            <- get(share).`case`.getOrFail()
      o            <- get(share).organisation.getOrFail()
      _            <- auditSrv.share.shareCase(c, o, profile)
    } yield newShareProf
  }

  def remove(share: Share with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(share).remove()
    for {
      c <- get(share).`case`.getOrFail()
      o <- get(share).organisation.getOrFail()
      _ <- auditSrv.share.unshareCase(c, o)
    } yield ()
  }

  /**
    * Unshare all Tasks for a given Share (Case <=> Organisation)
    * @param share the given share to clean
    * @return
    */
  def removeShareTasks(share: Share with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Seq[Unit]] = {
    val tasks = get(share).tasks.toList
    get(share)
      .outToE[ShareTask]
      .remove()
    tasks.toTry(
      t =>
        for {
          c <- taskSrv.get(t).`case`.getOrFail()
          o <- get(share).organisation.getOrFail()
          _ <- auditSrv.share.unshareTask(t, c, o)
        } yield ()
    )
  }

  /**
    * Unshare all Observables for a given Share (Case <=> Organisation)
    * @param share the given share to clean
    * @return
    */
  def removeShareObservable(
      share: Share with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Seq[Unit]] = {
    val observables = get(share).observables.toList
    get(share)
      .outToE[ShareObservable]
      .remove()
    observables.toTry(
      obs =>
        for {
          c <- observableSrv.get(obs).`case`.getOrFail()
          o <- get(share).organisation.getOrFail()
          _ <- auditSrv.share.unshareObservable(obs, c, o)
        } yield ()
    )
  }

  /**
    * Shares all the tasks for an already shared case
    * @param share the associated share
    * @return
    */
  def shareCaseTasks(
      share: Share with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Seq[ShareTask with Entity]] =
    get(share).`case`.tasks.filter(_.not(_.shares.hasId(share._id))).toIterator.toTry(shareTaskSrv.create(ShareTask(), share, _))

  /**
    * Shares a task for an already shared case
    * @return
    */
  def shareCaseTask(`case`: Case with Entity, richTask: RichTask)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      share <- caseSrv
        .get(`case`)
        .share
        .getOrFail()
      _ = shareTaskSrv.create(ShareTask(), share, richTask.task)
      _ <- auditSrv.task.create(richTask.task, `case`, richTask.toJson)
    } yield ()

  /**
    * Shares an observable for an already shared case
    * @return
    */
  def shareCaseObservable(`case`: Case with Entity, richObservable: RichObservable)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      share <- caseSrv
        .get(`case`)
        .share
        .getOrFail()
      _ <- shareObservableSrv.create(ShareObservable(), share, richObservable.observable)
      _ <- auditSrv.observable.create(richObservable.observable, `case`, richObservable.toJson)
    } yield ()

  /**
    * Shares all the observables for an already shared case
    * @param share the associated share
    * @return
    */
  def shareCaseObservables(
      share: Share with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Seq[ShareObservable with Entity]] =
    get(share).`case`.observables.filter(_.not(_.shares.hasId(share._id))).toIterator.toTry(shareObservableSrv.create(ShareObservable(), share, _))

  /**
    * Does a full rebuild of the share status of a task,
    * i.e. adds what's in the list and removes what's not
    * @param task the task concerned
    * @param organisations the organisations that are going to be able to see the task
    * @return
    */
  def updateTaskShares(
      task: Task with Entity,
      organisations: Seq[Organisation with Entity]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (orgsToAdd, orgsToRemove) = taskSrv
      .get(task)
      .shares
      .organisation
      .toIterator
      .foldLeft((organisations.toSet, Set.empty[Organisation with Entity])) {
        case ((toAdd, toRemove), o) if toAdd.contains(o) => (toAdd - o, toRemove)
        case ((toAdd, toRemove), o)                      => (toAdd, toRemove + o)
      }
    orgsToRemove.foreach(o => taskSrv.get(task).share(o.name).remove())
    orgsToAdd
      .toTry { o =>
        for {
          case0 <- taskSrv.get(task).`case`.getOrFail()
          share <- caseSrv.get(case0).share(o.name).getOrFail()
          _     <- shareTaskSrv.create(ShareTask(), share, task)
          _     <- auditSrv.share.shareTask(task, case0, o)
        } yield ()
      }
      .map(_ => ())
  }

  def addTaskShares(
      task: Task with Entity,
      organisations: Seq[Organisation with Entity]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val existingOrgs = taskSrv
      .get(task)
      .shares
      .organisation
      .toList

    organisations
      .filterNot(existingOrgs.contains)
      .toTry { o =>
        for {
          case0 <- taskSrv.get(task).`case`.getOrFail()
          share <- caseSrv.get(case0).share(o.name).getOrFail()
          _     <- shareTaskSrv.create(ShareTask(), share, task)
          _     <- auditSrv.share.shareTask(task, case0, o)
        } yield ()
      }
      .map(_ => ())
  }

  def addObservableShares(
      observable: Observable with Entity,
      organisations: Seq[Organisation with Entity]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val existingOrgs = observableSrv
      .get(observable)
      .shares
      .organisation
      .toList

    organisations
      .filterNot(existingOrgs.contains)
      .toTry { o =>
        for {
          case0 <- observableSrv.get(observable).`case`.getOrFail()
          share <- caseSrv.get(case0).share(o.name).getOrFail()
          _     <- shareObservableSrv.create(ShareObservable(), share, observable)
          _     <- auditSrv.share.shareObservable(observable, case0, o)
        } yield ()
      }
      .map(_ => ())
  }

  /**
    * Does a full rebuild of the share status of an observable,
    * i.e. adds what's in the list and removes what's not
    * @param observable the observable concerned
    * @param organisations the organisations that are going to be able to see the observable
    * @return
    */
  def updateObservableShares(
      observable: Observable with Entity,
      organisations: Seq[Organisation with Entity]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (orgsToAdd, orgsToRemove) = observableSrv
      .get(observable)
      .shares
      .organisation
      .toIterator
      .foldLeft((organisations.toSet, Set.empty[Organisation with Entity])) {
        case ((toAdd, toRemove), o) if toAdd.contains(o) => (toAdd - o, toRemove)
        case ((toAdd, toRemove), o)                      => (toAdd, toRemove + o)
      }
    orgsToRemove.foreach(o => observableSrv.get(observable).share(o.name).remove())
    orgsToAdd
      .toTry { o =>
        for {
          case0 <- observableSrv.get(observable).`case`.getOrFail()
          share <- caseSrv.get(case0).share(o.name).getOrFail()
          _     <- shareObservableSrv.create(ShareObservable(), share, observable)
          _     <- auditSrv.share.shareObservable(observable, case0, o)
        } yield ()
      }
      .map(_ => ())
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

  def byTask(taskId: String): ShareSteps = this.filter(
    _.outTo[ShareTask]
      .filter(_.hasId(taskId))
  )

  def byObservable(obsId: String): ShareSteps = this.filter(
    _.outTo[ShareObservable]
      .filter(_.hasId(obsId))
  )

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
