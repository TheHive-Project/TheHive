package org.thp.thehive.controllers.v0
import scala.util.Success

import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.CustomFieldSrv

@Singleton
class ListCtrl @Inject()(entryPoint: EntryPoint, db: Database, customFieldSrv: CustomFieldSrv) {
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
            Json.obj(
              "f20bbf7d45ceef499337d8a569930cc6" -> "url",
              "ddb0b7bcefaf0d33e9a8bb4f9a618599" -> "other",
              "da2104ec1c910ff2f049ddff5911c969" -> "user-agent",
              "ca088650506d64d495bc1a26c204c313" -> "regexp",
              "c189583488f19b62b00de7b56663f4ad" -> "mail_subject",
              "c183469522b196284008932bf9326f40" -> "registry",
              "b6da459cd373c6add0403e53e4cb693f" -> "mail",
              "b508e9e97b9e4cd3a872b055f217751a" -> "autonomous-system",
              "a748df22126f9cac50da922bc8ae9dd6" -> "domain",
              "9d400a69945d7cc31733e5e19b2f7ebd" -> "custom-type",
              "885681d3eb2fb83082a54c1434fda88c" -> "ip",
              "867344c6e79110b4fcdf43ac8f1e41b1" -> "uri_path",
              "7b8f3a0870d8671e18174d374e422655" -> "filename",
              "6739cee9f6dc6d33693658994978008e" -> "hash",
              "48f7f5121d5c0c659afb28c4541efa1b" -> "sometesttype",
              "46bcb895313c0750321a43b7a47cdc87" -> "file",
              "165fbd99c923d40e8873a8c1c333573b" -> "fqdn"
            )
          case "case_metrics" => JsObject.empty
          case "ui_settings"  => JsObject.empty
          case "custom_fields" =>
            val cf = db
              .transaction { implicit grap =>
                customFieldSrv.initSteps.toList()
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

  def itemExists(listName: String): Action[AnyContent] = entryPoint("check if item exist in list") { _ =>
    Success(Results.Locked(""))
  }
}
