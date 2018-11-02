package org.thp.thehive.services

import java.util.{List ⇒ JList}

import scala.collection.JavaConverters._
import scala.util.Success

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.{EdgeSrv, _}
import org.thp.scalligraph.{AuthorizationError, EntitySteps, InternalError}
import org.thp.thehive.models._

@Singleton
class CaseSrv @Inject()(customFieldSrv: CustomFieldSrv)(implicit db: Database) extends VertexSrv[Case, CaseSteps] {

  val caseImpactStatusSrv = new EdgeSrv[CaseImpactStatus, Case, ImpactStatus]
  val caseUserSrv         = new EdgeSrv[CaseUser, Case, User]
  val caseCustomFieldSrv  = new EdgeSrv[CaseCustomField, Case, CustomField]
  val caseOrganisationSrv = new EdgeSrv[CaseOrganisation, Case, Organisation]

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
        caseCustomFieldSrv.create(cf.`type`.setValue(value), createdCase, cf)
        CustomFieldValue(cf.name, cf.description, cf.`type`.name, value)
    }
    RichCase(createdCase, None, user.login, organisation.name, cfs)
  }

  def nextCaseNumber(implicit graph: Graph): Int = count.toInt + 1 // TODO use max(number)+1

  def setCustomField(`case`: Case with Entity, customFieldName: String, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext): CaseCustomField with Entity =
    setCustomField(`case`, customFieldSrv.getOrFail(customFieldName), value)

  def setCustomField(`case`: Case with Entity, customField: CustomField with Entity, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext): CaseCustomField with Entity = {
    val caseCustomField = customField.`type`.asInstanceOf[CustomFieldType[Any]].setValue(value)
    caseCustomFieldSrv.create(caseCustomField, `case`, customField)
  }

  override def get(caseIdOrNumber: String)(implicit graph: Graph): CaseSteps =
    Success(caseIdOrNumber)
      .filter(_.headOption.contains('#'))
      .map(_.tail.toInt)
      .map(initSteps.getCaseByNumber)
      .getOrElse(steps(graph.V().has(Key("_id") of caseIdOrNumber)))

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CaseSteps = new CaseSteps(raw)
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

  def richCase: ScalarSteps[RichCase] =
    ScalarSteps(
      raw
        .project[Any]("case", "impactStatus", "user", "organisation", "customFields")
        .by()
        .by(__[Vertex].outTo[CaseImpactStatus].values[String]("value").fold.traversal)
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
                  val caseCustomField = ccf.as[CaseCustomField]
                  CustomFieldValue(customField.name, customField.description, customField.`type`.name, customField.`type`.getValue(caseCustomField))
                case _ ⇒ throw InternalError("Not possible")
              }
            RichCase(
              m.get[Vertex]("case").as[Case],
              atMostOneOf[String](m.get[JList[String]]("impactStatus")),
              onlyOneOf[String](m.get[JList[String]]("user")),
              onlyOneOf[String](m.get[JList[String]]("organisation")),
              customFieldValues
            )
        })

  def impactStatus = new ImpactStatusSteps(raw.outTo[CaseImpactStatus])

  def tasks = new TaskSteps(raw.inTo[TaskCase])
}
