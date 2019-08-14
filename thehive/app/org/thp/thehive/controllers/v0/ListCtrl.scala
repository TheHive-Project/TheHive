package org.thp.thehive.controllers.v0
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.Hasher
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.{CustomFieldSrv, ObservableTypeSrv}
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class ListCtrl @Inject()(entryPoint: EntryPoint, db: Database, customFieldSrv: CustomFieldSrv, observableTypeSrv: ObservableTypeSrv) {
  import CustomFieldConversion._

  def list: Action[AnyContent] =
    entryPoint("list")
      .auth { _ =>
        Success(Results.Ok(Json.arr("list_artifactDataType", "case_metrics", "ui_settings")))
      }

  def listItems(listName: String): Action[AnyContent] =
    entryPoint("list list items")
      .auth { _ =>
        val result = listName match {
          case "list_artifactDataType" =>
            val objectTypes = observableTypeSrv.initialValues.toList.map { ot =>
              val id = Hasher("MD5").fromString(ot.name).head.toString
              id -> JsString(ot.name)
            }
            JsObject(objectTypes.toMap)
          case "case_metrics" => JsObject.empty
          case "ui_settings"  => JsObject.empty
          case "custom_fields" =>
            val cf = db
              .roTransaction { implicit grap =>
                customFieldSrv.initSteps.toList
              }
              .map(cf => cf._id -> cf.toJson)
            JsObject(cf)
          case _ => JsObject.empty
        }
        Success(Results.Ok(result))
      }

  def addItem(listName: String): Action[AnyContent] = entryPoint("add item to list") { _ =>
    Success(Results.Locked(""))
  }

  def deleteItem(itemId: String): Action[AnyContent] = entryPoint("delete list item") { _ =>
    Success(Results.Locked(""))
  }

  def updateItem(itemId: String): Action[AnyContent] = entryPoint("update list item") { _ =>
    Success(Results.Locked(""))
  }

  def itemExists(listName: String): Action[AnyContent] =
    entryPoint("check if item exist in list")
      .extract("key", FieldsParser.string.on("key"))
      .extract("value", FieldsParser.string.on("value"))
      .authRoTransaction(db) { request => implicit graph =>
        listName match {
          case "custom_fields" =>
            val v: String = request.body("value")
            customFieldSrv
              .initSteps
              .get(v)
              .getOrFail()
              .map(f => Results.Conflict(Json.obj("found" -> f.toJson)))
              .orElse(Success(Results.Ok))
          case _ => Success(Results.Locked(""))
        }
      }
}
