package org.thp.thehive.controllers.v0

import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.Hasher
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputCustomField
import org.thp.thehive.models.ObservableType
import org.thp.thehive.services.CustomFieldOps._
import org.thp.thehive.services.CustomFieldSrv
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success}

class ListCtrl(
    entrypoint: Entrypoint,
    db: Database,
    customFieldSrv: CustomFieldSrv
) {

  def list: Action[AnyContent] =
    entrypoint("list")
      .auth { _ =>
        Success(Results.Ok(Json.arr("list_artifactDataType", "case_metrics", "ui_settings")))
      }

  def listItems(listName: String): Action[AnyContent] =
    entrypoint("list list items")
      .auth { _ =>
        val result = listName match {
          case "list_artifactDataType" =>
            val objectTypes = ObservableType.initialValues.toList.map { ot =>
              val id = Hasher("MD5").fromString(ot.name).head.toString // this Traversable.head can't fail
              id -> JsString(ot.name)
            }
            JsObject(objectTypes.toMap)
          case "case_metrics" => JsObject.empty
          case "ui_settings"  => JsObject.empty
          case "custom_fields" =>
            val cf = db
              .roTransaction { implicit grap =>
                customFieldSrv.startTraversal.toSeq
              }
              .map(cf => cf._id.toString -> cf.toJson)
            JsObject(cf)
          case _ => JsObject.empty
        }
        Success(Results.Ok(result))
      }

  // TODO implement those as admin custom fields management seems to use them
  def addItem(listName: String): Action[AnyContent] =
    entrypoint("add item to list")
      .extract("value", FieldsParser.jsObject.on("value"))
      .auth { request =>
        val value: JsObject = request.body("value")
        listName match {
          case "custom_fields" => {
              for {
                inputCustomField <- value.validate[InputCustomField]
              } yield inputCustomField
            } fold (
              errors => Failure(new Exception(errors.mkString)),
              _ => Success(Results.Ok)
            )
          case _ => Success(Results.Locked(""))
        }
      }

  def deleteItem(itemId: String): Action[AnyContent] =
    entrypoint("delete list item") { _ =>
      Success(Results.Locked(""))
    }

  def updateItem(itemId: String): Action[AnyContent] =
    entrypoint("update list item") { _ =>
      Success(Results.Locked(""))
    }

  def itemExists(listName: String): Action[AnyContent] =
    entrypoint("check if item exist in list")
      .extract("key", FieldsParser.string.on("key"))
      .extract("value", FieldsParser.string.on("value"))
      .authRoTransaction(db) { request => implicit graph =>
        listName match {
          case "custom_fields" =>
            val v: String = request.body("value")
            customFieldSrv
              .startTraversal
              .getByName(v)
              .getOrFail("CustomField")
              .map(f => Results.Conflict(Json.obj("found" -> f.toJson)))
              .orElse(Success(Results.Ok))
          case _ => Success(Results.Locked(""))
        }
      }
}
