package org.thp.thehive.services

import java.util.{Date, List ⇒ JList}

import scala.collection.JavaConverters._

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.UpdateOps
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.{EdgeSrv, _}
import org.thp.scalligraph.{AuthorizationError, EntitySteps, FPath, InternalError}
import org.thp.thehive.models._

@Singleton
class AlertSrv @Inject()(caseSrv: CaseSrv, customFieldSrv: CustomFieldSrv, caseTemplateSrv: CaseTemplateSrv, taskSrv: TaskSrv, userSrv: UserSrv)(
    implicit db: Database)
    extends VertexSrv[Alert, AlertSteps] {

  val alertUserSrv         = new EdgeSrv[AlertUser, Alert, User]
  val alertCustomFieldSrv  = new EdgeSrv[AlertCustomField, Alert, CustomField]
  val alertOrganisationSrv = new EdgeSrv[AlertOrganisation, Alert, Organisation]
  val alertCaseSrv         = new EdgeSrv[AlertCase, Alert, Case]
  val alertCaseTemplateSrv = new EdgeSrv[AlertCaseTemplate, Alert, CaseTemplate]

  def create(
      alert: Alert,
      user: User with Entity,
      organisation: Organisation with Entity,
      customFields: Map[String, Any],
      caseTemplate: Option[RichCaseTemplate])(implicit graph: Graph, authContext: AuthContext): RichAlert = {
    val createdAlert = create(alert)
    alertUserSrv.create(AlertUser(), createdAlert, user)
    alertOrganisationSrv.create(AlertOrganisation(), createdAlert, organisation)
    caseTemplate.foreach(ct ⇒ alertCaseTemplateSrv.create(AlertCaseTemplate(), createdAlert, ct.caseTemplate))
    val cfs = customFields.map {
      case (name, value) ⇒
        val cf               = customFieldSrv.getOrFail(name)
        val alertCustomField = alertCustomFieldSrv.create(cf.`type`.setValue(AlertCustomField(), value), createdAlert, cf)
        CustomFieldWithValue(cf, alertCustomField)
    }.toSeq
    RichAlert(createdAlert, user.login, organisation.name, cfs, None, caseTemplate.map(_.name))
  }

  def isAvailableFor(alertId: String)(implicit graph: Graph, authContext: AuthContext): Boolean =
    get(alertId).availableFor(Some(authContext)).isDefined

  def setCustomField(`alert`: Alert with Entity, customFieldName: String, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext): AlertCustomField with Entity =
    setCustomField(`alert`, customFieldSrv.getOrFail(customFieldName), value)

  // FIXME add or update
  def setCustomField(`alert`: Alert with Entity, customField: CustomField with Entity, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext): AlertCustomField with Entity = {
    val alertCustomField = customField.`type`.asInstanceOf[CustomFieldType[Any]].setValue(AlertCustomField(), value)
    alertCustomFieldSrv.create(alertCustomField, `alert`, customField)
  }

  def markAsUnread(alertId: String)(implicit graph: Graph, authContext: AuthContext): Unit =
    db.update(graph, authContext, Model.vertex[Alert], alertId, Map(FPath("read") → UpdateOps.SetAttribute(false)))

  def markAsRead(alertId: String)(implicit graph: Graph, authContext: AuthContext): Unit =
    db.update(graph, authContext, Model.vertex[Alert], alertId, Map(FPath("read") → UpdateOps.SetAttribute(true)))

  def followAlert(alertId: String)(implicit graph: Graph, authContext: AuthContext): Unit =
    db.update(graph, authContext, Model.vertex[Alert], alertId, Map(FPath("follow") → UpdateOps.SetAttribute(true)))

  def unfollowAlert(alertId: String)(implicit graph: Graph, authContext: AuthContext): Unit =
    db.update(graph, authContext, Model.vertex[Alert], alertId, Map(FPath("read") → UpdateOps.SetAttribute(false)))

  def createCase(alert: RichAlert, user: User with Entity, organisation: Organisation with Entity)(
      implicit graph: Graph,
      authContext: AuthContext): RichCase = {
    val caseTemplate = alert.caseTemplate.map(caseTemplateSrv.get(_).richCaseTemplate.getOrFail())
    val customField = caseTemplate
      .fold[Seq[(String, Any)]](Nil) { ct ⇒
        ct.customFields.map(f ⇒ f.name → f.value)
      }
      .toMap ++ alert.customFields.map(f ⇒ f.name → f.value)
    val `case` = Case(
      number = 0,
      title = caseTemplate.flatMap(_.titlePrefix).getOrElse("") + alert.title,
      description = alert.description,
      severity = alert.severity,
      startDate = new Date,
      endDate = None,
      tags = alert.tags,
      flag = alert.flag,
      tlp = alert.tlp,
      pap = alert.pap,
      status = CaseStatus.open,
      summary = None
    )

    val createdCase = caseSrv.create(`case`, user, organisation, customField, caseTemplate)

    caseTemplate.foreach { ct ⇒
      caseTemplateSrv.get(ct.caseTemplate).tasks.toList().foreach { task ⇒
        taskSrv.create(task, createdCase.`case`)
      }
    }

    importObservables(alert.alert, createdCase.`case`)
    alertCaseSrv.create(AlertCase(), alert.alert, createdCase.`case`)
    createdCase
  }

  def mergeInCase(alertId: String, caseId: String)(implicit graph: Graph, authContext: AuthContext): Unit = {
    // TODO add observables
    val alert       = getOrFail(alertId)
    val `case`      = caseSrv.getOrFail(caseId)
    val description = `case`.description + s"\n  \n#### Merged with alert #${alert.sourceRef} ${alert.title}\n\n${alert.description.trim}"
    caseSrv.update(caseId, "description", description)
    importObservables(alert, `case`)
  }

  def importObservables(alert: Alert with Entity, `case`: Case with Entity) = {
    // TODO add observables
  }

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AlertSteps = new AlertSteps(raw)
}

@EntitySteps[Alert]
class AlertSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Alert, AlertSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): AlertSteps = new AlertSteps(raw)

  def availableFor(authContext: Option[AuthContext]): AlertSteps =
    availableFor(authContext.getOrElse(throw AuthorizationError("access denied")).organisation)

  def availableFor(organisation: String): AlertSteps =
    newInstance(raw.filter(_.outTo[AlertOrganisation].value("name").is(organisation)))

  def richAlert: ScalarSteps[RichAlert] =
    ScalarSteps(
      raw
        .project[Any]("alert", "user", "organisation", "customFields", "caseId", "caseTemplate")
        .by()
        .by(__[Vertex].outTo[AlertUser].values[String]("login").fold.traversal)
        .by(__[Vertex].outTo[AlertOrganisation].values[String]("name").fold.traversal)
        .by(__[Vertex].outToE[AlertCustomField].inV().path.fold.traversal)
        .by(__[Vertex].outTo[AlertCase].values[String]("_id").fold.traversal)
        .by(__[Vertex].outTo[AlertCaseTemplate].values[String]("name").fold.traversal)
        .map {
          case ValueMap(m) ⇒
            val customFieldValues = m
              .get[JList[Path]]("customFields")
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case ccf :: cf :: Nil ⇒ CustomFieldWithValue(cf.as[CustomField], ccf.as[AlertCustomField])
                case _                ⇒ throw InternalError("Not possible")
              }
            RichAlert(
              m.get[Vertex]("alert").as[Alert],
              onlyOneOf[String](m.get[JList[String]]("user")),
              onlyOneOf[String](m.get[JList[String]]("organisation")),
              customFieldValues,
              atMostOneOf[String](m.get[JList[String]]("caseId")),
              atMostOneOf[String](m.get[JList[String]]("caseTemplate"))
            )
        })
}
