package org.thp.thehive.services

import java.util.{List ⇒ JList, Set ⇒ JSet}

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{Order, Path, P ⇒ JP}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, InternalError, RichSeq}
import org.thp.thehive.models._

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

@Singleton
class CaseSrv @Inject()(
    customFieldSrv: CustomFieldSrv,
    userSrv: UserSrv,
    profileSrv: ProfileSrv,
    shareSrv: ShareSrv
)(implicit db: Database)
    extends VertexSrv[Case, CaseSteps] {

  val caseImpactStatusSrv     = new EdgeSrv[CaseImpactStatus, Case, ImpactStatus]
  val caseResolutionStatusSrv = new EdgeSrv[CaseResolutionStatus, Case, ResolutionStatus]
  val caseUserSrv             = new EdgeSrv[CaseUser, Case, User]
  val caseCustomFieldSrv      = new EdgeSrv[CaseCustomField, Case, CustomField]
  val caseObservableSrv       = new EdgeSrv[CaseObservable, Case, Observable]
  val caseTaskSrv             = new EdgeSrv[CaseTask, Case, Task]
  val shareCaseSrv            = new EdgeSrv[ShareCase, Share, Case]
  val caseCaseTemplateSrv     = new EdgeSrv[CaseCaseTemplate, Case, CaseTemplate]
  val mergedFromSrv           = new EdgeSrv[MergedFrom, Case, Case]

  def create(
      `case`: Case,
      user: Option[User with Entity],
      organisation: Organisation with Entity,
      customFields: Map[String, Option[Any]],
      caseTemplate: Option[RichCaseTemplate]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCase] = {
    val createdCase = create(if (`case`.number == 0) `case`.copy(number = nextCaseNumber) else `case`)
    user.foreach(caseUserSrv.create(CaseUser(), createdCase, _))
    shareSrv.create(createdCase, organisation, profileSrv.admin)
    caseTemplate.foreach(ct ⇒ caseCaseTemplateSrv.create(CaseCaseTemplate(), createdCase, ct.caseTemplate))

    customFields
      .toSeq
      .toTry {
        case (name, Some(value)) ⇒
          for {
            cf  ← customFieldSrv.getOrFail(name)
            ccf ← cf.`type`.setValue(CaseCustomField(), value)
            alertCustomField = caseCustomFieldSrv.create(ccf, createdCase, cf)
          } yield CustomFieldWithValue(cf, alertCustomField)
        case (name, _) ⇒
          customFieldSrv.getOrFail(name).map { cf ⇒
            val alertCustomField = caseCustomFieldSrv.create(CaseCustomField(), createdCase, cf)
            CustomFieldWithValue(cf, alertCustomField)
          }
      }
      .map { cfs ⇒
        RichCase(createdCase, None, None, user.map(_.login), cfs)
      }
  }

  def isAvailable(caseIdOrNumber: String)(implicit graph: Graph, authContext: AuthContext): Boolean =
    get(caseIdOrNumber).visible.isDefined

  def nextCaseNumber(implicit graph: Graph): Int = initSteps.getLast.headOption().fold(0)(_.number) + 1

  def setCustomField(`case`: Case with Entity, customFieldName: String, value: Any)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    customFieldSrv.getOrFail(customFieldName).flatMap(cf ⇒ setCustomField(`case`, cf, value))

  def setCustomField(`case`: Case with Entity, customField: CustomField with Entity, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    getCustomField(`case`, customField.name) match {
      case Some(cf) ⇒ caseCustomFieldSrv.get(cf.customFieldValue._id).update((cf.`type`.name + "Value") → Some(value))
      case None ⇒
        customField.`type`.asInstanceOf[CustomFieldType[Any]].setValue(CaseCustomField(), value).map { caseCustomField ⇒
          caseCustomFieldSrv.create(caseCustomField, `case`, customField)
          ()
        }
    }

  def getCustomField(`case`: Case with Entity, customFieldName: String)(implicit graph: Graph): Option[CustomFieldWithValue] =
    get(`case`).customFields(Some(customFieldName)).headOption()

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CaseSteps = new CaseSteps(raw)

  def setImpactStatus(
      `case`: Case with Entity,
      impactStatus: ImpactStatus with Entity
  )(implicit graph: Graph, authContext: AuthContext): CaseImpactStatus with Entity =
    caseImpactStatusSrv.create(CaseImpactStatus(), `case`, impactStatus)

  def setResolutionStatus(
      `case`: Case with Entity,
      resolutionStatus: ResolutionStatus with Entity
  )(implicit graph: Graph, authContext: AuthContext): CaseResolutionStatus with Entity =
    caseResolutionStatusSrv.create(CaseResolutionStatus(), `case`, resolutionStatus)

  def merge(cases: Seq[Case with Entity])(implicit graph: Graph, authContext: AuthContext): RichCase = ???
//  {
//    val summaries         = cases.flatMap(_.summary)
//    val user              = userSrv.getOrFail(authContext.userId)
//    val organisation      = organisationSrv.getOrFail(authContext.organisation)
//    val caseTaskSrv       = new EdgeSrv[CaseTask, Case, Task]
//    val caseObservableSrv = new EdgeSrv[CaseObservable, Case, Observable]
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
//      .flatMap(_.customFields().toList())
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
//      .flatMap(_.tasks.toList())
//      .foreach(task ⇒ caseTaskSrv.create(CaseTask(), task, mergedCase))
//
//    cases
//      .map(get)
//      .flatMap(_.observables.toList())
//      .foreach(observable ⇒ observableCaseSrv.create(ObservableCase(), observable, mergedCase))
//
//    get(mergedCase).richCase.head()
//  }
}

@EntitySteps[Case]
class CaseSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Case, CaseSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): CaseSteps = new CaseSteps(raw)

  override def get(id: String): CaseSteps =
    Success(id)
      .filter(_.headOption.contains('#'))
      .map(_.tail.toInt)
      .map(getByNumber)
      .getOrElse(getById(id))

  def getById(id: String): CaseSteps = newInstance(raw.has(Key("_id") of id))

  def getByNumber(caseNumber: Int): CaseSteps = newInstance(raw.has(Key("number") of caseNumber))

  def visible(implicit authContext: AuthContext): CaseSteps = newInstance(
    raw.filter(_.inTo[ShareCase].inTo[OrganisationShare].inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId))
  )

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

  def getLast: CaseSteps = newInstance(raw.order(By(Key[Int]("number"), Order.decr)))

  def richCase: ScalarSteps[RichCase] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[CaseImpactStatus].values[String]("value").fold))
            .and(By(__[Vertex].outTo[CaseResolutionStatus].values[String]("value").fold))
            .and(By(__[Vertex].outTo[CaseUser].values[String]("login").fold))
            .and(By(__[Vertex].outToE[CaseCustomField].inV().path.fold))
        )
        .map {
          case (caze, impactStatus, resolutionStatus, user, customFields) ⇒
            val customFieldValues = (customFields: JList[Path])
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case List(ccf, cf) ⇒ CustomFieldWithValue(cf.as[CustomField], ccf.as[CaseCustomField])
                case _             ⇒ throw InternalError("Not possible")
              }
            RichCase(
              caze.as[Case],
              atMostOneOf[String](impactStatus),
              atMostOneOf[String](resolutionStatus),
              atMostOneOf[String](user),
              customFieldValues
            )
        }
    )

  def customFields(name: Option[String] = None): ScalarSteps[CustomFieldWithValue] = {
    val ccfSteps: GremlinScala[Vertex] = raw
      .outToE[CaseCustomField]
      .inV()
    ScalarSteps(
      name
        .fold[GremlinScala[Vertex]](ccfSteps)(n ⇒ ccfSteps.has(Key("name") of n))
        .path
        .map(path ⇒ CustomFieldWithValue(path.get[Vertex](2).as[CustomField], path.get[Edge](1).as[CaseCustomField]))
    )
  }

  def linkedCases: CaseSteps = {
    val label = StepLabel[JSet[Vertex]]()
    new CaseSteps(
      raw
        .aggregate(label)
        .outTo[CaseObservable]
        .outTo[ObservableData]
        .inTo[ObservableData]
        .inTo[CaseObservable]
        .where(JP.without(label.name))
    )
  }

  def impactStatus = new ImpactStatusSteps(raw.outTo[CaseImpactStatus])

  def tasks = new TaskSteps(raw.outTo[CaseTask])

  def observables = new ObservableSteps(raw.outTo[CaseObservable])
}
