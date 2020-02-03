package org.thp.thehive.services

import java.util.{Map => JMap}

import scala.collection.JavaConverters._
import scala.util.Try

import play.api.libs.json.{JsObject, JsValue}

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services.{RichElement, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{EdgeSteps, Traversal, ValueMap, VertexSteps}
import org.thp.scalligraph.{EntitySteps, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import shapeless.HNil

@Singleton
class CustomFieldSrv @Inject() (implicit db: Database, auditSrv: AuditSrv) extends VertexSrv[CustomField, CustomFieldSteps] {

  def create(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] =
    for {
      created <- createEntity(e)
      _       <- auditSrv.customField.create(created, created.toJson)
    } yield created

  def delete(c: CustomField with Entity, force: Boolean)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(c).remove() // TODO use force
    auditSrv.customField.delete(c)
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
class CustomFieldSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[CustomField](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): CustomFieldSteps = new CustomFieldSteps(newRaw)
  override def newInstance(): CustomFieldSteps                             = new CustomFieldSteps(raw.clone())

  def get(idOrName: String): CustomFieldSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): CustomFieldSteps = new CustomFieldSteps(raw.has(Key("name") of name))

}

class CustomFieldValueSteps(raw: GremlinScala[Edge])(implicit db: Database, graph: Graph)
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

  def jsonValue: Traversal[(String, JsValue), (String, JsValue)] = {
    val customFieldValueLabel = StepLabel[Edge]()
    val typeLabel             = StepLabel[JMap[AnyRef, AnyRef]]()
    Traversal(
      raw
        .asInstanceOf[GremlinScala.Aux[Edge, HNil]]
        .as(customFieldValueLabel)
        .inV()
        .valueMap("name", "type")
        .as(typeLabel)
        .select()
        .map {
          case (edge, ValueMap(map)) =>
            map.get[String]("name") -> CustomFieldType.get(map.get[String]("type")).getJsonValue(new CustomFieldValueEdge(db, edge))
        }
    )
  }

  def richCustomField: Traversal[RichCustomField, RichCustomField] = {
    val customFieldValueLabel = StepLabel[Edge]()
    val customFieldLabel      = StepLabel[Vertex]()
    Traversal(
      raw
        .asInstanceOf[GremlinScala.Aux[Edge, HNil]]
        .as(customFieldValueLabel)
        .inV()
        .as(customFieldLabel)
        .select()
        .map {
          case (cfv, cf) => RichCustomField(cf.as[CustomField], new CustomFieldValueEdge(db, cfv))
        }
    )
  }

//  def remove()     = raw.drop().i
}
