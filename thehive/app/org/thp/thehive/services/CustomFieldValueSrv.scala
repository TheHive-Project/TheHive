package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal._
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.scalligraph.{EntityId, EntityIdOrName}
import org.thp.thehive.models._
import play.api.libs.json.JsValue

import java.util.{Map => JMap}
import scala.reflect.runtime.{universe => ru}
import scala.util.Try

class CustomFieldValueSrv extends VertexSrv[CustomFieldValue] with TheHiveOpsNoDeps {

  val customFieldValueCustomFieldSrv = new EdgeSrv[CustomFieldValueCustomField, CustomFieldValue, CustomField]

  def createCustomField(
      element: Product with Entity,
      customField: CustomField with Entity,
      value: JsValue,
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[CustomFieldValue with Entity] =
    for {
      cfv  <- customField.`type`.setValue(CustomFieldValue(element._id, customField.name, order), value)
      cfve <- createEntity(cfv)
      _    <- customFieldValueCustomFieldSrv.create(CustomFieldValueCustomField(), cfve, customField)
    } yield cfve

  def updateValue(customFieldValue: CustomFieldValue with Entity, value: JsValue, order: Option[Int])(implicit graph: Graph): Try[CustomFieldValue] =
    get(customFieldValue).`type`.getOrFail("CustomField").flatMap(updateValue(customFieldValue, _, value, order))

  def updateValue(
      customFieldValue: CustomFieldValue with Entity,
      customFieldType: CustomFieldType[_],
      value: JsValue,
      order: Option[Int]
  )(implicit graph: Graph): Try[CustomFieldValue with Entity] =
    for {
      traversal <- customFieldType.updateValue(get(customFieldValue), value)
      cfv       <- traversal.merge(order)((t, o) => t.update(_.order, Some(o))).getOrFail("CustomField")
    } yield cfv

  def delete(valueId: EntityId)(implicit graph: Graph): Try[Unit] = Try(getByIds(valueId).remove())
  override def delete(e: CustomFieldValue with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    super.delete(e)
}

trait CustomFieldValueOps {
  _: TraversalOps =>
  implicit class CustomFieldValueOpsDefs(traversal: Traversal.V[CustomFieldValue]) {
    def get(idOrName: EntityIdOrName): Traversal.V[CustomFieldValue] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.empty)

    def customField: Traversal.V[CustomField] = traversal.out[CustomFieldValueCustomField].v[CustomField]

    def `type`: Traversal[CustomFieldType[_], String, Converter[CustomFieldType[_], String]] = customField.value(_.`type`)

    def richCustomField: Traversal[RichCustomField, JMap[String, Any], Converter[RichCustomField, JMap[String, Any]]] =
      traversal.project(_.by.by(_.customField)).domainMap {
        case (cfv, cf) => RichCustomField(cf, cfv)
      }

    def `case`: Traversal.V[Case] = traversal.in[CaseCustomFieldValue].v[Case]

    def alert: Traversal.V[Alert] = traversal.in[AlertCustomFieldValue].v[Alert]

    def caseTemplate: Traversal.V[CaseTemplate] = traversal.in[CaseTemplateCustomFieldValue].v[CaseTemplate]
  }
}

class EntityWithCustomFieldOpsNoDepsDefs[E <: Product, CV <: Product: ru.TypeTag](traversal: Traversal.V[E], ops: TheHiveOpsNoDeps) {
  import ops._

  def richCustomFields: Traversal[RichCustomField, JMap[String, Any], Converter[RichCustomField, JMap[String, Any]]] = {
    val cfvLabel = StepLabel.v[CustomFieldValue]
    val cfLabel  = StepLabel.v[CustomField]
    customFieldValue
      .as(cfvLabel)
      .customField
      .as(cfLabel)
      .select((cfvLabel, cfLabel))
      .domainMap {
        case (cfv, cf) => RichCustomField(cf, cfv)
      }
  }

  def customFieldJsonValue(customField: EntityIdOrName): Traversal[JsValue, JMap[String, Any], Converter[JsValue, JMap[String, Any]]] =
    customField
      .fold(customFieldValue.hasId(_), customFieldValue.has(_.name, _))
      .project(_.by.by(_.customField))
      .domainMap {
        case (cfv, cf) => cf.`type`.getJsonValue(cfv)
      }

  def customFieldValue: Traversal.V[CustomFieldValue] =
    traversal.out[CV].v[CustomFieldValue]
}

class EntityWithCustomFieldOpsDefs[E <: Product, CV <: Product: ru.TypeTag](traversal: Traversal.V[E], ops: TheHiveOps) {
  import ops._

  def hasCustomField(customField: EntityIdOrName): Traversal.V[E] =
    customField.fold(
      cid => traversal.hasId(customFieldValueSrv.getByIds(cid)(traversal.graph).value(_.elementId).toSeq: _*),
      cname =>
        traversal
          .filter(
            _.out[CV]
              .v[CustomFieldValue]
              .has(_.name, cname)
          )
    )

  def hasNotCustomField(customField: EntityIdOrName): Traversal.V[E] =
    customField.fold(
      cid => traversal.hasNotId(customFieldValueSrv.getByIds(cid)(traversal.graph).value(_.elementId).toSeq: _*),
      cname =>
        traversal
          .filterNot(
            _.out[CV]
              .v[CustomFieldValue]
              .has(_.name, cname)
          )
    )
}
