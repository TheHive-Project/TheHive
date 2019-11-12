package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.{JsObject, Reads}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class DescribeCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val describeCtrl: DescribeCtrl = app.instanceOf[DescribeCtrl]

    def checkValues[T](l: List[JsObject], attribute: String, values: List[T])(implicit r: Reads[List[T]]) =
      l.find(o => (o \ "name").as[String] == attribute) must beSome.which(
        is => (is \ "values").as[List[T]] must containAllOf(values)
      )

    s"$name describe controller" should {

      "describe a model by its name if existing" in {
        val request = FakeRequest("GET", s"/api/dashboard/case")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val result = describeCtrl.describe("case")(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val jsonResult = contentAsJson(result).as[JsObject]
        val attributes = (jsonResult \ "attributes").as[List[JsObject]]

        attributes must not(beEmpty)
        (jsonResult \ "label").as[String] shouldEqual "case"
        (jsonResult \ "path").as[String] shouldEqual "/case"
        (jsonResult \ "attributes" \\ "name").map(_.as[String]).toList must contain("customFields.string1")
        checkValues[String](attributes, "impactStatus", List("NoImpact", "WithImpact", "NotApplicable"))
        checkValues[String](attributes, "status", List("Open", "Resolved", "Deleted", "Duplicated"))
        checkValues[String](attributes, "resolutionStatus", List("FalsePositive", "Duplicated", "Indeterminate", "TruePositive", "Other"))
        checkValues[Int](attributes, "pap", List(0, 1, 2, 3))
        checkValues[Int](attributes, "tlp", List(0, 1, 2, 3))

        val requestGet = FakeRequest("GET", s"/api/describe/yolo")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val resultGet = describeCtrl.describe("yolo")(requestGet)

        status(resultGet) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultGet)}")
      }

      "describe all available models" in {
        val request = FakeRequest("GET", s"/api/describe/_all")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val result = describeCtrl.describeAll(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        contentAsJson(result).as[JsObject].keys must not(beEmpty)
      }
    }
  }
}
