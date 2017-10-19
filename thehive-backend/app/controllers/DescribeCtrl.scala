package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import models.Roles

import org.elastic4play.controllers.{ Authenticated, Renderer }
import org.elastic4play.models.Attribute
import org.elastic4play.models.JsonFormat.attributeDefinitionWrites
import org.elastic4play.services.{ DBLists, ModelSrv }

@Singleton
class DescribeCtrl @Inject() (
    dblists: DBLists,
    modelSrv: ModelSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) {

  def describe(modelName: String): Action[AnyContent] = authenticated(Roles.read) { implicit request ⇒
    modelSrv(modelName)
      .map { model ⇒
        val attributeDefinitions = model.attributes.flatMap {
          case attribute: Attribute[t] ⇒ attribute.format.definition(dblists, attribute)
        }
        renderer.toOutput(OK, attributeDefinitions)
      }
      .getOrElse(NotFound(s"Model $modelName not found"))
  }
}
