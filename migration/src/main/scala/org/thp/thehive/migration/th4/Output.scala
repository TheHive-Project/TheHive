package org.thp.thehive.migration.th4

import akka.actor.ActorSystem
import akka.actor.typed.Scheduler
import akka.stream.Materializer
import com.google.inject.Guice
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph._
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, UserSrv => UserDB}
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.thehive.connector.cortex.models.{CortexSchemaDefinition, TheHiveCortexSchemaProvider}
import org.thp.thehive.connector.cortex.services.{ActionSrv, JobSrv}
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.{migration, ClusterSetup}
import org.thp.thehive.migration.IdMapping
import org.thp.thehive.migration.dto._
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.cache.SyncCacheApi
import play.api.cache.ehcache.EhCacheModule
import play.api.inject.guice.GuiceInjector
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle, Injector}
import play.api.libs.concurrent.{AkkaGuiceSupport, AkkaSchedulerProvider}
import play.api.{Configuration, Environment, Logger}

import javax.inject.{Inject, Provider, Singleton}
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
              bind[Scheduler].toProvider[AkkaSchedulerProvider]
              bind[Materializer].toInstance(Materializer(actorSystem))
              bind[ExecutionContext].toInstance(actorSystem.dispatcher)
              bind[Injector].to[GuiceInjector]
              bind[UserDB].to[LocalUserSrv]
              bindActor[DummyActor]("notification-actor")
              bindActor[DummyActor]("config-actor")
              bindActor[DummyActor]("cortex-actor")
              bindActor[DummyActor]("integrity-check-actor")

              val schemaBindings = ScalaMultibinder.newSetBinder[UpdatableSchema](binder)
              schemaBindings.addBinding.to[TheHiveSchemaDefinition]
              schemaBindings.addBinding.to[CortexSchemaDefinition]
              bind[SingleInstance].toInstance(new SingleInstance(true))

              bind[AuditSrv].to[NoAuditSrv]
              bind[Database].toProvider[JanusDatabaseProvider]
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
              bind[ClusterSetup].asEagerSingleton()
              ()
            }
          }).asJava
      )

  def apply(configuration: Configuration)(implicit actorSystem: ActorSystem): Output = {
    if (configuration.getOptional[Boolean]("dropDatabase").contains(true)) {
      Logger(getClass).info("Drop database")
      new JanusDatabase(configuration, actorSystem, fullTextIndexAvailable = false).drop()
    }
    buildApp(configuration).getInstance(classOf[Output])
  }
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
    db: Database,
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

    db.roTransaction { implicit graph =>
      Traversal
        .V()
        .unsafeHas(
          "_label",
          P.within(
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
        .toIterator
        .map(v => v.value[String]("_label") -> v)
        .foreach {
          case ("Profile", vertex) =>
            val profile = profileSrv.model.converter(vertex)
            profilesBuilder += (profile.name -> profile)
          case ("Organisation", vertex) =>
            val organisation = organisationSrv.model.converter(vertex)
            organisationsBuilder += (organisation.name -> organisation)
          case ("User", vertex) =>
            val user = userSrv.model.converter(vertex)
            usersBuilder += (user.login -> user)
          case ("ImpactStatus", vertex) =>
            val impactStatuse = impactStatusSrv.model.converter(vertex)
            impactStatusesBuilder += (impactStatuse.value -> impactStatuse)
          case ("ResolutionStatus", vertex) =>
            val resolutionStatuse = resolutionStatusSrv.model.converter(vertex)
            resolutionStatusesBuilder += (resolutionStatuse.value -> resolutionStatuse)
          case ("ObservableType", vertex) =>
            val observableType = observableTypeSrv.model.converter(vertex)
            observableTypesBuilder += (observableType.name -> observableType)
          case ("CustomField", vertex) =>
            val customField = customFieldSrv.model.converter(vertex)
            customFieldsBuilder += (customField.name -> customField)
          case ("CaseTemplate", vertex) =>
            val caseTemplate = caseTemplateSrv.model.converter(vertex)
            caseTemplatesBuilder += (caseTemplate.name -> caseTemplate)
          case ("Case", vertex) =>
            caseNumbersBuilder += UMapping.int.getProperty(vertex, "number")
          case ("Alert", vertex) =>
            val `type`    = UMapping.string.getProperty(vertex, "type")
            val source    = UMapping.string.getProperty(vertex, "source")
            val sourceRef = UMapping.string.getProperty(vertex, "sourceRef")
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
    if (
      profiles.nonEmpty ||
      organisations.nonEmpty ||
      users.nonEmpty ||
      impactStatuses.nonEmpty ||
      resolutionStatuses.nonEmpty ||
      observableTypes.nonEmpty ||
      customFields.nonEmpty ||
      caseTemplates.nonEmpty ||
      caseNumbers.nonEmpty ||
      alerts.nonEmpty
    )
      logger.info(s"""Already migrated:
                     | ${profiles.size} profiles
                     | ${organisations.size} organisations
                     | ${users.size} users
                     | ${impactStatuses.size} impactStatuses
                     | ${resolutionStatuses.size} resolutionStatuses
                     | ${observableTypes.size} observableTypes
                     | ${customFields.size} customFields
                     | ${caseTemplates.size} caseTemplates
                     | ${caseNumbers.size} caseNumbers
                     | ${alerts.size} alerts""".stripMargin)
  }

  def startMigration(): Try[Unit] =
//    db match {
//      case jdb: JanusDatabase => jdb.dropOtherConnections.recover { case error => logger.error(s"Fail to remove other connection", error) }
//      case _                  =>
//    }
    if (db.version("thehive") == 0)
      db.createSchemaFrom(theHiveSchema)(LocalUserSrv.getSystemAuthContext)
        .flatMap(_ => db.setVersion(theHiveSchema.name, theHiveSchema.operations.lastVersion))
        .flatMap(_ => db.createSchemaFrom(cortexSchema)(LocalUserSrv.getSystemAuthContext))
        .flatMap(_ => db.setVersion(cortexSchema.name, cortexSchema.operations.lastVersion))
        .map(_ => retrieveExistingData())
    else
      theHiveSchema
        .update(db)
        .flatMap(_ => cortexSchema.update(db))
        .map { _ =>
          retrieveExistingData()
          db.removeAllIndexes()
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
    val vertex = Traversal.V(entity._id).head
    UMapping.date.setProperty(vertex, "_createdAt", metaData.createdAt)
    UMapping.date.optional.setProperty(vertex, "_updatedAt", metaData.updatedAt)
  }

  def getAuthContext(userId: String): AuthContext =
    if (userId.startsWith("init@"))
      LocalUserSrv.getSystemAuthContext
    else if (userId.contains('@')) AuthContextImpl(userId, userId, EntityName("admin"), "mig-request", Permissions.all)
    else AuthContextImpl(s"$userId@$defaultUserDomain", s"$userId@$defaultUserDomain", EntityName("admin"), "mig-request", Permissions.all)

  def authTransaction[A](userId: String)(body: Graph => AuthContext => Try[A]): Try[A] =
    db.tryTransaction { implicit graph =>
      body(graph)(getAuthContext(userId))
    }

  def getTag(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    cache.getOrElseUpdate(s"tag-$tagName")(tagSrv.createEntity(Tag.fromString(tagName, tagSrv.defaultNamespace, tagSrv.defaultColour)))

  override def organisationExists(inputOrganisation: InputOrganisation): Boolean = organisations.contains(inputOrganisation.organisation.name)

  private def getOrganisation(organisationName: String): Try[Organisation with Entity] =
    organisations
      .get(organisationName)
      .fold[Try[Organisation with Entity]](Failure(NotFoundError(s"Organisation $organisationName not found")))(Success.apply)

  override def createOrganisation(inputOrganisation: InputOrganisation): Try[IdMapping] =
    authTransaction(inputOrganisation.metaData.createdBy) { implicit graph => implicit authContext =>
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
      .get(if (validLogin.startsWith("init@")) User.system.login else validLogin)
      .fold[Try[User with Entity]](Failure(NotFoundError(s"User $login not found")))(Success.apply)
  }

  override def createUser(inputUser: InputUser): Try[IdMapping] =
    authTransaction(inputUser.metaData.createdBy) { implicit graph => implicit authContext =>
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

  override def createCustomField(inputCustomField: InputCustomField): Try[IdMapping] =
    authTransaction(inputCustomField.metaData.createdBy) { implicit graph => implicit authContext =>
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
        observableTypeSrv.createEntity(ObservableType(typeName, isAttachment = false)).map { ot =>
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

  override def createProfile(inputProfile: InputProfile): Try[IdMapping] =
    authTransaction(inputProfile.metaData.createdBy) { implicit graph => implicit authContext =>
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

  override def createImpactStatus(inputImpactStatus: InputImpactStatus): Try[IdMapping] =
    authTransaction(inputImpactStatus.metaData.createdBy) { implicit graph => implicit authContext =>
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

  override def createCaseTemplate(inputCaseTemplate: InputCaseTemplate): Try[IdMapping] =
    authTransaction(inputCaseTemplate.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create case template ${inputCaseTemplate.caseTemplate.name}")
      for {
        organisation     <- getOrganisation(inputCaseTemplate.organisation)
        richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate.caseTemplate, organisation, Nil, Nil)
        _ = updateMetaData(richCaseTemplate.caseTemplate, inputCaseTemplate.metaData)
        _ = inputCaseTemplate.customFields.foreach {
          case InputCustomFieldValue(name, value, order) =>
            (for {
              cf  <- getCustomField(name)
              ccf <- CustomFieldType.map(cf.`type`).setValue(CaseTemplateCustomField(order = order), value)
              _   <- caseTemplateSrv.caseTemplateCustomFieldSrv.create(ccf, richCaseTemplate.caseTemplate, cf)
            } yield ()).logFailure(s"Unable to set custom field $name=${value.getOrElse("<not set>")}")
        }
        _ = caseTemplates += (inputCaseTemplate.caseTemplate.name -> richCaseTemplate.caseTemplate)
      } yield IdMapping(inputCaseTemplate.metaData.id, richCaseTemplate._id)
    }

  override def createCaseTemplateTask(caseTemplateId: EntityId, inputTask: InputTask): Try[IdMapping] =
    authTransaction(inputTask.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create task ${inputTask.task.title} in case template $caseTemplateId")
      for {
        caseTemplate <- caseTemplateSrv.getOrFail(caseTemplateId)
        richTask     <- caseTemplateSrv.createTask(caseTemplate, inputTask.task)
        _ = updateMetaData(richTask.task, inputTask.metaData)
      } yield IdMapping(inputTask.metaData.id, richTask._id)
    }

  override def caseExists(inputCase: InputCase): Boolean = caseNumbers.contains(inputCase.`case`.number)

  private def getCase(caseId: EntityId)(implicit graph: Graph): Try[Case with Entity] = caseSrv.getByIds(caseId).getOrFail("Case")

  override def createCase(inputCase: InputCase): Try[IdMapping] =
    authTransaction(inputCase.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create case #${inputCase.`case`.number}")
      val organisationIds = inputCase
        .organisations
        .flatMap {
          case (orgName, _) => getOrganisation(orgName).map(_._id).toOption
        }
        .toSeq
      val assignee = inputCase
        .`case`
        .assignee
        .flatMap(getUser(_).toOption)
      val caseTemplate = inputCase
        .`case`
        .caseTemplate
        .flatMap(getCaseTemplate)
      val resolutionStatus = inputCase
        .`case`
        .resolutionStatus
        .flatMap(getResolutionStatus(_).toOption)
      val impactStatus = inputCase
        .`case`
        .impactStatus
        .flatMap(getImpactStatus(_).toOption)
      val `case` = inputCase
        .`case`
        .copy(
          assignee = assignee.map(_.login),
          organisationIds = organisationIds,
          caseTemplate = caseTemplate.map(_.name),
          impactStatus = impactStatus.map(_.value),
          resolutionStatus = resolutionStatus.map(_.value)
        )
      caseSrv.createEntity(`case`).map { createdCase =>
        updateMetaData(createdCase, inputCase.metaData)
        assignee
          .foreach { user =>
            caseSrv
              .caseUserSrv
              .create(CaseUser(), createdCase, user)
              .logFailure(s"Unable to assign case #${createdCase.number} to ${user.login}")
          }
        caseTemplate
          .foreach { ct =>
            caseSrv
              .caseCaseTemplateSrv
              .create(CaseCaseTemplate(), createdCase, ct)
              .logFailure(s"Unable to set case template ${ct.name} to case #${createdCase.number}")
          }
        inputCase.`case`.tags.foreach { tagName =>
          getTag(tagName)
            .flatMap(tag => caseSrv.caseTagSrv.create(CaseTag(), createdCase, tag))
            .logFailure(s"Unable to add tag $tagName to case #${createdCase.number}")
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
            val owner = profileName == Profile.orgAdmin.name && !ownerSet
            val shared = for {
              organisation <- getOrganisation(organisationName)
              profile      <- getProfile(profileName)
              _            <- shareSrv.shareCase(owner, createdCase, organisation, profile)
            } yield ()
            shared.logFailure(s"Unable to share case #${createdCase.number} with organisation $organisationName, profile $profileName")
            ownerSet || owner
        }
        resolutionStatus
          .foreach { resolutionStatus =>
            caseSrv
              .caseResolutionStatusSrv
              .create(CaseResolutionStatus(), createdCase, resolutionStatus)
              .logFailure(s"Unable to set resolution status $resolutionStatus to case #${createdCase.number}")
          }
        impactStatus
          .foreach { impactStatus =>
            caseSrv
              .caseImpactStatusSrv
              .create(CaseImpactStatus(), createdCase, impactStatus)
              .logFailure(s"Unable to set impact status $impactStatus to case #${createdCase.number}")
          }

        IdMapping(inputCase.metaData.id, createdCase._id)
      }
    }

  override def createCaseTask(caseId: EntityId, inputTask: InputTask): Try[IdMapping] =
    authTransaction(inputTask.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create task ${inputTask.task.title} in case $caseId")
      val assignee      = inputTask.owner.flatMap(getUser(_).toOption)
      val organisations = inputTask.organisations.flatMap(getOrganisation(_).toOption)
      for {
        richTask <- taskSrv.create(inputTask.task.copy(relatedId = caseId, organisationIds = organisations.map(_._id)), assignee)
        _ = updateMetaData(richTask.task, inputTask.metaData)
        case0 <- getCase(caseId)
        _     <- organisations.toTry(o => shareSrv.shareTask(richTask, case0, o._id))
      } yield IdMapping(inputTask.metaData.id, richTask._id)
    }

  def createCaseTaskLog(taskId: EntityId, inputLog: InputLog): Try[IdMapping] =
    authTransaction(inputLog.metaData.createdBy) { implicit graph => implicit authContext =>
      for {
        task <- taskSrv.getOrFail(taskId)
        _ = logger.debug(s"Create log in task ${task.title}")
        log <- logSrv.createEntity(inputLog.log.copy(taskId = task._id, organisationIds = task.organisationIds))
        _ = updateMetaData(log, inputLog.metaData)
        _ <- logSrv.taskLogSrv.create(TaskLog(), task, log)
        _ <- inputLog.attachments.toTry { inputAttachment =>
          attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data).flatMap { attachment =>
            logSrv.logAttachmentSrv.create(LogAttachment(), log, attachment)
          }
        }
      } yield IdMapping(inputLog.metaData.id, log._id)
    }

  private def createSimpleObservable(observable: Observable, observableType: ObservableType with Entity, dataValue: String)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Observable with Entity] =
    for {
      data <- dataSrv.createEntity(Data(dataValue))
      _ <-
        if (observableType.isAttachment) Failure(BadRequestError("A attachment observable doesn't accept string value"))
        else Success(())
      createdObservable <- observableSrv.createEntity(observable.copy(data = Some(dataValue)))
      _                 <- observableSrv.observableDataSrv.create(ObservableData(), createdObservable, data)
    } yield createdObservable

  private def createAttachmentObservable(
      observable: Observable,
      observableType: ObservableType with Entity,
      inputAttachment: InputAttachment
  )(implicit graph: Graph, authContext: AuthContext): Try[Observable with Entity] =
    for {
      attachment <- attachmentSrv.create(inputAttachment.name, inputAttachment.size, inputAttachment.contentType, inputAttachment.data)
      _ <-
        if (!observableType.isAttachment) Failure(BadRequestError("A text observable doesn't accept attachment"))
        else Success(())
      createdObservable <- observableSrv.createEntity(observable.copy(data = None))
      _                 <- observableSrv.observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)
    } yield createdObservable

  private def createObservable(relatedId: EntityId, inputObservable: InputObservable, organisationIds: Seq[EntityId])(implicit
      graph: Graph,
      authContext: AuthContext
  ) =
    for {
      observableType <- getObservableType(inputObservable.observable.dataType)
      observable <-
        inputObservable
          .dataOrAttachment
          .fold(
            data =>
              createSimpleObservable(
                inputObservable.observable.copy(organisationIds = organisationIds, relatedId = relatedId),
                observableType,
                data
              ),
            attachment =>
              createAttachmentObservable(
                inputObservable.observable.copy(organisationIds = organisationIds, relatedId = relatedId),
                observableType,
                attachment
              )
          )
      _ = updateMetaData(observable, inputObservable.metaData)
      _ <- observableSrv.observableObservableType.create(ObservableObservableType(), observable, observableType)
      _ = inputObservable.observable.tags.foreach { tagName =>
        getTag(tagName)
          .foreach(tag => observableSrv.observableTagSrv.create(ObservableTag(), observable, tag))
      }
    } yield observable

  override def createCaseObservable(caseId: EntityId, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in case $caseId")
      for {
        organisations  <- inputObservable.organisations.toTry(getOrganisation)
        richObservable <- createObservable(caseId, inputObservable, organisations.map(_._id))
        case0          <- getCase(caseId)
        _              <- organisations.toTry(o => shareSrv.shareObservable(RichObservable(richObservable, None, None, Nil), case0, o._id))
      } yield IdMapping(inputObservable.metaData.id, richObservable._id)
    }

  override def createJob(observableId: EntityId, inputJob: InputJob): Try[IdMapping] =
    authTransaction(inputJob.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create job ${inputJob.job.cortexId}:${inputJob.job.workerName}:${inputJob.job.cortexJobId}")
      for {
        observable <- observableSrv.getOrFail(observableId)
        job        <- jobSrv.create(inputJob.job, observable)
        _ = updateMetaData(job.job, inputJob.metaData)
      } yield IdMapping(inputJob.metaData.id, job._id)
    }

  override def createJobObservable(jobId: EntityId, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in job $jobId")
      for {
        organisations <- inputObservable.organisations.toTry(getOrganisation)
        observable    <- createObservable(jobId, inputObservable, organisations.map(_._id))
        job           <- jobSrv.getOrFail(jobId)
        _             <- jobSrv.addObservable(job, observable)
      } yield IdMapping(inputObservable.metaData.id, observable._id)
    }

  override def alertExists(inputAlert: InputAlert): Boolean =
    alerts.contains((inputAlert.alert.`type`, inputAlert.alert.source, inputAlert.alert.sourceRef))

  override def createAlert(inputAlert: InputAlert): Try[IdMapping] =
    authTransaction(inputAlert.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create alert ${inputAlert.alert.`type`}:${inputAlert.alert.source}:${inputAlert.alert.sourceRef}")
      val `case` = inputAlert.caseId.flatMap(c => getCase(EntityId.read(c)).toOption)
      val tags   = inputAlert.alert.tags.flatMap(getTag(_).toOption)
      for {
        organisation <- getOrganisation(inputAlert.organisation)
        createdAlert <- alertSrv.createEntity(inputAlert.alert.copy(organisationId = organisation._id, caseId = `case`.map(_._id)))
        _ = updateMetaData(createdAlert, inputAlert.metaData)
        _ <- alertSrv.alertOrganisationSrv.create(AlertOrganisation(), createdAlert, organisation)
        _ <-
          inputAlert
            .caseTemplate
            .flatMap(getCaseTemplate)
            .map(ct => alertSrv.alertCaseTemplateSrv.create(AlertCaseTemplate(), createdAlert, ct))
            .flip
        _ = tags.foreach(t => alertSrv.alertTagSrv.create(AlertTag(), createdAlert, t))
        _ = inputAlert.customFields.foreach {
          case (name, value) => // TODO Add order
            getCustomField(name)
              .flatMap { cf =>
                CustomFieldType
                  .map(cf.`type`)
                  .setValue(AlertCustomField(), value)
                  .flatMap(acf => alertSrv.alertCustomFieldSrv.create(acf, createdAlert, cf))
              }
              .logFailure(s"Unable to set custom field $name=${value
                .getOrElse("<not set>")} to alert ${inputAlert.alert.`type`}:${inputAlert.alert.source}:${inputAlert.alert.sourceRef}")
        }
      } yield IdMapping(inputAlert.metaData.id, createdAlert._id)
    }

  override def createAlertObservable(alertId: EntityId, inputObservable: InputObservable): Try[IdMapping] =
    authTransaction(inputObservable.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create observable ${inputObservable.dataOrAttachment.fold(identity, _.name)} in alert $alertId")
      for {
        alert      <- alertSrv.getOrFail(alertId)
        observable <- createObservable(alert._id, inputObservable, Seq(alert.organisationId))
        _          <- alertSrv.alertObservableSrv.create(AlertObservable(), alert, observable)
      } yield IdMapping(inputObservable.metaData.id, observable._id)
    }

  private def getEntity(entityType: String, entityId: EntityId)(implicit graph: Graph): Try[Product with Entity] =
    entityType match {
      case "Task"       => taskSrv.getOrFail(entityId)
      case "Case"       => getCase(entityId)
      case "Observable" => observableSrv.getOrFail(entityId)
      case "Log"        => logSrv.getOrFail(entityId)
      case "Alert"      => alertSrv.getOrFail(entityId)
      case "Job"        => jobSrv.getOrFail(entityId)
      case "Action"     => actionSrv.getOrFail(entityId)
      case _            => Failure(BadRequestError(s"objectType $entityType is not recognised"))
    }

  override def createAction(objectId: EntityId, inputAction: InputAction): Try[IdMapping] =
    authTransaction(inputAction.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(
        s"Create action ${inputAction.action.cortexId}:${inputAction.action.workerName}:${inputAction.action.cortexJobId} for ${inputAction.objectType} $objectId"
      )
      for {
        entity <- getEntity(inputAction.objectType, objectId)
        action <- actionSrv.create(inputAction.action, entity)
        _ = updateMetaData(action.action, inputAction.metaData)
      } yield IdMapping(inputAction.metaData.id, action._id)
    }

  override def createAudit(contextId: EntityId, inputAudit: InputAudit): Try[Unit] =
    authTransaction(inputAudit.metaData.createdBy) { implicit graph => implicit authContext =>
      logger.debug(s"Create audit ${inputAudit.audit.action} on ${inputAudit.audit.objectType} ${inputAudit.audit.objectId}")
      for {
        obj <- (for {
            t <- inputAudit.audit.objectType
            i <- inputAudit.audit.objectId
          } yield getEntity(t, new EntityId(i))).flip
        ctxType = obj.map(_._label).map {
          case "Alert"                                        => "Alert"
          case "Log" | "Task" | "Observable" | "Case" | "Job" => "Case"
          case "User"                                         => "User"
          case "Action"                                       => "Action" // FIXME
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
