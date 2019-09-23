package org.thp.thehive.services

import java.util.{Date, List => JList, Set => JSet}

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{Order, Path, P => JP}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, InternalError, RichJMap, RichOptionTry, RichSeq}
import org.thp.thehive.models._
import play.api.libs.json.{JsNull, JsObject, Json}

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

@Singleton
class CaseSrv @Inject()(
    caseTemplateSrv: CaseTemplateSrv,
    tagSrv: TagSrv,
    customFieldSrv: CustomFieldSrv,
    userSrv: UserSrv,
    profileSrv: ProfileSrv,
    shareSrv: ShareSrv,
    taskSrv: TaskSrv,
    observableSrv: ObservableSrv,
    auditSrv: AuditSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    impactStatusSrv: ImpactStatusSrv
)(implicit db: Database)
    extends VertexSrv[Case, CaseSteps] {

  val caseTagSrv              = new EdgeSrv[CaseTag, Case, Tag]
  val caseImpactStatusSrv     = new EdgeSrv[CaseImpactStatus, Case, ImpactStatus]
  val caseResolutionStatusSrv = new EdgeSrv[CaseResolutionStatus, Case, ResolutionStatus]
  val caseUserSrv             = new EdgeSrv[CaseUser, Case, User]
  val caseCustomFieldSrv      = new EdgeSrv[CaseCustomField, Case, CustomField]
  val caseCaseTemplateSrv     = new EdgeSrv[CaseCaseTemplate, Case, CaseTemplate]
  val shareCaseSrv            = new EdgeSrv[ShareCase, Share, Case]
  val shareObservableSrv      = new EdgeSrv[ShareObservable, Share, Observable]
  val shareTaskSrv            = new EdgeSrv[ShareTask, Share, Task]
  val mergedFromSrv           = new EdgeSrv[MergedFrom, Case, Case]

  def create(
      `case`: Case,
      user: Option[User with Entity],
      organisation: Organisation with Entity,
      tagNames: Set[String],
      customFields: Map[String, Option[Any]],
      caseTemplate: Option[RichCaseTemplate],
      additionalTasks: Seq[Task]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCase] =
    for {
      createdCase  <- createEntity(if (`case`.number == 0) `case`.copy(number = nextCaseNumber) else `case`)
      _            <- user.map(caseUserSrv.create(CaseUser(), createdCase, _)).flip
      _            <- shareSrv.create(createdCase, organisation, profileSrv.admin)
      _            <- caseTemplate.map(ct => caseCaseTemplateSrv.create(CaseCaseTemplate(), createdCase, ct.caseTemplate)).flip
      createdTasks <- caseTemplate.fold(additionalTasks)(_.tasks).toTry(taskSrv.create(_))
      _            <- createdTasks.toTry(addTask(createdCase, _))
      caseTemplateCustomFields = caseTemplate
        .fold[Seq[CustomFieldWithValue]](Nil)(_.customFields)
        .map(cf => cf.name -> cf.value)
      cfs  <- createCustomFields(createdCase, caseTemplateCustomFields.toMap ++ customFields)
      tags <- tagNames.toTry(t => tagSrv.getOrCreate(t))
      caseTemplateTags = caseTemplate.fold[Seq[Tag with Entity]](Nil)(_.tags)
      allTags          = tags ++ caseTemplateTags
      _       <- allTags.toTry(t => caseTagSrv.create(CaseTag(), createdCase, t))
      _       <- auditSrv.`case`.create(createdCase)
      creator <- userSrv.current.getOrFail()
      _       <- assign(createdCase, creator)
    } yield RichCase(createdCase, allTags, None, None, user.map(_.login), cfs)

  def createCustomFields(
      `case`: Case with Entity,
      customFields: Map[String, Option[Any]]
  )(implicit graph: Graph, authContext: AuthContext): Try[Seq[CustomFieldWithValue]] =
    customFields
      .toTry {
        case (name, Some(value)) =>
          for {
            cf              <- customFieldSrv.getOrFail(name)
            ccf             <- cf.`type`.setValue(CaseCustomField(), value)
            caseCustomField <- caseCustomFieldSrv.create(ccf, `case`, cf)
          } yield CustomFieldWithValue(cf, caseCustomField)
        case (name, _) =>
          for {
            cf              <- customFieldSrv.getOrFail(name)
            caseCustomField <- caseCustomFieldSrv.create(CaseCustomField(), `case`, cf)
          } yield CustomFieldWithValue(cf, caseCustomField)
      }

  def nextCaseNumber(implicit graph: Graph): Int = initSteps.getLast.headOption().fold(0)(_.number) + 1

  def addTask(`case`: Case with Entity, task: Task with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      share <- get(`case`)
        .share
        .getOrFail()
      _ = shareTaskSrv.create(ShareTask(), share, task)
      _ <- auditSrv.task.create(task, `case`)
    } yield ()

  override def update(
      steps: CaseSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(CaseSteps, JsObject)] = {
    def endDateSetter(endDate: Date): PropertyUpdater =
      PropertyUpdater(FPathElem("endDate"), endDate.getTime) { (vertex, _, _, _) =>
        vertex.property("endDate", endDate.getTime)
        Success(Json.obj("endDate" -> endDate.getTime))
      }

    val closeCase = propertyUpdaters.exists(p => p.path.matches(FPathElem("status")) && p.value == "Resolved")

    val newPropertyUpdaters = if (closeCase) endDateSetter(new Date) +: propertyUpdaters else propertyUpdaters
    auditSrv.mergeAudits(super.update(steps, newPropertyUpdaters)) {
      case (caseSteps, updatedFields) =>
        caseSteps
          .clone()
          .getOrFail()
          .flatMap(auditSrv.`case`.update(_, updatedFields))
    }
  }

  def updateTags(`case`: Case with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(`case`)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[String])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t.name) => (toAdd - t.name, toRemove)
        case ((toAdd, toRemove), t)                           => (toAdd, toRemove + t.name)
      }
    for {
      createdTags <- tagsToAdd.toTry(tagSrv.getOrCreate(_))
      _           <- createdTags.toTry(caseTagSrv.create(CaseTag(), `case`, _))
      _ = get(`case`).removeTags(tagsToRemove)
      _ <- auditSrv.`case`.update(`case`, Json.obj("tags" -> tags))
    } yield ()
  }

  def addTags(`case`: Case with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val currentTags = get(`case`)
      .tags
      .toList
      .map(_.name)
      .toSet
    for {
      createdTags <- (tags -- currentTags).toTry(tagSrv.getOrCreate(_))
      _           <- createdTags.toTry(caseTagSrv.create(CaseTag(), `case`, _))
      _           <- auditSrv.`case`.update(`case`, Json.obj("tags" -> (currentTags ++ tags)))
    } yield ()
  }

  def addObservable(`case`: Case with Entity, observable: Observable with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      share <- get(`case`)
        .share
        .getOrFail()
      _ = shareObservableSrv.create(ShareObservable(), share, observable)
      _ <- auditSrv.observable.create(observable, `case`)
    } yield ()

  def cascadeRemove(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      _ <- get(`case`).tasks.toIterator.toTry(taskSrv.cascadeRemove(_))
      _ <- get(`case`).observables.toIterator.toTry(observableSrv.cascadeRemove(_))
      _ = get(`case`).share.remove()
      _ = get(`case`).remove()
      _ <- auditSrv.`case`.delete(`case`)
    } yield ()

  def isAvailable(caseIdOrNumber: String)(implicit graph: Graph, authContext: AuthContext): Boolean =
    get(caseIdOrNumber).visible.exists()

  override def get(idOrNumber: String)(implicit graph: Graph): CaseSteps =
    Success(idOrNumber)
      .filter(_.headOption.contains('#'))
      .map(_.tail.toInt)
      .map(initSteps.getByNumber(_))
      .getOrElse(super.getByIds(idOrNumber))

  def setCustomField(`case`: Case with Entity, customFieldName: String, value: Any)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    customFieldSrv.getOrFail(customFieldName).flatMap(cf => setCustomField(`case`, cf, value))

  def setCustomField(`case`: Case with Entity, customField: CustomField with Entity, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    for {
      _ <- getCustomField(`case`, customField.name) match {
        case Some(cf) => caseCustomFieldSrv.get(cf.customFieldValue).update((cf.`type`.name + "Value") -> Some(value))
        case None =>
          customField.`type`.asInstanceOf[CustomFieldType[Any]].setValue(CaseCustomField(), value).map { caseCustomField =>
            caseCustomFieldSrv.create(caseCustomField, `case`, customField)
            ()
          }
      }
      _ <- auditSrv.`case`.update(`case`, Json.obj(s"customField.${customField.name}" -> value.toString))
    } yield ()

  def getCustomField(`case`: Case with Entity, customFieldName: String)(implicit graph: Graph): Option[CustomFieldWithValue] =
    get(`case`).customFields(Some(customFieldName)).headOption()

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CaseSteps = new CaseSteps(raw)

  def setImpactStatus(
      `case`: Case with Entity,
      impactStatus: String
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    impactStatusSrv.getOrFail(impactStatus).flatMap(setImpactStatus(`case`, _))

  def setImpactStatus(
      `case`: Case with Entity,
      impactStatus: ImpactStatus with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    caseImpactStatusSrv.create(CaseImpactStatus(), `case`, impactStatus)
    auditSrv.`case`.update(`case`, Json.obj("impactStatus" -> impactStatus.value))
  }

  def unsetImpactStatus(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unsetImpactStatus()
    auditSrv.`case`.update(`case`, Json.obj("impactStatus" -> JsNull))
  }

  def setResolutionStatus(
      `case`: Case with Entity,
      resolutionStatus: String
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    resolutionStatusSrv.getOrFail(resolutionStatus).flatMap(setResolutionStatus(`case`, _))

  def setResolutionStatus(
      `case`: Case with Entity,
      resolutionStatus: ResolutionStatus with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unsetResolutionStatus()
    caseResolutionStatusSrv.create(CaseResolutionStatus(), `case`, resolutionStatus)
    auditSrv.`case`.update(`case`, Json.obj("resolutionStatus" -> resolutionStatus.value))
  }

  def unsetResolutionStatus(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unsetResolutionStatus()
    auditSrv.`case`.update(`case`, Json.obj("resolutionStatus" -> JsNull))
  }

  def assign(`case`: Case with Entity, user: User with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    caseUserSrv.create(CaseUser(), `case`, user)
    auditSrv.`case`.update(`case`, Json.obj("owner" -> user.login))
  }

  def unassign(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(`case`).unassign()
    auditSrv.`case`.update(`case`, Json.obj("owner" -> JsNull))
  }

  def merge(cases: Seq[Case with Entity])(implicit graph: Graph, authContext: AuthContext): RichCase = ???
//  {
//    val summaries         = cases.flatMap(_.summary)
//    val user              = userSrv.getOrFail(authContext.userId)
//    val organisation      = organisationSrv.getOrFail(authContext.organisation)
//    val caseTaskSrv       = new EdgeSrv[CaseTask, Case, Task]
//    val caseObservableSrv = new EdgeSrv[CaseObservable, Case, Observable] nop
//
//    val mergedCase = create(
//      Case(
//        nextCaseNumber,
//        cases.map(_.title).mkString(" / "),
//        cases.map(_.description).mkString("\n\n"),
//        cases.map(_.severity).max,
//        cases.map(_.startDate).min,
//        None,
//        cases.flatMap(_.tags).distinct,
//        cases.exists(_.flag),
//        cases.map(_.tlp).max,
//        cases.map(_.pap).max,
//        CaseStatus.open,
//        if (summaries.isEmpty) None else Some(summaries.mkString("\n\n"))
//      ))
//    caseUserSrv.create(CaseUser(), mergedCase, user)
//    caseOrganisationSrv.create(CaseOrganisation(), mergedCase, organisation)
//    cases
//      .map(get)
//      .flatMap(_.customFields().toList
//      .groupBy(_.name)
//      .foreach {
//        case (name, l) ⇒
//          val values = l.collect { case cfwv: CustomFieldWithValue if cfwv.value.isDefined ⇒ cfwv.value.get }
//          val cf     = customFieldSrv.getOrFail(name)
//          val caseCustomField =
//            if (values.size == 1) cf.`type`.setValue(CaseCustomField(), values.head)
//            else CaseCustomField()
//          caseCustomFieldSrv.create(caseCustomField, mergedCase, cf)
//      }
//
//    cases.foreach(mergedFromSrv.create(MergedFrom(), mergedCase, _))
//
//    cases
//      .map(get)
//      .flatMap(_.tasks.toList
//      .foreach(task ⇒ caseTaskSrv.create(CaseTask(), task, mergedCase))
//
//    cases
//      .map(get)
//      .flatMap(_.observables.toList
//      .foreach(observable ⇒ observableCaseSrv.create(ObservableCase(), observable, mergedCase))
//
//    get(mergedCase).richCase.head()
//  }
}

@EntitySteps[Case]
class CaseSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Case, CaseSteps](raw) {

  def get(id: String): CaseSteps =
    Success(id)
      .filter(_.headOption.contains('#'))
      .map(_.tail.toInt)
      .map(getByNumber)
      .getOrElse(getByIds(id))

  def getByNumber(caseNumber: Int): CaseSteps = newInstance(raw.has(Key("number") of caseNumber))

  def visible(implicit authContext: AuthContext): CaseSteps = newInstance(
    raw.filter(_.inTo[ShareCase].inTo[OrganisationShare].inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId))
  )

  def assignee = new UserSteps(raw.outTo[CaseUser])

  def can(permission: Permission)(implicit authContext: AuthContext): CaseSteps =
    newInstance(
      raw.filter(
        _.inTo[ShareCase]
          .filter(_.outTo[ShareProfile].has(Key("permissions") of permission))
          .inTo[OrganisationShare]
          .inTo[RoleOrganisation]
          .filter(_.outTo[RoleProfile].has(Key("permissions") of permission))
          .inTo[UserRole]
          .has(Key("login") of authContext.userId)
      )
    )

  override def newInstance(raw: GremlinScala[Vertex]): CaseSteps = new CaseSteps(raw)

  def getLast: CaseSteps =
    newInstance(raw.order(By(Key[Int]("number"), Order.desc)))

  def richCaseWithCustomRenderer[A](
      entityRenderer: GremlinScala[Vertex] => GremlinScala[A]
  ): ScalarSteps[(RichCase, A)] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[CaseTag].fold))
            .and(By(__[Vertex].outTo[CaseImpactStatus].values[String]("value").fold))
            .and(By(__[Vertex].outTo[CaseResolutionStatus].values[String]("value").fold))
            .and(By(__[Vertex].outTo[CaseUser].values[String]("login").fold))
            .and(By(__[Vertex].outToE[CaseCustomField].inV().path.fold))
            .and(By(entityRenderer(__[Vertex])))
        )
        .map {
          case (caze, tags, impactStatus, resolutionStatus, user, customFields, renderedEntity) =>
            val customFieldValues = (customFields: JList[Path])
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case List(ccf, cf) => CustomFieldWithValue(cf.as[CustomField], ccf.as[CaseCustomField])
                case _             => throw InternalError("Not possible")
              }
              .toSeq
            RichCase(
              caze.as[Case],
              tags.asScala.map(_.as[Tag]).toSeq,
              atMostOneOf[String](impactStatus),
              atMostOneOf[String](resolutionStatus),
              atMostOneOf[String](user),
              customFieldValues
            ) -> renderedEntity
        }
    )

  def customFields(name: Option[String] = None): ScalarSteps[CustomFieldWithValue] = {
    val ccfSteps: GremlinScala[Vertex] = raw
      .outToE[CaseCustomField]
      .inV()
    ScalarSteps(
      name
        .fold[GremlinScala[Vertex]](ccfSteps)(n => ccfSteps.has(Key("name") of n))
        .path
        .map(path => CustomFieldWithValue(path.get[Vertex](2).as[CustomField], path.get[Edge](1).as[CaseCustomField]))
    )
  }

  def share(implicit authContext: AuthContext): ShareSteps =
    new ShareSteps(
      raw
        .inTo[ShareCase]
        .filter(_.inTo[OrganisationShare].has(Key("name") of authContext.organisation))
    )

  def shares: ShareSteps = new ShareSteps(raw.inTo[ShareCase])

  def organisations: OrganisationSteps = new OrganisationSteps(raw.inTo[ShareCase].inTo[OrganisationShare])

  // Warning: this method doesn't generate audit log
  def unassign(): Unit = {
    raw.outToE[CaseUser].drop().iterate()
    ()
  }

  def unsetResolutionStatus(): Unit = {
    raw.outToE[CaseResolutionStatus].drop().iterate()
    ()
  }

  def unsetImpactStatus(): Unit = {
    raw.outToE[CaseImpactStatus].drop().iterate()
    ()
  }

  def removeTags(tagNames: Set[String]): Unit = {
    raw.outToE[CaseTag].where(_.otherV().has(Key[String]("name"), P.within(tagNames))).drop().iterate()
    ()
  }

  def linkedCases(implicit authContext: AuthContext): Seq[(RichCase, Seq[RichObservable])] = {
    val originCaseLabel = StepLabel[JSet[Vertex]]()
    val observableLabel = StepLabel[Vertex]()
    val linkedCaseLabel = StepLabel[Vertex]()

    val richCaseLabel        = StepLabel[RichCase]()
    val richObservablesLabel = StepLabel[JList[RichObservable]]()
    raw
      .`match`(
        _.as(originCaseLabel.name)
          .in("ShareCase")
          .filter(
            _.inTo[OrganisationShare]
              .inTo[RoleOrganisation]
              .inTo[UserRole]
              .has(Key("login") of authContext.userId)
          )
          .out("ShareObservable")
          .as(observableLabel.name),
        _.as(observableLabel.name)
          .out("ObservableData")
          .in("ObservableData")
          .in("ShareObservable")
          .filter(
            _.inTo[OrganisationShare]
              .inTo[RoleOrganisation]
              .inTo[UserRole]
              .has(Key("login") of authContext.userId)
          )
          .out("ShareCase")
          .where(JP.neq(originCaseLabel.name))
          .as(linkedCaseLabel.name),
        c => new CaseSteps(c.as(linkedCaseLabel)).richCase.as(richCaseLabel).raw,
        o => new ObservableSteps(o.as(observableLabel)).richObservable.fold.as(richObservablesLabel).raw
      )
      .select(richCaseLabel.name, richObservablesLabel.name)
      .toList()
      .map { resultMap =>
        resultMap.getValue(richCaseLabel) -> resultMap.getValue(richObservablesLabel).asScala.toSeq
      }
  }

  def richCase: ScalarSteps[RichCase] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[CaseTag].fold))
            .and(By(__[Vertex].outTo[CaseImpactStatus].values[String]("value").fold))
            .and(By(__[Vertex].outTo[CaseResolutionStatus].values[String]("value").fold))
            .and(By(__[Vertex].outTo[CaseUser].values[String]("login").fold))
            .and(By(__[Vertex].outToE[CaseCustomField].inV().path.fold))
        )
        .map {
          case (caze, tags, impactStatus, resolutionStatus, user, customFields) =>
            val customFieldValues = (customFields: JList[Path])
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case List(ccf, cf) => CustomFieldWithValue(cf.as[CustomField], ccf.as[CaseCustomField])
                case _             => throw InternalError("Not possible")
              }
              .toSeq
            RichCase(
              caze.as[Case],
              tags.asScala.map(_.as[Tag]).toSeq,
              atMostOneOf[String](impactStatus),
              atMostOneOf[String](resolutionStatus),
              atMostOneOf[String](user),
              customFieldValues
            )
        }
    )

  def tags: TagSteps = new TagSteps(raw.outTo[CaseTag])

  def impactStatus: ImpactStatusSteps = new ImpactStatusSteps(raw.outTo[CaseImpactStatus])

  def tasks(implicit authContext: AuthContext): TaskSteps =
    new TaskSteps(
      raw
        .inTo[ShareCase]
        .filter(_.inTo[OrganisationShare].has(Key("name") of authContext.organisation))
        .outTo[ShareTask]
    )

  def observables(implicit authContext: AuthContext): ObservableSteps =
    new ObservableSteps(
      raw
        .inTo[ShareCase]
        .filter(_.inTo[OrganisationShare].has(Key("name") of authContext.organisation))
        .outTo[ShareObservable]
    )

  def alert: AlertSteps = new AlertSteps(raw.inTo[AlertCase])
}
