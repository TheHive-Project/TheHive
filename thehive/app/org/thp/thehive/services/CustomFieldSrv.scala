package org.thp.thehive.services

import java.util.{Map => JMap}

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.{Edge}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal._
import org.thp.scalligraph.{EntityIdOrName, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldOps._
import play.api.libs.json.{JsObject, JsValue}

import scala.util.{Success, Try}

@Singleton
class CustomFieldSrv @Inject() (auditSrv: AuditSrv, organisationSrv: OrganisationSrv, @Named("integrity-check-actor") integrityCheckActor: ActorRef)(
    implicit db: Database
) extends VertexSrv[CustomField] {

  override def createEntity(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] = {
    integrityCheckActor ! EntityAdded("CustomField")
    super.createEntity(e)
  }

  def create(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] =
    for {
      created <- createEntity(e)
      _       <- auditSrv.customField.create(created, created.toJson)
    } yield created

  override def exists(e: CustomField)(implicit graph: Graph): Boolean = startTraversal.getByName(e.name).exists

  def delete(c: CustomField with Entity, force: Boolean)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(c).remove() // TODO use force
    organisationSrv.getOrFail(authContext.organisation).flatMap { organisation =>
      auditSrv.customField.delete(c, organisation)
    }
  }

  def useCount(c: CustomField with Entity)(implicit graph: Graph): Map[String, Long] =
    get(c)
      .in()
      .groupCount(_.byLabel)
      .headOption
      .fold(Map.empty[String, Long])(_.filterNot(_._1 == "Audit"))

  override def update(
      traversal: Traversal.V[CustomField],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[CustomField], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (customFieldSteps, updatedFields) =>
        customFieldSteps
          .clone()
          .getOrFail("CustomFields")
          .flatMap(auditSrv.customField.update(_, updatedFields))
    }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[CustomField] =
    startTraversal.getByName(name)
}

object CustomFieldOps {

  implicit class CustomFieldOpsDefs(traversal: Traversal.V[CustomField]) {
    def get(idOrName: EntityIdOrName): Traversal.V[CustomField] =
      idOrName.fold(traversal.getByIds(_), getByName)

    def getByName(name: String): Traversal.V[CustomField] = traversal.has(_.name, name)
  }

  implicit class CustomFieldValueOpsDefs[C <: CustomFieldValue[_]](traversal: Traversal.E[C]) {

    def setValue(value: Option[Any]): Try[Unit] = {
      val customFieldValueLabel = StepLabel.identity[Edge]
      val typeLabel             = StepLabel[CustomFieldType.Value, String, Converter[CustomFieldType.Value, String]]

      traversal
        .setConverter[Edge, Converter.Identity[Edge]](Converter.identity)
        .as(customFieldValueLabel)
        .inV
        .v[CustomField]
        .value(_.`type`)
        .as(typeLabel)
        .select((customFieldValueLabel, typeLabel))
        .toSeq
        .toTry {
          case (edge, typeName) =>
            val tpe = CustomFieldType.map(typeName)
            tpe.setValue(new CustomFieldValueEdge(edge), value)
        }
        .map(_ => ())
    }

    private def edgeNameType
        : Traversal[(Edge, String, CustomFieldType.Value), JMap[String, Any], Converter[(Edge, String, CustomFieldType.Value), JMap[String, Any]]] = {
      val customFieldValueLabel = StepLabel.identity[Edge]
      val nameLabel             = StepLabel.v[CustomField]
      val typeLabel             = StepLabel.v[CustomField]
      traversal
        .setConverter[Edge, Converter.Identity[Edge]](Converter.identity)
        .as(customFieldValueLabel)
        .inV
        .v[CustomField]
        .as(nameLabel, typeLabel)
        .select(_.apply(customFieldValueLabel)(_.by).apply(nameLabel)(_.byValue(_.name)).apply(typeLabel)(_.byValue(_.`type`)))
    }

    def nameJsonValue: Traversal[(String, JsValue), JMap[String, Any], Converter[(String, JsValue), JMap[String, Any]]] =
      edgeNameType
        .domainMap {
          case (edge, name, tpe) =>
            name -> CustomFieldType.map(tpe).getJsonValue(new CustomFieldValueEdge(edge))
        }

    def jsonValue: Traversal[JsValue, JMap[String, Any], Converter[JsValue, JMap[String, Any]]] =
      edgeNameType
        .domainMap {
          case (edge, _, tpe) =>
            CustomFieldType.map(tpe).getJsonValue(new CustomFieldValueEdge(edge))
        }

    def nameValue: Traversal[(String, Option[_]), JMap[String, Any], Converter[(String, Option[_]), JMap[String, Any]]] =
      edgeNameType
        .domainMap {
          case (edge, name, tpe) =>
            name -> CustomFieldType.map(tpe).getValue(new CustomFieldValueEdge(edge))
        }

    def selectValue: Traversal[Any, JMap[String, Any], Converter[Any, JMap[String, Any]]] =
      traversal.choose[String, Any](
        _.on(
          _.inV
            .v[CustomField]
            .value(_.`type`)
        ).option("boolean", _.value(_.booleanValue).cast[Any, Any].setConverter[Any, Converter.Identity[Any]](Converter.identity[Any]))
          .option("date", _.value(_.dateValue).cast[Any, Any].setConverter[Any, Converter.Identity[Any]](Converter.identity[Any]))
          .option("float", _.value(_.floatValue).cast[Any, Any].setConverter[Any, Converter.Identity[Any]](Converter.identity[Any]))
          .option("integer", _.value(_.integerValue).cast[Any, Any].setConverter[Any, Converter.Identity[Any]](Converter.identity[Any]))
          .option("string", _.value(_.stringValue).cast[Any, Any].setConverter[Any, Converter.Identity[Any]](Converter.identity[Any]))
      )

//    def value: Traversal[Any, JMap[String, Any], Converter[Any, JMap[String, Any]]] =
//      edgeNameType
//        .map {
//          case (edge, _, tpe) =>
//            CustomFieldType.map(tpe).getValue(new CustomFieldValueEdge(edge)).getOrElse(JsNull)
//        }

    def richCustomField: Traversal[RichCustomField, JMap[String, Any], Converter[RichCustomField, JMap[String, Any]]] = {
      val customFieldValueLabel = StepLabel.identity[Edge]
      val customFieldLabel      = StepLabel.v[CustomField]
      traversal
        .setConverter[Edge, Converter.Identity[Edge]](Converter.identity)
        .as(customFieldValueLabel)
        .inV
        .v[CustomField]
        .as(customFieldLabel)
        .select((customFieldValueLabel, customFieldLabel))
        .domainMap {
          case (customFieldValue, customField) => RichCustomField(customField, new CustomFieldValueEdge(customFieldValue))
        }
    }

    //  def remove()     = raw.drop().i
  }

}

class CustomFieldIntegrityCheckOps @Inject() (val db: Database, val service: CustomFieldSrv) extends IntegrityCheckOps[CustomField] {
  override def resolve(entities: Seq[CustomField with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }
}
