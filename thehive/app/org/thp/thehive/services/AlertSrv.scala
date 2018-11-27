package org.thp.thehive.services

import java.util.{List ⇒ JList}

import scala.collection.JavaConverters._

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Path
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.{EdgeSrv, _}
import org.thp.scalligraph.{AuthorizationError, EntitySteps, InternalError}
import org.thp.thehive.models._

@Singleton
class AlertSrv @Inject()(customFieldSrv: CustomFieldSrv)(implicit db: Database) extends VertexSrv[Alert, AlertSteps] {

  val alertUserSrv         = new EdgeSrv[AlertUser, Alert, User]
  val alertCustomFieldSrv  = new EdgeSrv[AlertCustomField, Alert, CustomField]
  val alertOrganisationSrv = new EdgeSrv[AlertOrganisation, Alert, Organisation]

  def create(alert: Alert, user: User with Entity, organisation: Organisation with Entity, customFields: Seq[(String, Any)])(
      implicit graph: Graph,
      authContext: AuthContext): RichAlert = {
    val createdAlert = create(alert)
    alertUserSrv.create(AlertUser(), createdAlert, user)
    alertOrganisationSrv.create(AlertOrganisation(), createdAlert, organisation)
    val cfs = customFields.map {
      case (name, value) ⇒
        val cf = customFieldSrv.getOrFail(name)
        alertCustomFieldSrv.create(cf.`type`.setValue(AlertCustomField(), value), createdAlert, cf)
        CustomFieldWithValue(cf.name, cf.description, cf.`type`.name, value)
    }
    RichAlert(createdAlert, user.login, organisation.name, cfs)
  }

  def setCustomField(`alert`: Alert with Entity, customFieldName: String, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext): AlertCustomField with Entity =
    setCustomField(`alert`, customFieldSrv.getOrFail(customFieldName), value)

  def setCustomField(`alert`: Alert with Entity, customField: CustomField with Entity, value: Any)(
      implicit graph: Graph,
      authContext: AuthContext): AlertCustomField with Entity = {
    val alertCustomField = customField.`type`.asInstanceOf[CustomFieldType[Any]].setValue(AlertCustomField(), value)
    alertCustomFieldSrv.create(alertCustomField, `alert`, customField)
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
        .project[Any]("alert", "user", "organisation", "customFields")
        .by()
        .by(__[Vertex].outTo[AlertUser].values[String]("login").fold.traversal)
        .by(__[Vertex].outTo[AlertOrganisation].values[String]("name").fold.traversal)
        .by(__[Vertex].outToE[AlertCustomField].inV().path.fold.traversal)
        .map {
          case ValueMap(m) ⇒
            val customFieldValues = m
              .get[JList[Path]]("customFields")
              .asScala
              .map(_.asScala.takeRight(2).toList.asInstanceOf[List[Element]])
              .map {
                case ccf :: cf :: Nil ⇒
                  val customField      = cf.as[CustomField]
                  val alertCustomField = ccf.as[AlertCustomField].asInstanceOf[AlertCustomField]
                  CustomFieldWithValue(
                    customField.name,
                    customField.description,
                    customField.`type`.name,
                    customField.`type`.getValue(alertCustomField))
                case _ ⇒ throw InternalError("Not possible")
              }
            RichAlert(
              m.get[Vertex]("alert").as[Alert],
              onlyOneOf[String](m.get[JList[String]]("user")),
              onlyOneOf[String](m.get[JList[String]]("organisation")),
              customFieldValues
            )
        })
}
