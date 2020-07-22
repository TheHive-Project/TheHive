package org.thp.thehive.migration.th4

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.Guice
import gremlin.scala._
import javax.inject.{Inject, Named, Provider, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.thp.scalligraph._
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, UserSrv => UserDB}
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, Entity, Schema, UniMapping}
import org.thp.scalligraph.services.{DatabaseStorageSrv, HadoopStorageSrv, LocalFileSystemStorageSrv, S3StorageSrv, StorageSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.connector.cortex.models.{CortexSchemaDefinition, TheHiveCortexSchemaProvider}
import org.thp.thehive.connector.cortex.services.{ActionSrv, JobSrv}
import org.thp.thehive.migration
import org.thp.thehive.migration.IdMapping
import org.thp.thehive.migration.dto._
import org.thp.thehive.models._
import org.thp.thehive.services.{
  AlertSrv,
  AttachmentSrv,
  AuditSrv,
  CaseSrv,
  CaseTemplateSrv,
  CustomFieldSrv,
  DataSrv,
  ImpactStatusSrv,
  LocalUserSrv,
  LogSrv,
  ObservableSrv,
  ObservableTypeSrv,
  OrganisationSrv,
  ProfileSrv,
  ResolutionStatusSrv,
  ShareSrv,
  TagSrv,
  TaskSrv,
  UserSrv
}
import play.api.cache.SyncCacheApi
import play.api.cache.ehcache.EhCacheModule
import play.api.inject.guice.GuiceInjector
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle, Injector}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Environment, Logger}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object Output {

  private def buildApp(configuration: Configuration)(implicit actorSystem: ActorSystem) =
    Guice
      .createInjector(
        (play.api.inject.guice.GuiceableModule.guiceable(new EhCacheModule).guiced(Environment.simple(), configuration, Set.empty) :+
          new ScalaModule with AkkaGuiceSupport {
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
              bindActor[DummyActor]("integrity-check-actor")

              bind[AuditSrv].to[NoAuditSrv]
              bind[Database].to[JanusDatabase]
              bind[Database].annotatedWithName("with-thehive-schema").toProvider[BasicDatabaseProvider]
              bind[Database].annotatedWithName("with-thehive-cortex-schema").toProvider[BasicDatabaseProvider]
              bind[Configuration].toInstance(configuration)
              bind[Environment].toInstance(Environment.simple())
              bind[ApplicationLifecycle].to[DefaultApplicationLifecycle]
              bind[Schema].toProvider[TheHiveCortexSchemaProvider]
              configuration.get[String]("storage.provider") match {
                case "localfs"  => bind(classOf[StorageSrv]).to(classOf[LocalFileSystemStorageSrv])
                case "database" => bind(classOf[StorageSrv]).to(classOf[DatabaseStorageSrv])
                case "hdfs"     => bind(classOf[StorageSrv]).to(classOf[HadoopStorageSrv])
                case "s3"       => bind(classOf[StorageSrv]).to(classOf[S3StorageSrv])
              }
              ()
            }
          }).asJava
      )

  def apply(configuration: Configuration)(implicit actorSystem: ActorSystem): Output = {
    if (configuration.getOptional[Boolean]("dropDatabase").contains(true)) {
      Logger(getClass).info("Drop database")
      new JanusDatabase(configuration, actorSystem).drop()
    }
    buildApp(configuration).getInstance(classOf[Output])
  }
}

@Singleton
class BasicDatabaseProvider @Inject() (database: Database) extends Provider[Database] {
  override def get(): Database = database
}

@Singleton
class Output @Inject() (
    theHiveSchema: TheHiveSchemaDefinition,
    cortexSchema: CortexSchemaDefinition,
    caseSrv: CaseSrv,
    observableSrvProvider: Provider[ObservableSrv],
    dataSrv: DataSrv,
    userSrv: UserSrv,
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
    auditSrv: AuditSrv,
    customFieldSrv: CustomFieldSrv,
    impactStatusSrv: ImpactStatusSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    jobSrv: JobSrv,
    actionSrv: ActionSrv,
    @Named("with-thehive-schema") db: Database,
    cache: SyncCacheApi
) extends migration.Output {
  lazy val logger: Logger = Logger(getClass)
  val defaultUserDomain: String = userSrv
    .defaultUserDomain
    .getOrElse(
      throw BadConfigurationError("Default user domain is empty in configuration. Please add `auth.defaultUserDomain` in your configuration file.")
    )
  lazy val observableSrv: ObservableSrv                                     = observableSrvProvider.get
  private var profiles: Map[String, Profile with Entity]                    = Map.empty
  private var organisations: Map[String, Organisation with Entity]          = Map.empty
  private var users: Map[String, User with Entity]                          = Map.empty
  private var impactStatuses: Map[String, ImpactStatus with Entity]         = Map.empty
  private var resolutionStatuses: Map[String, ResolutionStatus with Entity] = Map.empty
  private var observableTypes: Map[String, ObservableType with Entity]      = Map.empty
  private var customFields: Map[String, CustomField with Entity]            = Map.empty
  private var caseTemplates: Map[String, CaseTemplate with Entity]          = Map.empty
  private var caseNumbers: Set[Int]                                         = Set.empty
  private var alerts: Set[(String, String, String)]                         = Set.empty

  private def retrieveExistingData(): Unit = {
    val profilesBuilder           = Map.newBuilder[String, Profile with Entity]
    val organisationsBuilder      = Map.newBuilder[String, Organisation with Entity]
    val usersBuilder              = Map.newBuilder[String, User with Entity]
    val impactStatusesBuilder     = Map.newBuilder[String, ImpactStatus with Entity]
    val resolutionStatusesBuilder = Map.newBuilder[String, ResolutionStatus with Entity]
    val observableTypesBuilder    = Map.newBuilder[String, ObservableType with Entity]
    val customFieldsBuilder       = Map.newBuilder[String, CustomField with Entity]
    val caseTemplatesBuilder      = Map.newBuilder[String, CaseTemplate with Entity]
    val caseNumbersBuilder        = Set.newBuilder[Int]
    val alertsBuilder             = Set.newBuilder[(String, String, String)]

    db.roTransaction { graph =>
      graph
        .V()
        .has(
          Key[String]("_label"),
          P.within(
            Seq(
              "Profile",
              "Organisation",
              "User",
              "ImpactStatus",
              "ResolutionStatus",
              "ObservableType",
              "CustomField",
              "CaseTemplate",
              "Case",
              "Alert"
            )
          )
        )
        .toIterator()
        .map(v => v.value[String]("_label") -> v)
        .foreach {
          case ("Profile", vertex) =>
            val profile = profileSrv.model.toDomain(vertex)(db)
            profilesBuilder += (profile.name -> profile)
          case ("Organisation", vertex) =>
            val organisation = organisationSrv.model.toDomain(vertex)(db)
            organisationsBuilder += (organisation.name -> organisation)
          case ("User", vertex) =>
            val user = userSrv.model.toDomain(vertex)(db)
            usersBuilder += (user.login -> user)
          case ("ImpactStatus", vertex) =>
            val impactStatuse = impactStatusSrv.model.toDomain(vertex)(db)
            impactStatusesBuilder += (impactStatuse.value -> impactStatuse)
          case ("ResolutionStatus", vertex) =>
            val resolutionStatuse = resolutionStatusSrv.model.toDomain(vertex)(db)
            resolutionStatusesBuilder += (resolutionStatuse.value -> resolutionStatuse)
          case ("ObservableType", vertex) =>
            val observableType = observableTypeSrv.model.toDomain(vertex)(db)
            observableTypesBuilder += (observableType.name -> observableType)
          case ("CustomField", vertex) =>
            val customField = customFieldSrv.model.toDomain(vertex)(db)
            customFieldsBuilder += (customField.name -> customField)
          case ("CaseTemplate", vertex) =>
            val caseTemplate = caseTemplateSrv.model.toDomain(vertex)(db)
            caseTemplatesBuilder += (caseTemplate.name -> caseTemplate)
          case ("Case", vertex) =>
            caseNumbersBuilder += db.getSingleProperty(vertex, "number", UniMapping.int)
          case ("Alert", vertex) =>
            val `type`    = db.getSingleProperty(vertex, "type", UniMapping.string)
            val source    = db.getSingleProperty(vertex, "source", UniMapping.string)
            val sourceRef = db.getSingleProperty(vertex, "sourceRef", UniMapping.string)
            alertsBuilder += ((`type`, source, sourceRef))
          case _ =>
        }
    }
    profiles = profilesBuilder.result()
    organisations = organisationsBuilder.result()
    users = usersBuilder.result()
    impactStatuses = impactStatusesBuilder.result()
    resolutionStatuses = resolutionStatusesBuilder.result()
    observableTypes = observableTypesBuilder.result()
    customFields = customFieldsBuilder.result()
    caseTemplates = caseTemplatesBuilder.result()
    caseNumbers = caseNumbersBuilder.result()
    alerts = alertsBuilder.result()
  }

  def startMigration(): Try[Unit] = {
    db match {
      case jdb: JanusDatabase => jdb.dropOtherConnections.recover { case error => logger.error(s"Fail to remove other connection", error) }
      case _                  =>
    }
    if (db.version("thehive") == 0) {
      db.createSchemaFrom(theHiveSchema)(LocalUserSrv.getSystemAuthContext)
        .flatMap(_ => db.setVersion(theHiveSchema.name, theHiveSchema.operations.lastVersion))
        .flatMap(_ => db.createSchemaFrom(cortexSchema)(LocalUserSrv.getSystemAuthContext))
        .flatMap(_ => db.setVersion(cortexSchema.name, cortexSchema.operations.lastVersion))
        .map(_ => retrieveExistingData())
    } else {
      theHiveSchema
        .update(db)(LocalUserSrv.getSystemAuthContext)
        .flatMap(_ => cortexSchema.update(db)(LocalUserSrv.getSystemAuthContext))
        .map { _ =>
          retrieveExistingData()
          db.removeAllIndexes()
        }
    }
  }

  def endMigration(): Try[Unit] = {
    db.addSchemaIndexes(theHiveSchema)
      .flatMap(_ => db.addSchemaIndexes(cortexSchema))
    Try(db.close())
  }

  // TODO check integrity

  implicit class RichTry[A](t: Try[A]) {
    def logFailure(message: String): Unit = t.failed.foreach(error => logger.warn(s"$message: $error"))
  }

  def updateMetaData(entity: Entity, metaData: MetaData)(implicit graph: Graph): Unit = {
    val e1 = graph.V(entity._id).property(Key[Date]("_createdAt"), metaData.createdAt)
    val e2 = metaData.updatedAt.fold(e1)(e1.property(Key[Date]("_updatedAt"), _))
    metaData.updatedAt.foreach(e2.property(Key[Date]("_updatedAt"), _))
  }

  def getAuthContext(userId: String): AuthContext =
    if (userId.startsWith("init@"))
      LocalUserSrv.getSystemAuthContext
    else if (userId.contains('@')) AuthContextImpl(userId, userId, "admin", "mig-request", Permissions.all)
    else AuthContextImpl(s"$userId@$defaultUserDomain", s"$userId@$defaultUserDomain", "admin", "mig-request", Permissions.all)

  def authTransaction[A](userId: String)(body: Graph => AuthContext => Try[A]): Try[A] = db.tryTransaction { implicit graph =>
    body(graph)(getAuthContext(userId))
  }

  def getTag(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    cache.getOrElseUpdate(s"tag-$tagName")(tagSrv.createEntity(Tag.fromString(tagName, tagSrv.defaultNamespace, tagSrv.defaultColour)))

  override def organisationExists(inputOrganisation: InputOrganisation): Boolean = organisations.contains(inputOrganisation.organisation.name)

  private def getOrganisation(organisationName: String): Try[Organisation with Entity] =
    organisations
      .get(organisationName)
      .fold[Try[Organisation with Entity]](Failure(NotFoundError(s"Organisation $organisationName not found")))(Success.apply)

  override def createOrganisation(inputOrganisation: InputOrganisation): Try[IdMapping] = authTransaction(inputOrganisation.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create organisation ${inputOrganisation.organisation.name}")
      organisationSrv.create(inputOrganisation.organisation).map { o =>
        updateMetaData(o, inputOrganisation.metaData)
        organisations += (o.name -> o)
        IdMapping(inputOrganisation.metaData.id, o._id)
      }
  }

  override def userExists(inputUser: InputUser): Boolean = {
    val validLogin =
      if (inputUser.user.login.contains('@')) inputUser.user.login.toLowerCase
      else s"${inputUser.user.login}@$defaultUserDomain".toLowerCase
    users.contains(validLogin)
  }

  private def getUser(login: String): Try[User with Entity] = {
    val validLogin =
      if (login.contains('@')) login.toLowerCase
      else s"$login@$defaultUserDomain".toLowerCase
    users
      .get(validLogin)
      .fold[Try[User with Entity]](Failure(NotFoundError(s"User $login not found")))(Success.apply)
  }

  override def createUser(inputUser: InputUser): Try[IdMapping] = authTransaction(inputUser.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create user ${inputUser.user.login}")
      userSrv.checkUser(inputUser.user).flatMap(userSrv.createEntity).map { createdUser =>
        updateMetaData(createdUser, inputUser.metaData)
        inputUser
          .avatar
          .foreach { inputAttachment =>
            attachmentSrv
              .create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data)
              .flatMap { attachment =>
                userSrv.setAvatar(createdUser, attachment)
              }
              .logFailure(s"Unable to set avatar to user ${createdUser.login}")
          }
        inputUser.organisations.foreach {
          case (organisationName, profileName) =>
            (for {
              organisation <- getOrganisation(organisationName)
              profile      <- getProfile(profileName)
              _            <- userSrv.addUserToOrganisation(createdUser, organisation, profile)
            } yield ()).logFailure(s"Unable to put user ${createdUser.login} in organisation $organisationName with profile $profileName")
        }
        users += (createdUser.login -> createdUser)
        IdMapping(inputUser.metaData.id, createdUser._id)
      }
  }

  override def customFieldExists(inputCustomField: InputCustomField): Boolean = customFields.contains(inputCustomField.customField.name)

  private def getCustomField(name: String): Try[CustomField with Entity] =
    customFields.get(name).fold[Try[CustomField with Entity]](Failure(NotFoundError(s"Custom field $name not found")))(Success.apply)

  override def createCustomField(inputCustomField: InputCustomField): Try[IdMapping] = authTransaction(inputCustomField.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create custom field ${inputCustomField.customField.name}")
      customFieldSrv.create(inputCustomField.customField).map { cf =>
        updateMetaData(cf, inputCustomField.metaData)
        customFields += (cf.name -> cf)
        IdMapping(inputCustomField.customField.name, cf._id)
      }
  }

  override def observableTypeExists(inputObservableType: InputObservableType): Boolean =
    observableTypes.contains(inputObservableType.observableType.name)

  def getObservableType(typeName: String)(implicit graph: Graph, authContext: AuthContext): Try[ObservableType with Entity] =
    observableTypes
      .get(typeName)
      .fold[Try[ObservableType with Entity]] {
        observableTypeSrv.create(ObservableType(typeName, isAttachment = false)).map { ot =>
          observableTypes += (typeName -> ot)
          ot
        }
      }(Success.apply)

  override def createObservableTypes(inputObservableType: InputObservableType): Try[IdMapping] =
    authTransaction(inputObservableType.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create observable types ${inputObservableType.observableType.name}")
      observableTypeSrv.create(inputObservableType.observableType).map { ot =>
        updateMetaData(ot, inputObservableType.metaData)
        observableTypes += (ot.name -> ot)
        IdMapping(inputObservableType.observableType.name, ot._id)
      }
    }

  override def profileExists(inputProfile: InputProfile): Boolean = profiles.contains(inputProfile.profile.name)

  private def getProfile(profileName: String)(implicit graph: Graph, authContext: AuthContext): Try[Profile with Entity] =
    profiles
      .get(profileName)
      .fold[Try[Profile with Entity]] {
        profileSrv.createEntity(Profile(profileName, Set.empty)).map { p =>
          profiles += (profileName -> p)
          p
        }
      }(Success.apply)

  override def createProfile(inputProfile: InputProfile): Try[IdMapping] = authTransaction(inputProfile.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create profile ${inputProfile.profile.name}")
      profileSrv.create(inputProfile.profile).map { profile =>
        updateMetaData(profile, inputProfile.metaData)
        profiles += (profile.name -> profile)
        IdMapping(inputProfile.profile.name, profile._id)
      }
  }

  override def impactStatusExists(inputImpactStatus: InputImpactStatus): Boolean = impactStatuses.contains(inputImpactStatus.impactStatus.value)

  private def getImpactStatus(name: String)(implicit graph: Graph, authContext: AuthContext): Try[ImpactStatus with Entity] =
    impactStatuses
      .get(name)
      .fold[Try[ImpactStatus with Entity]] {
        impactStatusSrv.createEntity(ImpactStatus(name)).map { is =>
          impactStatuses += (name -> is)
          is
        }
      }(Success.apply)

  override def createImpactStatus(inputImpactStatus: InputImpactStatus): Try[IdMapping] = authTransaction(inputImpactStatus.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create impact status ${inputImpactStatus.impactStatus.value}")
      impactStatusSrv.create(inputImpactStatus.impactStatus).map { status =>
        updateMetaData(status, inputImpactStatus.metaData)
        impactStatuses += (status.value -> status)
        IdMapping(inputImpactStatus.impactStatus.value, status._id)
      }
  }

  override def resolutionStatusExists(inputResolutionStatus: InputResolutionStatus): Boolean =
    resolutionStatuses.contains(inputResolutionStatus.resolutionStatus.value)

  private def getResolutionStatus(name: String)(implicit graph: Graph, authContext: AuthContext): Try[ResolutionStatus with Entity] =
    resolutionStatuses
      .get(name)
      .fold[Try[ResolutionStatus with Entity]] {
        resolutionStatusSrv.createEntity(ResolutionStatus(name)).map { rs =>
          resolutionStatuses += (name -> rs)
          rs
        }
      }(Success.apply)

  override def createResolutionStatus(inputResolutionStatus: InputResolutionStatus): Try[IdMapping] =
    authTransaction(inputResolutionStatus.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create resolution status ${inputResolutionStatus.resolutionStatus.value}")
      resolutionStatusSrv
        .create(inputResolutionStatus.resolutionStatus)
        .map { status =>
          updateMetaData(status, inputResolutionStatus.metaData)
          resolutionStatuses += (status.value -> status)
          IdMapping(inputResolutionStatus.resolutionStatus.value, status._id)
        }
    }

  override def caseTemplateExists(inputCaseTemplate: InputCaseTemplate): Boolean = caseTemplates.contains(inputCaseTemplate.caseTemplate.name)

  private def getCaseTemplate(name: String): Option[CaseTemplate with Entity] = caseTemplates.get(name)

  override def createCaseTemplate(inputCaseTemplate: InputCaseTemplate): Try[IdMapping] = authTransaction(inputCaseTemplate.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create case template ${inputCaseTemplate.caseTemplate.name}")
      for {
        organisation     <- getOrganisation(inputCaseTemplate.organisation)
        tags             <- inputCaseTemplate.tags.toTry(getTag)
        richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate.caseTemplate, organisation, tags, Nil, Nil)
        _ = updateMetaData(richCaseTemplate.caseTemplate, inputCaseTemplate.metaData)
        _ = inputCaseTemplate.customFields.foreach {
          case (name, value, order) =>
            (for {
              cf  <- getCustomField(name)
              ccf <- CustomFieldType.map(cf.`type`).setValue(CaseTemplateCustomField(order = order), value)
              _   <- caseTemplateSrv.caseTemplateCustomFieldSrv.create(ccf, richCaseTemplate.caseTemplate, cf)
            } yield ()).logFailure(s"Unable to set custom field $name=${value.getOrElse("<not set>")}")
        }
        _ = caseTemplates += (inputCaseTemplate.caseTemplate.name -> richCaseTemplate.caseTemplate)
      } yield IdMapping(inputCaseTemplate.metaData.id, richCaseTemplate._id)
  }

  override def createCaseTemplateTask(caseTemplateId: String, inputTask: InputTask): Try[IdMapping] = authTransaction(inputTask.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create task ${inputTask.task.title} in case template $caseTemplateId")
      for {
        caseTemplate <- caseTemplateSrv.getOrFail(caseTemplateId)
        taskOwner = inputTask.owner.flatMap(getUser(_).toOption)
        richTask <- taskSrv.create(inputTask.task, taskOwner)
        _ = updateMetaData(richTask.task, inputTask.metaData)
        _ <- caseTemplateSrv.addTask(caseTemplate, richTask.task)
      } yield IdMapping(inputTask.metaData.id, richTask._id)
  }

  override def caseExists(inputCase: InputCase): Boolean = caseNumbers.contains(inputCase.`case`.number)

  private def getCase(caseId: String)(implicit graph: Graph): Try[Case with Entity] = caseSrv.getByIds(caseId).getOrFail()

  override def createCase(inputCase: InputCase): Try[IdMapping] =
    authTransaction(inputCase.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create case #${inputCase.`case`.number}")
      caseSrv.createEntity(inputCase.`case`).map { createdCase =>
        updateMetaData(createdCase, inputCase.metaData)
        inputCase
          .user
          .foreach { userLogin =>
            getUser(userLogin)
              .flatMap(user => caseSrv.caseUserSrv.create(CaseUser(), createdCase, user))
              .logFailure(s"Unable to assign case #${createdCase.number} to $userLogin")
          }
        inputCase
          .caseTemplate
          .flatMap(getCaseTemplate)
          .foreach { ct =>
            caseSrv
              .caseCaseTemplateSrv
              .create(CaseCaseTemplate(), createdCase, ct)
              .logFailure(s"Unable to set case template ${ct.name} to case #${createdCase.number}")
          }
        inputCase.customFields.foreach {
          case (name, value) => // TODO Add order
            getCustomField(name)
              .flatMap { cf =>
                CustomFieldType
                  .map(cf.`type`)
                  .setValue(CaseCustomField(), value)
                  .flatMap(ccf => caseSrv.caseCustomFieldSrv.create(ccf, createdCase, cf))
              }
              .logFailure(s"Unable to set custom field $name=${value.getOrElse("<not set>")} to case #${createdCase.number}")
        }
        inputCase.organisations.foldLeft(false) {
          case (ownerSet, (organisationName, profileName)) =>
            val owner = profileName == profileSrv.orgAdmin.name && !ownerSet
            val shared = for {
              organisation <- getOrganisation(organisationName)
              profile      <- getProfile(profileName)
              _            <- shareSrv.shareCase(owner, createdCase, organisation, profile)
            } yield ()
            shared.logFailure(s"Unable to share case #${createdCase.number} with organisation $organisationName, profile $profileName")
            ownerSet || owner
        }
        inputCase.tags.filterNot(_.isEmpty).foreach { tagName =>
          getTag(tagName)
            .flatMap(tag => caseSrv.caseTagSrv.create(CaseTag(), createdCase, tag))
            .logFailure(s"Unable to add tag $tagName to case #${createdCase.number}")
        }
        inputCase
          .resolutionStatus
          .foreach { resolutionStatus =>
            getResolutionStatus(resolutionStatus)
              .flatMap(caseSrv.caseResolutionStatusSrv.create(CaseResolutionStatus(), createdCase, _))
              .logFailure(s"Unable to set resolution status $resolutionStatus to case #${createdCase.number}")
          }
        inputCase
          .impactStatus
          .foreach { impactStatus =>
            getImpactStatus(impactStatus)
              .flatMap(caseSrv.caseImpactStatusSrv.create(CaseImpactStatus(), createdCase, _))
              .logFailure(s"Unable to set impact status $impactStatus to case #${createdCase.number}")
          }

        IdMapping(inputCase.metaData.id, createdCase._id)
      }
    }

  override def createCaseTask(caseId: String, inputTask: InputTask): Try[IdMapping] =
    authTransaction(inputTask.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create task ${inputTask.task.title} in case $caseId")
      val owner = inputTask.owner.flatMap(getUser(_).toOption)
      for {
        richTask <- taskSrv.create(inputTask.task, owner)
        _ = updateMetaData(richTask.task, inputTask.metaData)
        case0 <- getCase(caseId)
        _ <- inputTask.organisations.toTry { organisation =>
          getOrganisation(organisation).flatMap(shareSrv.shareTask(richTask, case0, _))
        }
      } yield IdMapping(inputTask.metaData.id, richTask._id)
    }

  def createCaseTaskLog(taskId: String, inputLog: InputLog): Try[IdMapping] =
    authTransaction(inputLog.metaData.createdBy) { implicit graph => implicit authContext =>
      for {
        task <- taskSrv.getOrFail(taskId)
        _ = logger.debug(s"Create log in task ${task.title}")
        log <- logSrv.create(inputLog.log, task)
        _ = updateMetaData(log, inputLog.metaData)
        _ <- inputLog.attachments.toTry { inputAttachment =>
          attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap { attachment =>
            logSrv.addAttachment(log, attachment)
          }
        }
      } yield IdMapping(inputLog.metaData.id, log._id)
    }

  override def createCaseObservable(caseId: String, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in case $caseId")
      for {
        observableType <- getObservableType(inputObservable.`type`)
        tags           <- inputObservable.tags.filterNot(_.isEmpty).toTry(getTag)
        richObservable <- inputObservable
          .dataOrAttachment
          .fold(
            { dataValue =>
              dataSrv.createEntity(Data(dataValue)).flatMap { data =>
                observableSrv.create(inputObservable.observable, observableType, data, tags, Nil)
              }
            }, { inputAttachment =>
              attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap {
                attachment =>
                  observableSrv.create(inputObservable.observable, observableType, attachment, tags, Nil)
              }
            }
          )
        _ = updateMetaData(richObservable.observable, inputObservable.metaData)
        case0 <- getCase(caseId)
        orgs  <- inputObservable.organisations.toTry(getOrganisation)
        _     <- orgs.toTry(o => shareSrv.shareObservable(richObservable, case0, o))
      } yield IdMapping(inputObservable.metaData.id, richObservable._id)
    }

  override def createJob(observableId: String, inputJob: InputJob): Try[IdMapping] =
    authTransaction(inputJob.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create job ${inputJob.job.cortexId}:${inputJob.job.workerName}:${inputJob.job.cortexJobId}")
      for {
        observable <- observableSrv.getOrFail(observableId)
        job        <- jobSrv.create(inputJob.job, observable)
        _ = updateMetaData(job.job, inputJob.metaData)
      } yield IdMapping(inputJob.metaData.id, job._id)
    }

  override def createJobObservable(jobId: String, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in job $jobId")
      for {
        job            <- jobSrv.getOrFail(jobId)
        observableType <- getObservableType(inputObservable.`type`)
        tags = inputObservable.tags.filterNot(_.isEmpty).flatMap(getTag(_).toOption).toSeq
        richObservable <- inputObservable
          .dataOrAttachment
          .fold(
            { dataValue =>
              dataSrv.createEntity(Data(dataValue)).flatMap { data =>
                observableSrv.create(inputObservable.observable, observableType, data, tags, Nil)
              }
            }, { inputAttachment =>
              attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap {
                attachment =>
                  observableSrv.create(inputObservable.observable, observableType, attachment, tags, Nil)
              }
            }
          )
        _ = updateMetaData(richObservable.observable, inputObservable.metaData)
        _ <- jobSrv.addObservable(job, richObservable.observable)
      } yield IdMapping(inputObservable.metaData.id, richObservable._id)
    }

  override def alertExists(inputAlert: InputAlert): Boolean =
    alerts.contains((inputAlert.alert.`type`, inputAlert.alert.source, inputAlert.alert.sourceRef))

  override def createAlert(inputAlert: InputAlert): Try[IdMapping] = authTransaction(inputAlert.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create alert ${inputAlert.alert.`type`}:${inputAlert.alert.source}:${inputAlert.alert.sourceRef}")
      for {
        organisation <- getOrganisation(inputAlert.organisation)
        caseTemplate = inputAlert
          .caseTemplate
          .flatMap(ct =>
            getCaseTemplate(ct).orElse {
              logger.warn(
                s"Case template $ct not found (used in alert ${inputAlert.alert.`type`}:${inputAlert.alert.source}:${inputAlert.alert.sourceRef})"
              )
              None
            }
          )
        tags = inputAlert.tags.filterNot(_.isEmpty).flatMap(getTag(_).toOption).toSeq
        alert <- alertSrv.create(inputAlert.alert, organisation, tags, inputAlert.customFields, caseTemplate)
        _ = updateMetaData(alert.alert, inputAlert.metaData)
        _ = inputAlert.caseId.flatMap(getCase(_).toOption).foreach(alertSrv.alertCaseSrv.create(AlertCase(), alert.alert, _))
      } yield IdMapping(inputAlert.metaData.id, alert._id)
  }

  override def createAlertObservable(alertId: String, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in alert $alertId")
      for {
        observableType <- getObservableType(inputObservable.`type`)
        tags = inputObservable.tags.filterNot(_.isEmpty).flatMap(getTag(_).toOption).toSeq
        richObservable <- inputObservable
          .dataOrAttachment
          .fold(
            { dataValue =>
              dataSrv.createEntity(Data(dataValue)).flatMap { data =>
                observableSrv.create(inputObservable.observable, observableType, data, tags, Nil)
              }
            }, { inputAttachment =>
              attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap {
                attachment =>
                  observableSrv.create(inputObservable.observable, observableType, attachment, tags, Nil)
              }
            }
          )
        _ = updateMetaData(richObservable.observable, inputObservable.metaData)
        alert <- alertSrv.getOrFail(alertId)
        _     <- alertSrv.alertObservableSrv.create(AlertObservable(), alert, richObservable.observable)
      } yield IdMapping(inputObservable.metaData.id, richObservable._id)
    }

  private def getEntity(entityType: String, entityId: String)(implicit graph: Graph): Try[Entity] = entityType match {
    case "Task"       => taskSrv.getOrFail(entityId)
    case "Case"       => getCase(entityId)
    case "Observable" => observableSrv.getOrFail(entityId)
    case "Log"        => logSrv.getOrFail(entityId)
    case "Alert"      => alertSrv.getOrFail(entityId)
    case "Job"        => jobSrv.getOrFail(entityId)
    case _            => Failure(BadRequestError(s"objectType $entityType is not recognised"))
  }

  override def createAction(objectId: String, inputAction: InputAction): Try[IdMapping] = authTransaction(inputAction.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(
        s"Create action ${inputAction.action.cortexId}:${inputAction.action.workerName}:${inputAction.action.cortexJobId} for ${inputAction.objectType} $objectId"
      )
      for {
        entity <- getEntity(inputAction.objectType, objectId)
        action <- actionSrv.create(inputAction.action, entity)
        _ = updateMetaData(action.action, inputAction.metaData)
      } yield IdMapping(inputAction.metaData.id, action._id)
  }

  override def createAudit(contextId: String, inputAudit: InputAudit): Try[Unit] = authTransaction(inputAudit.metaData.createdBy) {
    implicit graph => implicit authContext =>
      logger.debug(s"Create audit ${inputAudit.audit.action} on ${inputAudit.audit.objectType} ${inputAudit.audit.objectId}")
      for {
        obj <- (for {
          t <- inputAudit.audit.objectType
          i <- inputAudit.audit.objectId
        } yield getEntity(t, i)).flip
        ctxType = obj.map(_._model.label).map {
          case "Alert"                                        => "Alert"
          case "Log" | "Task" | "Observable" | "Case" | "Job" => "Case"
          case "User"                                         => "User"
          case other =>
            logger.error(s"Unknown object type: $other")
            other
        }
        context      <- ctxType.map(getEntity(_, contextId)).flip
        user         <- getUser(authContext.userId)
        createdAudit <- auditSrv.createEntity(inputAudit.audit)
        _ = updateMetaData(createdAudit, inputAudit.metaData)
        _ <- auditSrv.auditUserSrv.create(AuditUser(), createdAudit, user)
        _ <- obj.map(auditSrv.auditedSrv.create(Audited(), createdAudit, _)).flip
        _ <- context.map(auditSrv.auditContextSrv.create(AuditContext(), createdAudit, _)).flip
      } yield ()
  }
}
