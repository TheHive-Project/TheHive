package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Named, Provider, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.scalligraph.{CreateError, EntitySteps}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._

import scala.util.{Failure, Try}

@Singleton
class ShareSrv @Inject() (
    @Named("with-thehive-schema") implicit val db: Database,
    auditSrv: AuditSrv,
    caseSrvProvider: Provider[CaseSrv],
    taskSrv: TaskSrv,
    observableSrvProvider: Provider[ObservableSrv]
) extends VertexSrv[Share, ShareSteps] {
  lazy val caseSrv: CaseSrv             = caseSrvProvider.get
  lazy val observableSrv: ObservableSrv = observableSrvProvider.get

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
    * @param owner indicate if the share owns the case
    * @param `case` the case to share
    * @param organisation the organisation to share for
    * @param profile the related share profile
    * @return
    */
  def shareCase(owner: Boolean, `case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Share with Entity] =
    get(`case`, organisation.name).headOption() match {
      case Some(_) => Failure(CreateError(s"Case #${`case`.number} is already shared with organisation ${organisation.name}"))
      case None =>
        for {
          createdShare <- createEntity(Share(owner))
          _            <- organisationShareSrv.create(OrganisationShare(), organisation, createdShare)
          _            <- shareCaseSrv.create(ShareCase(), createdShare, `case`)
          _            <- shareProfileSrv.create(ShareProfile(), createdShare, profile)
          _            <- auditSrv.share.shareCase(`case`, organisation, profile)
        } yield createdShare
    }

  def get(`case`: Case with Entity, organisationName: String)(implicit graph: Graph): ShareSteps =
    caseSrv.get(`case`).share(organisationName)

  def get(observable: Observable with Entity, organisationName: String)(implicit graph: Graph): ShareSteps =
    observableSrv.get(observable).share(organisationName)

  def get(task: Task with Entity, organisationName: String)(implicit graph: Graph): ShareSteps =
    taskSrv.get(task).share(organisationName)

  def update(
      share: Share with Entity,
      profile: Profile with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[ShareProfile with Entity] = {
    get(share).outToE[ShareProfile].remove()
    for {
      newShareProfile <- shareProfileSrv.create(ShareProfile(), share, profile)
      case0           <- get(share).`case`.getOrFail("Case")
      organisation    <- get(share).organisation.getOrFail("Organisation")
      _               <- auditSrv.share.shareCase(case0, organisation, profile)
    } yield newShareProfile
  }

//  def remove(`case`: Case with Entity, organisationId: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
//    caseSrv.get(`case`).inTo[ShareCase].filter(_.inTo[OrganisationShare])._id.getOrFail().flatMap(remove(_)) // FIXME add organisation ?

  def remove(shareId: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      case0        <- get(shareId).`case`.getOrFail("Case")
      organisation <- get(shareId).organisation.getOrFail("Organisation")
      _            <- auditSrv.share.unshareCase(case0, organisation)
    } yield get(shareId).remove()

  /**
    * Unshare Task for a given Organisation
    * @param task task to unshare
    * @param organisation organisation the task must be unshared with
    * @return
    */
  def removeShareTasks(
      task: Task with Entity,
      organisation: Organisation with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      shareTask <- taskSrv
        .get(task)
        .inToE[ShareTask]
        .filter(st => new ShareSteps(st.outV().raw).byOrganisationName(organisation.name))
        .getOrFail("Task")
      case0 <- taskSrv.get(task).`case`.getOrFail("Case")
      _     <- auditSrv.share.unshareTask(task, case0, organisation)
    } yield shareTaskSrv.get(shareTask.id().toString).remove()

  /**
    * Unshare Task for a given Organisation
    * @param observable observable to unshare
    * @param organisation organisation the task must be unshared with
    * @return
    */
  def removeShareObservable(
      observable: Observable with Entity,
      organisation: Organisation with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      shareObservable <- observableSrv
        .get(observable)
        .inToE[ShareObservable]
        .filter(_.outV().inTo[OrganisationShare].hasId(organisation._id))
        .getOrFail("Share")
      case0 <- observableSrv.get(observable).`case`.getOrFail("Case")
      _     <- auditSrv.share.unshareObservable(observable, case0, organisation)
    } yield shareObservableSrv.get(shareObservable.id().toString).remove()

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
  def shareTask(
      richTask: RichTask,
      `case`: Case with Entity,
      organisation: Organisation with Entity
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      share <- get(`case`, organisation.name).getOrFail("Case")
      _     <- shareTaskSrv.create(ShareTask(), share, richTask.task)
      _     <- auditSrv.task.create(richTask.task, richTask.toJson)
    } yield ()

  /**
    * Shares an observable for an already shared case
    * @return
    */
  def shareObservable(richObservable: RichObservable, `case`: Case with Entity, organisation: Organisation with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      share <- get(`case`, organisation.name).getOrFail("Case")
      _     <- shareObservableSrv.create(ShareObservable(), share, richObservable.observable)
      _     <- auditSrv.observable.create(richObservable.observable, richObservable.toJson)
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
      .toTry { organisation =>
        for {
          case0 <- taskSrv.get(task).`case`.getOrFail("Task")
          share <- caseSrv.get(case0).share(organisation.name).getOrFail("Share")
          _     <- shareTaskSrv.create(ShareTask(), share, task)
          _     <- auditSrv.share.shareTask(task, case0, organisation)
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
      .toTry { organisation =>
        for {
          case0 <- taskSrv.get(task).`case`.getOrFail("Task")
          share <- caseSrv.get(case0).share(organisation.name).getOrFail("Case")
          _     <- shareTaskSrv.create(ShareTask(), share, task)
          _     <- auditSrv.share.shareTask(task, case0, organisation)
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
      .toTry { organisation =>
        for {
          case0 <- observableSrv.get(observable).`case`.getOrFail("Observable")
          share <- caseSrv.get(case0).share(organisation.name).getOrFail("Case")
          _     <- shareObservableSrv.create(ShareObservable(), share, observable)
          _     <- auditSrv.share.shareObservable(observable, case0, organisation)
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
      .toTry { organisation =>
        for {
          case0 <- observableSrv.get(observable).`case`.getOrFail("Observable")
          share <- caseSrv.get(case0).share(organisation.name).getOrFail("Case")
          _     <- shareObservableSrv.create(ShareObservable(), share, observable)
          _     <- auditSrv.share.shareObservable(observable, case0, organisation)
        } yield ()
      }
      .map(_ => ())
  }
}

@EntitySteps[Share]
class ShareSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph) extends VertexSteps[Share](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): ShareSteps = new ShareSteps(newRaw)
  override def newInstance(): ShareSteps                             = new ShareSteps(raw.clone())

  def relatedTo(`case`: Case with Entity): ShareSteps = this.filter(_.`case`.get(`case`._id))

  def `case`: CaseSteps = new CaseSteps(raw.outTo[ShareCase])

  def relatedTo(organisation: Organisation with Entity): ShareSteps = this.filter(_.organisation.get(organisation._id))

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[OrganisationShare])

  def tasks = new TaskSteps(raw.outTo[ShareTask])

  def byTask(taskId: String): ShareSteps = this.filter(
    _.outTo[ShareTask].hasId(taskId)
  )

  def byObservable(observableId: String): ShareSteps = this.filter(
    _.outTo[ShareObservable].hasId(observableId)
  )

  def byOrganisationName(organisationName: String): ShareSteps = this.filter(
    _.inTo[OrganisationShare].has("name", organisationName)
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
