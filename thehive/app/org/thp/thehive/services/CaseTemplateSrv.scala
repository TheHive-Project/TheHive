package org.thp.thehive.services
import java.util.{UUID, List ⇒ JList}

import gremlin.scala.{__, Edge, Element, Graph, GremlinScala, Key, Vertex}
import javax.inject.Inject
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.services._
import org.thp.scalligraph.{AuthorizationError, EntitySteps, InternalError}
import org.thp.thehive.models._

import scala.collection.JavaConverters._
import scala.util.Try

class CaseTemplateSrv @Inject()(customFieldSrv: CustomFieldSrv, organisationSrv: OrganisationSrv)(implicit db: Database)
    extends VertexSrv[CaseTemplate, CaseTemplateSteps] {

  val caseTemplateCustomFieldSrv  = new EdgeSrv[CaseTemplateCustomField, CaseTemplate, CustomField]
  val caseTemplateOrganisationSrv = new EdgeSrv[CaseTemplateOrganisation, CaseTemplate, Organisation]
  val caseTemplateTaskSrv         = new EdgeSrv[CaseTemplateTask, CaseTemplate, Task]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CaseTemplateSteps = new CaseTemplateSteps(raw)

  def create(caseTemplate: CaseTemplate, organisation: Organisation with Entity, customFields: Seq[(String, Option[Any])])(
      implicit graph: Graph,
      authContext: AuthContext): RichCaseTemplate = {

    val createdCaseTemplate = create(caseTemplate)
    caseTemplateOrganisationSrv.create(CaseTemplateOrganisation(), createdCaseTemplate, organisation)
    val cfs = customFields.map {
      case (name, Some(value)) ⇒
        val customField = customFieldSrv.getOrFail(name)
        val caseTemplateCustomField =
          caseTemplateCustomFieldSrv.create(customField.`type`.setValue(CaseTemplateCustomField(), value), createdCaseTemplate, customField)
        CustomFieldWithValue(customField, caseTemplateCustomField)
      case (name, None) ⇒
        val customField             = customFieldSrv.getOrFail(name)
        val caseTemplateCustomField = caseTemplateCustomFieldSrv.create(CaseTemplateCustomField(), createdCaseTemplate, customField)
        CustomFieldWithValue(customField, caseTemplateCustomField)
    }
    RichCaseTemplate(createdCaseTemplate, organisation.name, cfs)
  }

  override def get(caseTemplateNameOrId: String)(implicit graph: Graph): CaseTemplateSteps =
    Try(UUID.fromString(caseTemplateNameOrId))
      .map(_ ⇒ initSteps.getById(caseTemplateNameOrId))
      .getOrElse(initSteps.getByName(caseTemplateNameOrId))

  def addTask(caseTemplate: CaseTemplate with Entity, task: Task with Entity)(implicit graph: Graph, authContext: AuthContext): Unit = {
    caseTemplateTaskSrv.create(CaseTemplateTask(), caseTemplate, task)
    ()
  }
}

@EntitySteps[CaseTemplate]
class CaseTemplateSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[CaseTemplate, CaseTemplateSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): CaseTemplateSteps = new CaseTemplateSteps(raw)

  def getById(id: String): CaseTemplateSteps = newInstance(raw.has(Key("_id") of id))

  def getByName(name: String): CaseTemplateSteps = newInstance(raw.has(Key("name") of name))

  def availableFor(authContext: Option[AuthContext]): CaseTemplateSteps =
    availableFor(authContext.getOrElse(throw AuthorizationError("access denied")).organisation)

  def availableFor(organisation: String): CaseTemplateSteps =
    newInstance(raw.filter(_.outTo[CaseTemplateOrganisation].value("name").is(organisation)))

  def customFields: ScalarSteps[CustomFieldWithValue] =
    ScalarSteps(
      raw
        .outToE[CaseTemplateCustomField]
        .inV()
        .path
        .map(path ⇒ CustomFieldWithValue(path.get[Vertex](2).as[CustomField], path.get[Edge](1).as[CaseTemplateCustomField])))

  def richCaseTemplate: ScalarSteps[RichCaseTemplate] =
    ScalarSteps(
      raw
        .project[Any]("caseTemplate", "organisation", "customFields")
        .by()
        .by(__[Vertex].outTo[CaseTemplateOrganisation].values[String]("name").fold.traversal)
        .by(__[Vertex].outToE[CaseTemplateCustomField].inV().path.fold.traversal)
        .map {
          case ValueMap(m) ⇒
            val customFieldValues = m
              .get[JList[Path]]("customFields")
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case ccf :: cf :: Nil ⇒ CustomFieldWithValue(cf.as[CustomField], ccf.as[CaseCustomField])
                case _                ⇒ throw InternalError("Not possible")
              }
            RichCaseTemplate(
              m.get[Vertex]("caseTemplate").as[CaseTemplate],
              onlyOneOf[String](m.get[JList[String]]("organisation")),
              customFieldValues
            )
        })

  def tasks = new TaskSteps(raw.inTo[TaskCaseTemplate])
}
