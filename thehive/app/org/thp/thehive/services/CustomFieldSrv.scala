package org.thp.thehive.services

import java.util.{Map => JMap}

import akka.actor.ActorRef
import gremlin.scala._
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services.{IntegrityCheckOps, RichElement, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps._
import org.thp.scalligraph.{EntitySteps, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.libs.json.{JsNull, JsObject, JsValue}
import shapeless.HNil

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

@Singleton
class CustomFieldSrv @Inject() (auditSrv: AuditSrv, organisationSrv: OrganisationSrv, @Named("integrity-check-actor") integrityCheckActor: ActorRef)(
    implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[CustomField, CustomFieldSteps] {

  override def createEntity(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("CustomField")
    super.createEntity(e)
  }

  def create(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] =
    for {
      created <- createEntity(e)
      _       <- auditSrv.customField.create(created, created.toJson)
    } yield created

  override def exists(e: CustomField)(implicit graph: Graph): Boolean = initSteps.getByName(e.name).exists()

  def delete(c: CustomField with Entity, force: Boolean)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(c).remove() // TODO use force
    organisationSrv.getOrFail(authContext.organisation).flatMap { organisation =>
      auditSrv.customField.delete(c, organisation)
    }
  }

  def useCount(c: CustomField with Entity)(implicit graph: Graph): Map[String, Int] =
    get(c)
      .in()
      .groupCount(By[String](T.label))
      .headOption()
      .fold(Map.empty[String, Int])(_.asScala.collect { case (k, v) if k != "Audit" => k -> v.toInt }.toMap)

  override def update(
      steps: CustomFieldSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(CustomFieldSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (customFieldSteps, updatedFields) =>
        customFieldSteps
          .newInstance()
          .getOrFail()
          .flatMap(auditSrv.customField.update(_, updatedFields))
    }

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CustomFieldSteps = new CustomFieldSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): CustomFieldSteps =
    if (db.isValidId(idOrName)) super.getByIds(idOrName)
    else initSteps.getByName(idOrName)
}

@EntitySteps[CustomField]
class CustomFieldSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph)
    extends VertexSteps[CustomField](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): CustomFieldSteps = new CustomFieldSteps(newRaw)
  override def newInstance(): CustomFieldSteps                             = new CustomFieldSteps(raw.clone())

  def get(idOrName: String): CustomFieldSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): CustomFieldSteps = new CustomFieldSteps(raw.has(Key("name") of name))

}

class CustomFieldValueSteps(raw: GremlinScala[Edge])(implicit @Named("with-thehive-schema") db: Database, graph: Graph)
    extends EdgeSteps[CustomFieldValue[_], Product, CustomField](raw) {
  override def newInstance(): CustomFieldValueSteps = new CustomFieldValueSteps(raw.clone())

  override def newInstance(newRaw: GremlinScala[Edge]): CustomFieldValueSteps = new CustomFieldValueSteps(newRaw)

  def setValue(value: Option[Any]): Try[Unit] = {
    val customFieldValueLabel = StepLabel[Edge]()
    val typeLabel             = StepLabel[String]()

    raw
      .asInstanceOf[GremlinScala.Aux[Edge, HNil]]
      .as(customFieldValueLabel)
      .inV()
      .value("type")
      .as(typeLabel)
      .select()
      .traversal
      .asScala
      .toTry {
        case (edge, typeName) =>
          val tpe = CustomFieldType.get(typeName)
          tpe.setValue(new CustomFieldValueEdge(db, edge), value)
      }
      .map(_ => ())
  }

  private def edgeNameType: GremlinScala[(Edge, String, String)] = {
    val customFieldValueLabel = StepLabel[Edge]()
    val typeLabel             = StepLabel[JMap[AnyRef, AnyRef]]()
    raw
      .asInstanceOf[GremlinScala.Aux[Edge, HNil]]
      .as(customFieldValueLabel)
      .inV()
      .valueMap("name", "type")
      .as(typeLabel)
      .select(customFieldValueLabel.name, typeLabel.name)
      .map {
        case SelectMap(map) =>
          val ValueMap(values) = map.get(typeLabel)
          (map.get(customFieldValueLabel), values.get("name").asInstanceOf[String], values.get("type").asInstanceOf[String])
      }
  }

  def nameJsonValue: Traversal[(String, JsValue), (String, JsValue)] =
    Traversal(
      edgeNameType
        .map {
          case (edge, name, tpe) =>
            name -> CustomFieldType.get(tpe).getJsonValue(new CustomFieldValueEdge(db, edge))
        }
    )

  def jsonValue: Traversal[JsValue, JsValue] =
    Traversal(
      edgeNameType
        .map {
          case (edge, _, tpe) =>
            CustomFieldType.get(tpe).getJsonValue(new CustomFieldValueEdge(db, edge))
        }
    )

  def nameValue: Traversal[(String, Option[Any]), (String, Option[Any])] =
    Traversal(
      edgeNameType
        .map {
          case (edge, name, tpe) =>
            name -> CustomFieldType.get(tpe).getValue(new CustomFieldValueEdge(db, edge))
        }
    )

  def value: Traversal[Any, Any] =
    Traversal(
      edgeNameType
        .map {
          case (edge, _, tpe) =>
            CustomFieldType.get(tpe).getValue(new CustomFieldValueEdge(db, edge)).getOrElse(JsNull)
        }
    )

  def richCustomField: Traversal[RichCustomField, RichCustomField] = {
    val customFieldValueLabel = StepLabel[Edge]()
    val customFieldLabel      = StepLabel[Vertex]()
    Traversal(
      raw
        .asInstanceOf[GremlinScala.Aux[Edge, HNil]]
        .as(customFieldValueLabel)
        .inV()
        .as(customFieldLabel)
        .select(customFieldValueLabel.name, customFieldLabel.name)
        .map {
          case SelectMap(m) => RichCustomField(m.get(customFieldLabel).as[CustomField], new CustomFieldValueEdge(db, m.get(customFieldValueLabel)))
        }
    )
  }

//  def remove()     = raw.drop().i
}

class CustomFieldIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: CustomFieldSrv)
    extends IntegrityCheckOps[CustomField] {
  override def resolve(entities: List[CustomField with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      tail.foreach(service.get(_).remove())
      Success(())
    case _ => Success(())
  }
}
