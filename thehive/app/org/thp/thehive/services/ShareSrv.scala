package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.{CreateError, EntityId, EntityIdOrName}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.libs.json.{Json, OFormat}

import java.util.{Map => JMap}
import scala.util.{Failure, Success, Try}

//case class CaseSharingProfile(taskRule: String, taskRuleEditable: Boolean, observableRule: String, observableRuleEditable: Boolean)
//object CaseSharingProfile {
//  def default = CaseSharingProfile(taskRule = "none", taskRuleEditable = true, observableRule = "none", observableRuleEditable = true)
//}
//
//case class OrganisationSharingProfile(
//    name: String,
//    description: String,
//    autoShare: Boolean,
//    autoShareEditable: Boolean,
//    permissionProfile: String,
//    permissionProfileEditable: Boolean,
//    caseSharingProfile: CaseSharingProfile
//)

case class SharingParameter(
    autoShare: Boolean,
    permissionProfile: String,
    taskRule: String,
    observableRule: String
)
//{
//  def toSharingProfile: SharingProfile =
//    SharingProfile(
//      name = "custom",
//      description = "Sharing profile manually set by user",
//      autoShare = autoShare,
//      editable = true,
//      permissionProfile = permissionProfile,
//      taskRule = taskRule,
//      observableRule = observableRule
//    )
//}

object SharingRule {
  val manual: String       = "manual"
  val existingOnly: String = "existingOnly"
  val upcomingOnly: String = "upcomingOnly"
  val all: String          = "all"
  val default: String      = manual
}
case class SharingProfile(
    name: String,
    description: String,
    autoShare: Boolean,
    editable: Boolean,
    permissionProfile: String,
    taskRule: String,
    observableRule: String
) {
  def applyWith(sharingParameter: SharingParameter): SharingProfile =
    if (editable || !autoShare)
      SharingProfile(
        name = "custom",
        description = "Sharing profile manually set by user",
        autoShare = sharingParameter.autoShare,
        editable = true,
        permissionProfile = sharingParameter.permissionProfile,
        taskRule = sharingParameter.taskRule,
        observableRule = sharingParameter.observableRule
      )
    else this

//  def toUserShare: SharingParameter = SharingParameter(autoShare, permissionProfile, taskRule, observableRule)
}

object SharingProfile {
  implicit val formats: OFormat[SharingProfile] = Json.format[SharingProfile]
  val default: SharingProfile =
    SharingProfile(
      name = "default",
      description = "default sharing profile",
      autoShare = false,
      editable = true,
      permissionProfile = Profile.readonly.name,
      taskRule = SharingRule.default,
      observableRule = SharingRule.default
    )
}

class ShareSrv(
    auditSrv: AuditSrv,
    profileSrv: ProfileSrv,
    _caseSrv: => CaseSrv,
    _taskSrv: => TaskSrv,
    _observableSrv: => ObservableSrv,
    organisationSrv: OrganisationSrv
) extends VertexSrv[Share]
    with TheHiveOpsNoDeps {
  lazy val caseSrv: CaseSrv             = _caseSrv
  lazy val observableSrv: ObservableSrv = _observableSrv
  lazy val taskSrv: TaskSrv             = _taskSrv

  val organisationShareSrv = new EdgeSrv[OrganisationShare, Organisation, Share]
  val shareProfileSrv      = new EdgeSrv[ShareProfile, Share, Profile]
  val shareCaseSrv         = new EdgeSrv[ShareCase, Share, Case]
  val shareTaskSrv         = new EdgeSrv[ShareTask, Share, Task]
  val shareObservableSrv   = new EdgeSrv[ShareObservable, Share, Observable]

  /**
    * Shares a case (creates a share entity) for a precise organisation
    * according to the given profile.
    *
    * @param owner indicate if the share owns the case
    * @param `case` the case to share
    * @param organisation the organisation to share for
    * @param sharingProfile the related share profile
    * @return
    */
  def shareCase(owner: Boolean, `case`: Case with Entity, organisation: Organisation with Entity, sharingProfile: SharingProfile)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Share with Entity] = {
    def applySharingRules: Try[Unit] =
      for {
        share <- get(`case`, authContext.organisation).getOrFail("Case")
        _ <-
          if (share.taskRule == SharingRule.all || share.taskRule == SharingRule.existingOnly)
            shareTasks(`case`, None, Some(Seq(organisation)))
          else Success(())
        _ <-
          if (share.observableRule == SharingRule.all || share.observableRule == SharingRule.existingOnly)
            shareObservables(`case`, None, Some(Seq(organisation)))
          else Success(())

      } yield ()

    get(`case`, organisation._id).headOption match {
      case Some(_) => Failure(CreateError(s"Case #${`case`.number} is already shared with organisation ${organisation.name}"))
      case None =>
        for {
          createdShare <- createEntity(Share.fromProfile(owner, sharingProfile))
          profile      <- profileSrv.getOrFail(EntityIdOrName(sharingProfile.permissionProfile))
          _            <- organisationShareSrv.create(OrganisationShare(), organisation, createdShare)
          _            <- shareCaseSrv.create(ShareCase(), createdShare, `case`)
          _            <- shareProfileSrv.create(ShareProfile(), createdShare, profile)
          _            <- caseSrv.get(`case`).addValue(_.organisationIds, organisation._id).getOrFail("Case")
          _            <- if (owner) Success(()) else applySharingRules
          _            <- auditSrv.share.shareCase(`case`, organisation, profile)
        } yield createdShare
    }
  }

  def get(`case`: Case with Entity, organisationName: EntityIdOrName)(implicit graph: Graph): Traversal.V[Share] =
    caseSrv.get(`case`).share(organisationName)

  def get(observable: Observable with Entity, organisationName: EntityIdOrName)(implicit graph: Graph): Traversal.V[Share] =
    observableSrv.get(observable).share(organisationName)

  def get(task: Task with Entity, organisationName: EntityIdOrName)(implicit graph: Graph): Traversal.V[Share] =
    taskSrv.get(task).share(organisationName)

  def updateProfile(
      share: Share with Entity,
      profile: Profile with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[ShareProfile with Entity] = {
    get(share).outE[ShareProfile].remove()
    for {
      newShareProfile <- shareProfileSrv.create(ShareProfile(), share, profile)
      case0           <- get(share).`case`.getOrFail("Case")
      organisation    <- get(share).organisation.getOrFail("Organisation")
      _               <- auditSrv.share.shareCase(case0, organisation, profile)
    } yield newShareProfile
  }

  def updateTaskRule(`case`: Case with Entity, newRule: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    get(`case`, authContext.organisation)
      .update(_.taskRule, newRule)
      .getOrFail("Share")
      .flatMap { _ =>
        if (newRule == SharingRule.existingOnly || newRule == SharingRule.all)
          shareTasks(`case`, None, None)
        else Success(())
      }

  def updateObservableRule(`case`: Case with Entity, newRule: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    get(`case`, authContext.organisation)
      .update(_.observableRule, newRule)
      .getOrFail("Share")
      .flatMap { _ =>
        if (newRule == SharingRule.existingOnly || newRule == SharingRule.all)
          shareObservables(`case`, Some(caseSrv.get(`case`).observables), None)
        else Success(())
      }

  def unshareCase(shareId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      organisation <- get(shareId).organisation.getOrFail("Organisation")
      case0        <- get(shareId).`case`.removeValue(_.organisationIds, organisation._id).getOrFail("Case")
      _ = get(shareId).observables.removeValue(_.organisationIds, organisation._id).barrier().filterNot(_.shares.range(1, 2)).remove()
      _ = get(shareId).tasks.removeValue(_.organisationIds, organisation._id).barrier().filterNot(_.shares.range(1, 2)).remove()
      _ <- auditSrv.share.unshareCase(case0, organisation)
    } yield get(shareId).remove()

  def unshareTask(
      task: Task with Entity,
      organisation: Organisation with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      shareTask <-
        taskSrv
          .get(task)
          .removeValue(_.organisationIds, organisation._id)
          .inE[ShareTask]
          .filter(_.outV.v[Share].byOrganisation(organisation._id))
          .getOrFail("Task")
      case0 <- taskSrv.get(task).`case`.getOrFail("Case")
      _     <- auditSrv.share.unshareTask(task, case0, organisation)
    } yield shareTaskSrv.get(shareTask).remove()

  def unshareObservable(
      observable: Observable with Entity,
      organisation: Organisation with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      shareObservable <-
        observableSrv
          .get(observable)
          .removeValue(_.organisationIds, organisation._id)
          .inE[ShareObservable]
          .filter(_.outV.in[OrganisationShare].hasId(organisation._id))
          .getOrFail("Share")
      case0 <- observableSrv.get(observable).`case`.getOrFail("Case")
      _     <- auditSrv.share.unshareObservable(observable, case0, organisation)
    } yield shareObservableSrv.get(shareObservable).remove()

//  /**
//    * Shares all the tasks for an already shared case
//    * @param share the associated share
//    * @return
//    */
//  def shareCaseTasks(
//      share: Share with Entity
//  )(implicit graph: Graph, authContext: AuthContext): Try[Seq[ShareTask with Entity]] =
//    for {
//      organisation <- get(share).organisation.getOrFail("Share")
//      shareTask <-
//        get(share)
//          .`case`
//          .tasks
//          .filter(_.shares.has(T.id, P.neq(share._id)))
//          .toIterator
//          .toTry { task =>
//            taskSrv
//              .get(task)
//              .addValue(_.organisationIds, organisation._id)
//              .getOrFail("Task")
//              .flatMap(shareTaskSrv.create(ShareTask(), share, _))
//          }
//    } yield shareTask
//
  /**
    * Add a new task to a case
    * @return
    */
  def addTaskToCase(
      richTask: RichTask,
      `case`: Case with Entity,
      organisationId: EntityId
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      share <- get(`case`, organisationId).getOrFail("Case")
      _     <- shareTaskSrv.create(ShareTask(), share, richTask.task)
      _     <- taskSrv.get(richTask.task).addValue(_.organisationIds, organisationId).getOrFail("Task")
      _ <-
        if (share.taskRule == SharingRule.upcomingOnly || share.taskRule == SharingRule.all)
          shareTasks(`case`, Some(taskSrv.get(richTask.task)), None)
        else Success(())
      _ <- auditSrv.task.create(richTask.task, richTask.toJson)
    } yield ()

  /**
    * Add a new observable to a case
    * @return
    */
  def addObservableToCase(richObservable: RichObservable, `case`: Case with Entity, organisationId: EntityId)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      share <- get(`case`, organisationId).getOrFail("Case")
      _     <- shareObservableSrv.create(ShareObservable(), share, richObservable.observable)
      _     <- observableSrv.get(richObservable.observable).addValue(_.organisationIds, organisationId).getOrFail("Observable")
      _ <-
        if (share.observableRule == SharingRule.upcomingOnly || share.observableRule == SharingRule.all)
          shareObservables(`case`, Some(observableSrv.get(richObservable.observable)), None)
        else Success(())
      _ <- auditSrv.observable.create(richObservable.observable, richObservable.toJson)
    } yield ()
//
//  /**
//    * Shares all the observables for an already shared case
//    * @param share the associated share
//    * @return
//    */
//  def shareCaseObservables(
//      share: Share with Entity
//  )(implicit graph: Graph, authContext: AuthContext): Try[Seq[ShareObservable with Entity]] =
//    for {
//      organisation <- get(share).organisation.getOrFail("Share")
//      shareObservable <-
//        get(share)
//          .`case`
//          .observables // list observables related to authContext
//          .filter(_.shares.has(T.id, P.neq(share._id)))
//          .toIterator
//          .toTry { obs =>
//            observableSrv
//              .get(obs)
//              .addValue(_.organisationIds, organisation._id)
//              .getOrFail("Observable")
//              .flatMap(_ => shareObservableSrv.create(ShareObservable(), share, obs))
//
//          }
//    } yield shareObservable

//  /**
//    * Does a full rebuild of the share status of a task,
//    * i.e. adds what's in the list and removes what's not
//    * @param task the task concerned
//    * @param organisations the organisations that are going to be able to see the task
//    * @return
//    */
//  def updateTaskShares(
//      task: Task with Entity,
//      organisations: Set[Organisation with Entity]
//  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
//    val (orgsToAdd, orgsToRemove) = taskSrv
//      .get(task)
//      .update(_.organisationIds, organisations.map(_._id))
//      .shares
//      .organisation
//      .toIterator
//      .foldLeft((organisations, Set.empty[Organisation with Entity])) {
//        case ((toAdd, toRemove), o) if toAdd.contains(o) => (toAdd - o, toRemove)
//        case ((toAdd, toRemove), o)                      => (toAdd, toRemove + o)
//      }
//    orgsToRemove.foreach(o => taskSrv.get(task).share(o._id).remove())
//    orgsToAdd
//      .toTry { organisation =>
//        for {
//          case0 <- taskSrv.get(task).`case`.getOrFail("Task")
//          share <- caseSrv.get(case0).share(organisation._id).getOrFail("Share")
//          _     <- shareTaskSrv.create(ShareTask(), share, task)
//          _     <- auditSrv.share.shareTask(task, case0, organisation)
//        } yield ()
//      }
//      .map(_ => ())
//  }
//

//  private def shareTasksToAllOrgs(`case`: Case with Entity, tasks: Option[Traversal.V[Task]])(implicit
//      graph: Graph,
//      authContext: AuthContext
//  ): Try[Unit] = {

//    caseSrv
//      .get(`case`)
//      .shares
//      .filterNot(_.organisation.current)
//      .project(_.by.by(_.organisation._id))
//      .toIterator
//      .toTry {
//        case (toShare, orgId) =>
//          tasks.getOrElse(caseSrv.get(`case`).tasks).filterNot(_.shares.hasId(toShare._id)).toIterator.toTry { task =>
//            for {
//              _ <- shareTaskSrv.create(ShareTask(), toShare, task)
//              _ <- taskSrv.get(task).addValue(_.organisationIds, orgId).getOrFail("Task")
//            } yield ()
//          }
//      }
//      .map(_ => ())
//  }
  def shareTask(
      task: Task with Entity,
      organisations: Seq[Organisation with Entity]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    taskSrv.get(task).`case`.getOrFail("Case").flatMap(c => shareTasks(c, Some(taskSrv.get(task)), Some(organisations)))
  //    val existingOrgs = taskSrv
  //      .get(task)
  //      .shares
  //      .organisation
  //      ._id
  //      .toSet
  //
  //    organisations
  //      .filterNot(o => existingOrgs.contains(o._id))
  //      .toTry { organisation =>
  //        for {
  //          case0 <- taskSrv.get(task).addValue(_.organisationIds, organisation._id).`case`.getOrFail("Task")
  //          share <- get(case0, organisation._id).getOrFail("Case")
  //          _     <- shareTaskSrv.create(ShareTask(), share, task)
  //          _     <- auditSrv.share.shareTask(task, case0, organisation)
  //        } yield ()
  //      }
  //      .map(_ => ())

  private def shareTasks(`case`: Case with Entity, tasks: Option[Traversal.V[Task]], organisations: Option[Seq[Organisation with Entity]])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    organisations
      .getOrElse(caseSrv.get(`case`).organisations.has(T.id, P.neq(organisationSrv.currentId)).toSeq)
      .toTry { organisation =>
        tasks
          .getOrElse(get(`case`, authContext.organisation).tasks)
          .filterNot(_.share(organisation._id))
          .toIterator
          .toTry { task =>
            for {
              case0 <- taskSrv.get(task).addValue(_.organisationIds, organisation._id).`case`.getOrFail("Task")
              share <- get(case0, organisation._id).getOrFail("Case")
              _     <- shareTaskSrv.create(ShareTask(), share, task)
              _     <- auditSrv.share.shareTask(task, case0, organisation)
            } yield ()
          }
      }
      .map(_ => ())

  def shareObservable(
      observable: Observable with Entity,
      organisations: Seq[Organisation with Entity]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    observableSrv.get(observable).`case`.getOrFail("Case").flatMap(c => shareObservables(c, Some(observableSrv.get(observable)), Some(organisations)))

  private def shareObservables(
      `case`: Case with Entity,
      observables: Option[Traversal.V[Observable]],
      organisations: Option[Seq[Organisation with Entity]]
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    organisations
      .getOrElse(caseSrv.get(`case`).organisations.has(T.id, P.neq(organisationSrv.currentId)).toSeq)
      .toTry { organisation =>
        observables
          .getOrElse(get(`case`, authContext.organisation).observables)
          .filterNot(_.share(organisation._id))
          .toIterator
          .toTry { observable =>
            for {
              case0 <- observableSrv.get(observable).addValue(_.organisationIds, organisation._id).`case`.getOrFail("Observable")
              share <- get(case0, organisation._id).getOrFail("Case")
              _     <- shareObservableSrv.create(ShareObservable(), share, observable)
              _     <- auditSrv.share.shareObservable(observable, case0, organisation)
            } yield ()
          }
      }
      .map(_ => ())

//  def shareObservablesToAllOrgs(`case`: Case with Entity, observables: Option[Traversal.V[Observable]])(implicit
//                                                                                                        graph: Graph,
//                                                                                                        authContext: AuthContext
//  ): Try[Unit] =
//    caseSrv
//      .get(`case`)
//      .shares
//      .filterNot(_.organisation.current)
//      .project(_.by.by(_.organisation._id))
//      .toIterator
//      .toTry {
//        case (toShare, orgId) =>
//          observables.getOrElse(caseSrv.get(`case`).observables).filterNot(_.shares.hasId(toShare._id)).toIterator.toTry { observable =>
//            for {
//              _ <- shareObservableSrv.create(ShareObservable(), toShare, observable)
//              _ <- observableSrv.get(observable).addValue(_.organisationIds, orgId).getOrFail("Observable")
//            } yield ()
//          }
//      }
//      .map(_ => ())
//

  //
//  /**
//    * Does a full rebuild of the share status of an observable,
//    * i.e. adds what's in the list and removes what's not
//    * @param observable the observable concerned
//    * @param organisations the organisations that are going to be able to see the observable
//    * @return
//    */
//  def updateObservableShares(
//      observable: Observable with Entity,
//      organisations: Set[Organisation with Entity]
//  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
//    val (orgsToAdd, orgsToRemove) = observableSrv
//      .get(observable)
//      .update(_.organisationIds, organisations.map(_._id))
//      .shares
//      .organisation
//      .toIterator
//      .foldLeft((organisations, Set.empty[Organisation with Entity])) {
//        case ((toAdd, toRemove), o) if toAdd.contains(o) => (toAdd - o, toRemove)
//        case ((toAdd, toRemove), o)                      => (toAdd, toRemove + o)
//      }
//    orgsToRemove.foreach(o => observableSrv.get(observable).removeValue(_.organisationIds, o._id).share(o._id).remove())
//    orgsToAdd
//      .toTry { organisation =>
//        for {
//          case0 <- observableSrv.get(observable).addValue(_.organisationIds, organisation._id).`case`.getOrFail("Observable")
//          share <- caseSrv.get(case0).share(organisation._id).getOrFail("Case")
//          _     <- shareObservableSrv.create(ShareObservable(), share, observable)
//          _     <- auditSrv.share.shareObservable(observable, case0, organisation)
//        } yield ()
//      }
//      .map(_ => ())
//  }
}

trait ShareOps { _: TheHiveOpsNoDeps =>
  implicit class ShareOpsDefs(traversal: Traversal.V[Share]) {
    def get(idOrName: EntityIdOrName): Traversal.V[Share] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.empty)

    def relatedTo(`case`: Case with Entity): Traversal.V[Share] = traversal.filter(_.`case`.hasId(`case`._id))

    def `case`: Traversal.V[Case] = traversal.out[ShareCase].v[Case]

    def relatedTo(organisation: Organisation with Entity): Traversal.V[Share] = traversal.filter(_.organisation.hasId(organisation._id))

    def organisation: Traversal.V[Organisation] = traversal.in[OrganisationShare].v[Organisation]

    def visible(implicit authContext: AuthContext): Traversal.V[Share] = traversal.filter(_.organisation.visible)

    def tasks: Traversal.V[Task] = traversal.out[ShareTask].v[Task]

    def byTask(taskId: EntityIdOrName): Traversal.V[Share] =
      traversal.filter(_.tasks.get(taskId))

    def byObservable(observableId: EntityIdOrName): Traversal.V[Share] =
      traversal.filter(_.observables.get(observableId))

    def byOrganisation(organisationName: EntityIdOrName): Traversal.V[Share] =
      traversal.filter(_.organisation.get(organisationName))

    def observables: Traversal.V[Observable] = traversal.out[ShareObservable].v[Observable]

    def profile: Traversal.V[Profile] = traversal.out[ShareProfile].v[Profile]

    def richShare: Traversal[RichShare, JMap[String, Any], Converter[RichShare, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.in[OrganisationShare].v[Organisation].value(_.name).fold)
            .by(_.out[ShareCase]._id.fold)
            .by(_.out[ShareProfile].v[Profile].value(_.name).fold)
        )
        .domainMap {
          case (share, organisationName, caseId, profileName) =>
            RichShare(
              share,
              caseId.head,
              organisationName.head,
              profileName.head
            )
        }

    def sharingProfiles: Traversal[(Organisation with Entity, SharingProfile), JMap[String, Any], Converter[
      (Organisation with Entity, SharingProfile),
      JMap[String, Any]
    ]] =
      traversal.project(_.by(_.organisation).by.by(_.profile.value(_.name))).domainMap {
        case (org, share, profileName) =>
          org -> SharingProfile(
            "fromShare",
            "Sharing profile from a share",
            autoShare = true,
            editable = true,
            permissionProfile = profileName,
            taskRule = share.taskRule,
            observableRule = share.observableRule
          )
      }
  }
}
