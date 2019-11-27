package org.thp.thehive.services

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{EntitySteps, RichSeq}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.libs.json.{JsBoolean, JsValue}
import play.api.test.PlaySpecification

import scala.util.Try

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
    }
  }
}
