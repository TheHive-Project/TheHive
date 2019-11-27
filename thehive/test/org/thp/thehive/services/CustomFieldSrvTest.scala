package org.thp.thehive.services

import java.util.Date

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.{AppBuilder, RichSeq}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.libs.json._
import play.api.test.PlaySpecification

import scala.util.{Success, Try}

class CustomFieldSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val customFieldSrv: CustomFieldSrv    = app.instanceOf[CustomFieldSrv]
    val caseSrv: CaseSrv                  = app.instanceOf[CaseSrv]
    val db: Database                      = app.instanceOf[Database]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    def createCF(name: String, desc: String, cfType: CustomFieldType.Value, options: Seq[JsValue]) = {
      val cf = db.tryTransaction { implicit graph =>
        customFieldSrv.create(
          CustomField(
            name,
            s"displayed $name",
            desc,
            cfType,
            mandatory = false,
            options
          )
        )
      }

      cf must beSuccessfulTry

      cf.get
    }

    s"[$name] custom field service" should {
      "create a custom field" in {
        val cf = createCF("cf 1", "desc cf 1", CustomFieldType.boolean, Seq(JsBoolean(true)))

        cf.name shouldEqual "cf 1"
        cf.displayName shouldEqual "displayed cf 1"
        cf.description shouldEqual "desc cf 1"
        cf.options shouldEqual Seq(JsBoolean(true))
      }

      "delete custom fields" in {
        val usedCustomFields = db.roTransaction(implicit graph => caseSrv.get("#3").customFields.richCustomField.toList)

        usedCustomFields must not(beEmpty)

        db.tryTransaction { implicit graph =>
          usedCustomFields.toTry(richCF => customFieldSrv.delete(richCF.customField, force = true))
        } must beSuccessfulTry

        db.roTransaction(implicit graph => caseSrv.get("#3").customFields.richCustomField.toList) must beEmpty
      }

      "count use of custom fields" in {
        val cf2 = createCF("cf 2", "desc cf 2", CustomFieldType.string, Nil)
        val cf3 = createCF("cf 3", "desc cf 3", CustomFieldType.float, Seq(JsNumber(23.23)))
        db.tryTransaction { implicit graph =>
          caseSrv.setOrCreateCustomField(caseSrv.get("#1").getOrFail().get, "cf 2", None)
          caseSrv.setOrCreateCustomField(caseSrv.get("#1").getOrFail().get, "cf 3", Some(23.23))
          caseSrv.setOrCreateCustomField(caseSrv.get("#2").getOrFail().get, "cf 3", Some(23.23))
        }

        db.roTransaction(implicit graph => customFieldSrv.useCount(cf2)) shouldEqual Map("Case" -> 1)
        db.roTransaction(implicit graph => customFieldSrv.useCount(cf3)) shouldEqual Map("Case" -> 2)
      }

      "update a custom field" in {
        val now   = new Date()
        val cf    = createCF("cf 4", "desc cf 4", CustomFieldType.date, Seq(JsString(now.toString)))
        val updates = Seq(
          PropertyUpdater(FPathElem("description"), "updated") { (vertex, _, _, _) =>
            vertex.property("description", "updated")
            Success(Json.obj("description" -> "updated"))
          }
        )

        db.tryTransaction { implicit graph =>
          customFieldSrv.update(customFieldSrv.get(cf), updates)
        } must beSuccessfulTry

        val updatedCF = db.roTransaction(implicit graph => customFieldSrv.get(cf).getOrFail().get)

        updatedCF.description shouldEqual "updated"
      }
    }
  }
}
