package org.thp.thehive.services

import java.util.{List ⇒ JList}

import scala.collection.JavaConverters._
import scala.util.Success

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{Order, Path}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.{EdgeSrv, _}
import org.thp.scalligraph.{AuthorizationError, EntitySteps, InternalError}
import org.thp.thehive.models._

@Singleton
class CaseSrv @Inject()(customFieldSrv: CustomFieldSrv, userSrv: UserSrv, organisationSrv: OrganisationSrv)(implicit db: Database)
    extends VertexSrv[Case, CaseSteps] {

  val caseImpactStatusSrv  = new EdgeSrv[CaseImpactStatus, Case, ImpactStatus]
  val caseResolutionStatus = new EdgeSrv[CaseResolutionStatus, Case, ResolutionStatus]
  val caseUserSrv          = new EdgeSrv[CaseUser, Case, User]
  val caseCustomFieldSrv   = new EdgeSrv[CaseCustomField, Case, CustomField]
  val caseOrganisationSrv  = new EdgeSrv[CaseOrganisation, Case, Organisation]

  def create(`case`: Case, user: User with Entity, organisation: Organisation with Entity, customFields: Seq[(String, Any)])(
      implicit graph: Graph,
      authContext: AuthContext): RichCase = {
    val caseNumber  = nextCaseNumber
    val createdCase = create(`case`.copy(number = caseNumber))
    caseUserSrv.create(CaseUser(), createdCase, user)
    caseOrganisationSrv.create(CaseOrganisation(), createdCase, organisation)
    val cfs = customFields.map {
      case (name, value) ⇒
        val cf = customFieldSrv.getOrFail(name)
        caseCustomFieldSrv.create(cf.`type`.setValue(CaseCustomField(), value), createdCase, cf)
        CustomFieldWithValue(cf.name, cf.description, cf.`type`.name, value)
    }
    RichCase(createdCase, None, None, user.login, organisation.name, cfs)
  }

  def nextCaseNumber(implicit graph: Graph): Int = initSteps.getLast.headOption().fold(0)(_.number) + 1

  def setCustomField(`case`: Case with Entity, customFieldName: String, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext): CaseCustomField with Entity =
    setCustomField(`case`, customFieldSrv.getOrFail(customFieldName), value)

  def setCustomField(`case`: Case with Entity, customField: CustomField with Entity, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext): CaseCustomField with Entity = {
    val caseCustomField = customField.`type`.asInstanceOf[CustomFieldType[Any]].setValue(CaseCustomField(), value)
    caseCustomFieldSrv.create(caseCustomField, `case`, customField)
  }

  override def get(caseIdOrNumber: String)(implicit graph: Graph): CaseSteps =
    Success(caseIdOrNumber)
      .filter(_.headOption.contains('#'))
      .map(_.tail.toInt)
      .map(initSteps.getCaseByNumber)
      .getOrElse(steps(graph.V().has(Key("_id") of caseIdOrNumber)))

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CaseSteps = new CaseSteps(raw)

  def merge(caseIds: String*)(implicit graph: Graph, authContext: AuthContext): RichCase = {
    val cases        = caseIds.map(getOrFail)
    val summaries    = cases.flatMap(_.summary)
    val user         = userSrv.getOrFail(authContext.userId)
    val organisation = organisationSrv.getOrFail(authContext.organisation)
    val taskCaseSrv  = new EdgeSrv[TaskCase, Task, Case]

    val mergedCase = create(
      Case(
        nextCaseNumber,
        cases.map(_.title).mkString(" / "),
        cases.map(_.description).mkString("\n\n"),
        cases.map(_.severity).max,
        cases.map(_.startDate).min,
        None,
        cases.flatMap(_.tags).distinct,
        cases.exists(_.flag),
        cases.map(_.tlp).max,
        cases.map(_.pap).max,
        CaseStatus.open,
        if (summaries.isEmpty) None else Some(summaries.mkString("\n\n"))
      ))
    caseUserSrv.create(CaseUser(), mergedCase, user)
    caseOrganisationSrv.create(CaseOrganisation(), mergedCase, organisation)
    cases
      .map(get)
      .flatMap(_.customFields.toList())
      .groupBy(_.name)
      .foreach {
        case (name, l) if l.size == 1 ⇒
          val cf = customFieldSrv.getOrFail(name)
          caseCustomFieldSrv.create(cf.`type`.setValue(CaseCustomField(), l.head.value), mergedCase, cf)
          l.head.name → l.head.value
        case (name, _) ⇒
          val cf = customFieldSrv.getOrFail(name)
          caseCustomFieldSrv.create(CaseCustomField(), mergedCase, cf)
      }

    cases
      .map(get)
      .flatMap(_.tasks.toList())
      .foreach(task ⇒ taskCaseSrv.create(TaskCase(), task, mergedCase))

    // TODO:
    // link observables

    get(mergedCase).richCase.head()
  }
}

@EntitySteps[Case]
class CaseSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Case, CaseSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): CaseSteps = new CaseSteps(raw)

  def getCaseById(id: String): CaseSteps = newInstance(raw.has(Key("_id") of id))

  def getCaseByNumber(caseNumber: Int): CaseSteps = newInstance(raw.has(Key("number") of caseNumber))

  def availableFor(authContext: Option[AuthContext]): CaseSteps =
    availableFor(authContext.getOrElse(throw AuthorizationError("access denied")).organisation)

  def availableFor(organisation: String): CaseSteps =
    newInstance(
      raw.filter(
        _.or(_.outTo[CaseOrganisation].value("name").is(organisation), _.inTo[ShareCase].outTo[ShareOrganisation].value("name").is(organisation))))

  def getLast: CaseSteps = newInstance(raw.order(By(Key[Int]("number"), Order.decr)))

  def richCase: ScalarSteps[RichCase] =
    ScalarSteps(
      raw
        .project[Any]("case", "impactStatus", "resolutionStatus", "user", "organisation", "customFields")
        .by()
        .by(__[Vertex].outTo[CaseImpactStatus].values[String]("value").fold.traversal)
        .by(__[Vertex].outTo[CaseResolutionStatus].values[String]("value").fold.traversal)
        .by(__[Vertex].outTo[CaseUser].values[String]("login").fold.traversal)
        .by(__[Vertex].outTo[CaseOrganisation].values[String]("name").fold.traversal)
        .by(__[Vertex].outToE[CaseCustomField].inV().path.fold.traversal)
        .map {
          case ValueMap(m) ⇒
            val customFieldValues = m
              .get[JList[Path]]("customFields")
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case ccf :: cf :: Nil ⇒
                  val customField     = cf.as[CustomField]
                  val caseCustomField = ccf.as[CaseCustomField].asInstanceOf[CaseCustomField]
                  CustomFieldWithValue(
                    customField.name,
                    customField.description,
                    customField.`type`.name,
                    customField.`type`.getValue(caseCustomField))
                case _ ⇒ throw InternalError("Not possible")
              }
            RichCase(
              m.get[Vertex]("case").as[Case],
              atMostOneOf[String](m.get[JList[String]]("impactStatus")),
              atMostOneOf[String](m.get[JList[String]]("resolutionStatus")),
              onlyOneOf[String](m.get[JList[String]]("user")),
              onlyOneOf[String](m.get[JList[String]]("organisation")),
              customFieldValues
            )
        })

  def customFields: ScalarSteps[CustomFieldWithValue] =
    ScalarSteps(
      raw
        .outToE[CaseCustomField]
        .inV()
        .path
        .map { y ⇒
          val caseCustomField = y.get[Edge](1).as[CaseCustomField].asInstanceOf[CaseCustomField]
          val customField     = y.get[Vertex](2).as[CustomField]
          CustomFieldWithValue(customField.name, customField.description, customField.`type`.name, customField.`type`.getValue(caseCustomField))
        })

  def impactStatus = new ImpactStatusSteps(raw.outTo[CaseImpactStatus])

  def tasks = new TaskSteps(raw.inTo[TaskCase])
}
