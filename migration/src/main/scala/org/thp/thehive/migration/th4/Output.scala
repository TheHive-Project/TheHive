package org.thp.thehive.migration.th4

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

import play.api.inject.guice.GuiceInjector
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle, Injector}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Environment}

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.Guice
import com.google.inject.name.Names
import gremlin.scala._
import javax.inject.{Inject, Provider, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.thp.scalligraph._
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, UserSrv => UserDB}
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, Entity, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.connector.cortex.models.{SchemaUpdater => CortexSchemaUpdater}
import org.thp.thehive.connector.cortex.services.{ActionSrv, JobSrv}
import org.thp.thehive.migration
import org.thp.thehive.migration.IdMapping
import org.thp.thehive.migration.dto._
import org.thp.thehive.models.{AlertCase, Case, Permissions, TheHiveSchema, SchemaUpdater => TheHiveSchemaUpdater}
import org.thp.thehive.services.{
  AlertSrv,
  AttachmentSrv,
  AuditSrv,
  CaseSrv,
  CaseTemplateSrv,
  CustomFieldSrv,
  ImpactStatusSrv,
  LocalUserSrv,
  LogSrv,
  ObservableSrv,
  ObservableTypeSrv,
  OrganisationSrv,
  ProfileSrv,
  ResolutionStatusSrv,
  RoleSrv,
  ShareSrv,
  TagSrv,
  TaskSrv,
  UserSrv
}

object Output {

  def apply(configuration: Configuration)(implicit actorSystem: ActorSystem): Output =
    Guice
      .createInjector(new ScalaModule with AkkaGuiceSupport {
        override def configure(): Unit = {
          bind[Configuration].toInstance(configuration)
          bind[ActorSystem].toInstance(actorSystem)
          bind[Materializer].toInstance(Materializer(actorSystem))
          bind[ExecutionContext].toInstance(actorSystem.dispatcher)
          bind[Injector].to[GuiceInjector]
          bind[UserDB].to[LocalUserSrv]
          bindActor[DummyActor]("notification-actor")
          bindActor[DummyActor]("config-actor")
          bindActor[DummyActor]("cortex-actor")
          bind[Database].to[JanusDatabase]
          //    bind[Database].to[OrientDatabase]
          //    bind[Database].to[RemoteJanusDatabase]
          bind[StorageSrv].to[LocalFileSystemStorageSrv]
          //    bind[StorageSrv].to[HadoopStorageSrv]
          bind[Configuration].toInstance(configuration)
          bind[Environment].toInstance(Environment.simple())
          bind[ApplicationLifecycle].to[DefaultApplicationLifecycle]
          bind[Schema].to[TheHiveSchema]
          bind[Int].annotatedWith(Names.named("schemaVersion")).toInstance(1)
          bind[TheHiveSchemaUpdater].asEagerSingleton()
          bind[CortexSchemaUpdater].asEagerSingleton()
        }
      })
      .getInstance(classOf[Output])
}

@Singleton
class Output @Inject() (
    caseSrv: CaseSrv,
    observableSrvProvider: Provider[ObservableSrv],
    userSrv: UserSrv,
    localUserSrv: LocalUserSrv,
    tagSrv: TagSrv,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    observableTypeSrv: ObservableTypeSrv,
    alertSrv: AlertSrv,
    taskSrv: TaskSrv,
    shareSrv: ShareSrv,
    attachmentSrv: AttachmentSrv,
    profileSrv: ProfileSrv,
    logSrv: LogSrv,
    roleSrv: RoleSrv,
    auditSrv: AuditSrv,
    customFieldSrv: CustomFieldSrv,
    impactStatusSrv: ImpactStatusSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    jobSrv: JobSrv,
    actionSrv: ActionSrv,
    db: Database
) extends migration.Output {

  lazy val observableSrv: ObservableSrv = observableSrvProvider.get

  def getAuthContext(userId: String)(implicit graph: Graph): AuthContext =
    userSrv
      .getOrFail(userId)
      .map { user =>
        AuthContextImpl(user.login, user.name, "admin", "mig-request", Permissions.all)
      }
      .getOrElse {
        if (userId != "init") logger.warn(s"User $userId not found, use system user")
        localUserSrv.getSystemAuthContext
      }

  def authTransaction[A](userId: String)(body: Graph => AuthContext => Try[A]): Try[A] = db.tryTransaction { implicit graph =>
    auditSrv.mergeAudits(body(graph)(getAuthContext(userId)))(_ => Success(()))
  }

  override def removeData(): Try[Unit] = db.tryTransaction { graph =>
    logger.info(s"Remove data")
    graph.V.drop().iterate()
    Success(())
  }

  def shareCase(`case`: Case with Entity, organisationName: String, profileName: String)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      organisation <- organisationSrv.getOrFail(organisationName)
      profile      <- profileSrv.getOrFail(profileName)
      _            <- shareSrv.shareCase(owner = false, `case`, organisation, profile)
    } yield ()

  override def createOrganisation(inputOrganisation: InputOrganisation): Try[IdMapping] = authTransaction(inputOrganisation.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create organisation ${inputOrganisation.organisation.name}")
      organisationSrv.create(inputOrganisation.organisation).map(o => IdMapping(inputOrganisation.metaData.id, o._id))
  }

  override def createCase(inputCase: InputCase): Try[IdMapping] =
    authTransaction(inputCase.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.info(s"Create case #${inputCase.`case`.number}")
      val user = inputCase.user.map(userSrv.getOrFail).flip.getOrElse(None)
      for {
        tags         <- inputCase.tags.filterNot(_.isEmpty).toTry(tagSrv.getOrCreate)
        caseTemplate <- inputCase.caseTemplate.map(caseTemplateSrv.get(_).richCaseTemplate.getOrFail()).flip
        organisation <- inputCase.organisations.find(_._2 == ProfileSrv.orgAdmin.name) match {
          case Some(o) => organisationSrv.getOrFail(o._1)
          case None    => Failure(InternalError("Organisation not found"))
        }
        richCase <- caseSrv.create(inputCase.`case`, user, organisation, tags.toSet, Map.empty, caseTemplate, Nil)
        _ <- inputCase.organisations.toTry {
          case (org, profile) if org != organisation.name => shareCase(richCase.`case`, org, profile)
          case _                                          => Success(())
        }
        _ = inputCase.customFields.foreach {
          case (name, value) =>
            caseSrv
              .setOrCreateCustomField(richCase.`case`, name, value)
              .failed
              .foreach(error => logger.warn(s"Add custom field $name:$value to case #${richCase.number} failure: $error"))
        }
      } yield IdMapping(inputCase.metaData.id, richCase._id)
    }

  override def createCaseObservable(caseId: String, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.info(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in case $caseId")
      for {
        observableType <- observableTypeSrv.getOrFail(inputObservable.`type`)
        richObservable <- inputObservable.dataOrAttachment match {
          case Right(inputAttachment) =>
            attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap {
              attachment =>
                observableSrv.create(inputObservable.observable, observableType, attachment, inputObservable.tags, Nil)
            }
          case Left(data) => observableSrv.create(inputObservable.observable, observableType, data, inputObservable.tags, Nil)
        }
        orgs  <- inputObservable.organisations.toTry(organisationSrv.getOrFail)
        case0 <- caseSrv.getOrFail(caseId)
        _ <- caseSrv.addObservable(case0, richObservable)(
          graph,
          AuthContextImpl(authContext.userId, authContext.userName, orgs.head.name, authContext.requestId, authContext.permissions)
        )
        _ <- shareSrv.addObservableShares(richObservable.observable, orgs)
      } yield IdMapping(inputObservable.metaData.id, richObservable._id)
    }

  override def createAlertObservable(alertId: String, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.info(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in alert $alertId")
      for {
        observableType <- observableTypeSrv.getOrFail(inputObservable.`type`)
        richObservable <- inputObservable.dataOrAttachment match {
          case Right(inputAttachment) =>
            attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap {
              attachment =>
                observableSrv.create(inputObservable.observable, observableType, attachment, inputObservable.tags, Nil)
            }
          case Left(data) => observableSrv.create(inputObservable.observable, observableType, data, inputObservable.tags, Nil)
        }
        alert <- alertSrv.getOrFail(alertId)
        _     <- alertSrv.addObservable(alert, richObservable)
      } yield IdMapping(inputObservable.metaData.id, richObservable._id)
    }

  override def createCaseTask(caseId: String, inputTask: InputTask): Try[IdMapping] =
    authTransaction(inputTask.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.info(s"Create task ${inputTask.task.title} in case $caseId")
      for {
        owner    <- inputTask.owner.map(userSrv.getOrFail).flip
        richTask <- taskSrv.create(inputTask.task, owner)
        case0    <- caseSrv.getOrFail(caseId)
        _ <- inputTask.organisations.toTry { organisation =>
          organisationSrv.getOrFail(organisation).flatMap(shareSrv.shareTask(richTask, case0, _))
        }
      } yield IdMapping(inputTask.metaData.id, richTask._id)
    }

  def createCaseTaskLog(taskId: String, inputLog: InputLog): Try[IdMapping] =
    authTransaction(inputLog.metaData.createdBy) { implicit graph => implicit authContext =>
      for {
        task <- taskSrv.getOrFail(taskId)
        _ = logger.info(s"Create log in task ${task.title}")
        log <- logSrv.create(inputLog.log, task)
        _ <- inputLog.attachments.toTry { inputAttachment =>
          attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap { attachment =>
            logSrv.addAttachment(log, attachment)
          }
        }
      } yield IdMapping(inputLog.metaData.id, log._id)
    }

  override def createCaseTemplate(inputCaseTemplate: InputCaseTemplate): Try[IdMapping] = authTransaction(inputCaseTemplate.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create case template ${inputCaseTemplate.caseTemplate.name}")
      for {
        organisation     <- organisationSrv.getOrFail(inputCaseTemplate.organisation)
        richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate.caseTemplate, organisation, inputCaseTemplate.tags, Nil, Nil)
        _                <- caseTemplateSrv.addTags(richCaseTemplate.caseTemplate, inputCaseTemplate.tags)
        _ = inputCaseTemplate.customFields.foreach {
          case (name, value, order) =>
            caseTemplateSrv.setOrCreateCustomField(richCaseTemplate.caseTemplate, name, value, order).getOrElse {
              logger.warn(s"Add custom field $name:$value to case template ${richCaseTemplate.name} failure")
            }
        }

      } yield IdMapping(inputCaseTemplate.metaData.id, richCaseTemplate._id)
  }

  override def createCaseTemplateTask(caseTemplateId: String, inputTask: InputTask): Try[IdMapping] = authTransaction(inputTask.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create task ${inputTask.task.title} in case template $caseTemplateId")
      for {
        caseTemplate <- caseTemplateSrv.getOrFail(caseTemplateId)
        taskOwner    <- inputTask.owner.map(userSrv.getOrFail).flip
        richTask     <- taskSrv.create(inputTask.task, taskOwner)
        _            <- caseTemplateSrv.addTask(caseTemplate, richTask.task)
      } yield IdMapping(inputTask.metaData.id, richTask._id)
  }

  override def createAlert(inputAlert: InputAlert): Try[IdMapping] = authTransaction(inputAlert.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create alert ${inputAlert.alert.source}:${inputAlert.alert.sourceRef}")
      for {
        organisation <- organisationSrv.getOrFail(inputAlert.organisation)
        caseTemplate = inputAlert.caseTemplate.flatMap(caseTemplateSrv.get(_).headOption()) // TODO add warning if case template is not found
        alert <- alertSrv.create(inputAlert.alert, organisation, inputAlert.tags, inputAlert.customFields, caseTemplate)
        case0 <- inputAlert.caseId.map(caseSrv.getOrFail(_).flatMap(case0 => alertSrv.alertCaseSrv.create(AlertCase(), alert.alert, case0))).flip
        // TODO add link between case and alert
      } yield IdMapping(inputAlert.metaData.id, alert._id)
  }

  def createUser(inputUser: InputUser): Try[IdMapping] = authTransaction(inputUser.metaData.createdBy) { implicit graph => implicit authContext =>
    logger.info(s"Create user ${inputUser.user.login}")
    for {
      validUser <- userSrv.checkUser(inputUser.user)
      createdUser <- if (userSrv.get(validUser.login).exists())
        userSrv
          .get(UserSrv.init.login)
          .updateOne("name" -> inputUser.user.name, "apikey" -> inputUser.user.apikey, "password" -> inputUser.user.password)
      else userSrv.createEntity(validUser)
      _ <- inputUser
        .avatar
        .map { inputAttachment =>
          attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap { attachment =>
            userSrv.setAvatar(createdUser, attachment)
          }
        }
        .flip
      _ <- inputUser.organisations.toTry {
        case (organisationName, profileName) =>
          for {
            organisation <- organisationSrv.getOrFail(organisationName)
            profile      <- profileSrv.getOrFail(profileName)
          } yield roleSrv.create(createdUser, organisation, profile)
      }
    } yield IdMapping(inputUser.metaData.id, createdUser._id)
  }

  override def createCustomField(inputCustomField: InputCustomField): Try[IdMapping] = authTransaction(inputCustomField.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create custom field ${inputCustomField.customField.name}")
      customFieldSrv.create(inputCustomField.customField).map(cf => IdMapping(inputCustomField.customField.name, cf._id))
  }

  def createObservableTypes(inputObservableType: InputObservableType): Try[IdMapping] = authTransaction(inputObservableType.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create observable types ${inputObservableType.observableType.name}")
      observableTypeSrv.create(inputObservableType.observableType).map(cf => IdMapping(inputObservableType.observableType.name, cf._id))
  }
  override def createProfile(inputProfile: InputProfile): Try[IdMapping] = authTransaction(inputProfile.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create profile ${inputProfile.profile.name}")
      profileSrv.create(inputProfile.profile).map(profile => IdMapping(inputProfile.profile.name, profile._id))
  }

  override def createImpactStatus(inputImpactStatus: InputImpactStatus): Try[IdMapping] = authTransaction(inputImpactStatus.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create impact status ${inputImpactStatus.impactStatus.value}")
      impactStatusSrv.create(inputImpactStatus.impactStatus).map(status => IdMapping(inputImpactStatus.impactStatus.value, status._id))
  }

  override def createResolutionStatus(inputResolutionStatus: InputResolutionStatus): Try[IdMapping] =
    authTransaction(inputResolutionStatus.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.info(s"Create resolution status ${inputResolutionStatus.resolutionStatus.value}")
      resolutionStatusSrv
        .create(inputResolutionStatus.resolutionStatus)
        .map(status => IdMapping(inputResolutionStatus.resolutionStatus.value, status._id))
    }

  override def createJob(observableId: String, inputJob: InputJob): Try[IdMapping] =
    authTransaction(inputJob.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.info(s"Create job ${inputJob.job.cortexId}:${inputJob.job.workerName}:${inputJob.job.cortexJobId}")
      for {
        observable <- observableSrv.getOrFail(observableId)
        job        <- jobSrv.create(inputJob.job, observable)
      } yield IdMapping(inputJob.metaData.id, job._id)
    }

  override def createJobObservable(jobId: String, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.info(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in job $jobId")
      for {
        job            <- jobSrv.getOrFail(jobId)
        observableType <- observableTypeSrv.getOrFail(inputObservable.`type`)
        richObservable <- inputObservable.dataOrAttachment match {
          case Right(inputAttachment) =>
            attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap {
              attachment =>
                observableSrv.create(inputObservable.observable, observableType, attachment, inputObservable.tags, Nil)
            }
          case Left(data) => observableSrv.create(inputObservable.observable, observableType, data, inputObservable.tags, Nil)
        }
        _ <- jobSrv.addObservable(job, richObservable.observable)
      } yield IdMapping(inputObservable.metaData.id, richObservable._id)
    }

  def getEntity(entityType: String, entityId: String)(implicit graph: Graph): Try[Entity] = entityType match {
    case "Task"       => taskSrv.getOrFail(entityId)
    case "Case"       => caseSrv.getOrFail(entityId)
    case "Observable" => observableSrv.getOrFail(entityId)
    case "Log"        => logSrv.getOrFail(entityId)
    case "Alert"      => alertSrv.getOrFail(entityId)
    case _            => Failure(BadRequestError(s"objectType $entityType is not recognised"))
  }

  override def createAction(objectId: String, inputAction: InputAction): Try[IdMapping] = authTransaction(inputAction.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(
        s"Create action ${inputAction.action.cortexId}:${inputAction.action.workerName}:${inputAction.action.cortexJobId} for ${inputAction.objectType} $objectId"
      )
      for {
        entity <- getEntity(inputAction.objectType, objectId)
        action <- actionSrv.create(inputAction.action, entity)
      } yield IdMapping(inputAction.metaData.id, action._id)
  }

  override def createAudit(contextId: String, inputAudit: InputAudit): Try[Unit] = authTransaction(inputAudit.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.info(s"Create audit ${inputAudit.audit.action} on ${inputAudit.audit.objectType} ${inputAudit.audit.objectId}")
      for {
        obj <- (for {
          t <- inputAudit.audit.objectType
          i <- inputAudit.audit.objectId
        } yield getEntity(t, i)).flip
        ctxType = obj.map(_._model.label).map {
          case "Log"   => "Task"
          case "Share" => "Case"
          case other   => other
        }
        context <- ctxType.map(getEntity(_, contextId)).flip
        _       <- auditSrv.create(inputAudit.audit, context, obj)
      } yield ()
  }
}
