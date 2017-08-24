package org.elastic4play.controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._

import org.elastic4play.services.{ DBLists, Role }
import org.elastic4play.{ MissingAttributeError, Timed }

@Singleton
class DBListCtrl @Inject() (
    dblists: DBLists,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    implicit val ec: ExecutionContext) extends AbstractController(components) {

  @Timed("controllers.DBListCtrl.list")
  def list: Action[AnyContent] = authenticated(Role.read).async { implicit request ⇒
    dblists.listAll.map { listNames ⇒
      renderer.toOutput(OK, listNames)
    }
  }

  @Timed("controllers.DBListCtrl.listItems")
  def listItems(listName: String): Action[AnyContent] = authenticated(Role.read) { implicit request ⇒
    val (src, _) = dblists(listName).getItems[JsValue]
    val items = src.map { case (id, value) ⇒ s""""$id":$value""" }
      .intersperse("{", ",", "}")
    Ok.chunked(items).as("application/json")
  }

  @Timed("controllers.DBListCtrl.addItem")
  def addItem(listName: String): Action[Fields] = authenticated(Role.admin).async(fieldsBodyParser) { implicit request ⇒
    request.body.getValue("value").fold(Future.successful(NoContent)) { value ⇒
      dblists(listName).addItem(value).map { item ⇒
        renderer.toOutput(OK, item.id)
      }
    }
  }

  @Timed("controllers.DBListCtrl.deleteItem")
  def deleteItem(itemId: String): Action[AnyContent] = authenticated(Role.admin).async { implicit request ⇒
    dblists.deleteItem(itemId).map { _ ⇒
      NoContent
    }
  }

  @Timed("controllers.DBListCtrl.udpateItem")
  def updateItem(itemId: String): Action[Fields] = authenticated(Role.admin).async(fieldsBodyParser) { implicit request ⇒
    request.body.getValue("value")
      .map { value ⇒
        for {
          item ← dblists.getItem(itemId)
          _ ← dblists.deleteItem(item)
          newItem ← dblists(item.dblist).addItem(value)
        } yield renderer.toOutput(OK, newItem.id)
      }
      .getOrElse(Future.failed(MissingAttributeError("value")))
  }

  @Timed("controllers.DBListCtrl.itemExists")
  def itemExists(listName: String): Action[Fields] = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val itemKey = request.body.getString("key").getOrElse(throw MissingAttributeError("Parameter key is missing"))
    val itemValue = request.body.getValue("value").getOrElse(throw MissingAttributeError("Parameter value is missing"))
    dblists(listName).exists(itemKey, itemValue).map(r ⇒ Ok(Json.obj("found" → r)))
  }
}