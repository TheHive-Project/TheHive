package org.thp.thehive.services

import java.util.{List ⇒ JList}

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.{Filter, PredicateFilter}
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, LogGeneratedCode, PrivateField}
import org.thp.thehive.models._
import shapeless.HList

import scala.collection.JavaConverters._
import scala.util.Success

@Singleton
class CaseSrv @Inject()(caseUserSrv: CaseUserSrv, caseCustomFieldSrv: CaseCustomFieldSrv, customFieldSrv: CustomFieldSrv)(implicit db: Database)
    extends VertexSrv[Case] {

  def create(`case`: Case, user: User with Entity, customFields: Seq[(String, Any)])(implicit graph: Graph, authContext: AuthContext): RichCase = {
    val caseNumber  = nextCaseNumber
    val createdCase = create(`case`.copy(number = caseNumber))
    caseUserSrv.create(CaseUser(), createdCase, user)
    val cfs = customFields.map {
      case (name, value) ⇒
        val cf = customFieldSrv.getOrFail(name)
        caseCustomFieldSrv.create(cf.`type`.setValue(value), createdCase, cf)
        CustomFieldValue(cf.name, cf.description, cf.`type`.name, value)
    }
    RichCase(createdCase, None, user.login, cfs)
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
      .map(steps.getCaseByNumber)
      .getOrElse(steps(graph.V(caseIdOrNumber)))

  override def steps(implicit graph: Graph): CaseSteps     = new CaseSteps(graph.V.hasLabel(model.label), caseCustomFieldSrv, customFieldSrv)
  override def steps(raw: GremlinScala[Vertex]): CaseSteps = new CaseSteps(raw, caseCustomFieldSrv, customFieldSrv)
}

@EntitySteps[Case]
@LogGeneratedCode
class CaseSteps(raw: GremlinScala[Vertex], caseCustomFieldSrv: CaseCustomFieldSrv, customFieldSrv: CustomFieldSrv)(implicit db: Database)
    extends BaseVertexSteps[Case, CaseSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): CaseSteps = new CaseSteps(raw, caseCustomFieldSrv, customFieldSrv)
  override def filter(f: EntityFilter[Vertex]): CaseSteps        = newInstance(f(raw))

  override val filterHook: PartialFunction[PredicateFilter[Vertex], Filter[Vertex]] = {
    case PredicateFilter("ImpactStatus", p: P[a]) ⇒
      Filter[Vertex](_.where(_.out("CaseImpactStatus").has(Key[a]("value"), p)))
  }

  def getCaseById(id: String): CaseSteps = newInstance(raw.hasId(id))

  def getCaseByNumber(caseNumber: Int): CaseSteps = newInstance(raw.has(Key("number") of caseNumber))

  @PrivateField def richCase[NewLabels <: HList]: GremlinScala[RichCase] =
    raw
      .project[Any]("case", "impactStatus", "user", "customFields")
      .by()
      .by(__[Vertex].outTo[CaseImpactStatus].values[String]("value").fold.traversal)
      .by(__[Vertex].outTo[CaseUser].values[String]("login").fold.traversal)
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
            }
          RichCase(
            m.get[Vertex]("case").as[Case],
            atMostOneOf[String](m.get[JList[String]]("impactStatus")),
            onlyOneOf[String](m.get[JList[String]]("user")),
            customFieldValues
          )
      }

  def impactStatus = new ImpactStatusSteps(raw.outTo[CaseImpactStatus])

  def tasks = new TaskSteps(raw.outTo[CaseTask])
}
