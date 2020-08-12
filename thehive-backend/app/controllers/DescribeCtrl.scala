package controllers

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import models.Roles

import org.elastic4play.controllers.{Authenticated, Renderer}
import org.elastic4play.models.{Attribute, AttributeDefinition, BaseModelDef}
import org.elastic4play.models.JsonFormat.attributeDefinitionWrites
import org.elastic4play.services.{DBLists, ModelSrv}

@Singleton
class DescribeCtrl @Inject()(
    dblists: DBLists,
    modelSrv: ModelSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext
) extends AbstractController(components) {

  private def modelToJson(model: BaseModelDef): JsObject = {
    val attributeDefinitions = model.attributes.flatMap {
      case attribute: Attribute[t] ⇒ attribute.format.definition(dblists, attribute)
    } ++ model.computedMetrics.keys.map { computedMetricName ⇒
      AttributeDefinition(s"computed.$computedMetricName", "number", s"Computed metric $computedMetricName", Nil, Nil)
    }
    Json.obj("label" → model.label, "path" → model.path, "attributes" → attributeDefinitions)
  }

  def describe(modelName: String): Action[AnyContent] = authenticated(Roles.read) { _ ⇒
    modelSrv(modelName)
      .map { model ⇒
        renderer.toOutput(OK, modelToJson(model))
      }
      .getOrElse(NotFound(s"Model $modelName not found"))
  }

  private val allModels: Seq[String] = Seq("case", "case_artifact", "case_task", "case_task_log", "alert", "case_artifact_job", "audit", "action")

  def describeAll: Action[AnyContent] = authenticated(Roles.read) { _ ⇒
    val entityDefinitions = modelSrv
      .list
      .collect {
        case model if allModels.contains(model.modelName) ⇒ model.modelName → modelToJson(model)
      }
    renderer.toOutput(OK, JsObject(entityDefinitions))
  }
}
