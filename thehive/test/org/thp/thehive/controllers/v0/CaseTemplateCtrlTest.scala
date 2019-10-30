package org.thp.thehive.controllers.v0

import org.specs2.matcher.MatchResult
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputCaseTemplate
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import scala.util.{Random, Try}

class CaseTemplateCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val caseTemplateCtrl = app.instanceOf[CaseTemplateCtrl]

    def getAndTestCaseTemplate(name: String, description: String)(body: OutputCaseTemplate => MatchResult[Any]) = {
      val json = s"""{
            "name":"$name",
            "titlePrefix":"test",
            "severity":1,
            "tlp":2,
            "pap":2,
            "tags":[
               "tg${Random.nextInt}",
               "tg${Random.nextInt}"
            ],
            "tasks":[
               {
                  "order":0,
                  "title":"task template ${Random.nextInt}",
                  "group":"default",
                  "description":"Alios autem dicere aiunt multo etiam inhumanius (quem locum breviter paulo ante perstrinxi) praesidii adiumentique causa, non benevolentiae neque caritatis..."
                }
            ],
            "customFields":{},
            "description":"$description"
          }""".stripMargin
      val request = FakeRequest("POST", "/api/case/template")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(
          Json.parse(json)
        )
      val result = caseTemplateCtrl.create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      body(contentAsJson(result).as[OutputCaseTemplate])
    }

    s"$name case template controller" should {
      "create a template" in getAndTestCaseTemplate("tmp basic case", "description tmp case 1") { output =>
        output.tags.size shouldEqual 2
        output.name shouldEqual "tmp basic case"
        output.tlp must beSome(2)
        output.pap must beSome(2)
        output.severity must beSome(1)
        output.tasks must not(beEmpty)
        output.tasks.head.title must contain("task template")
      }

      "get a template" in getAndTestCaseTemplate("tmp basic case 2", "description tmp case 2") { output =>
        val request = FakeRequest("GET", s"/api/case/template/${output._id}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val result = caseTemplateCtrl.get(output._id)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      }

      "delete a template" in getAndTestCaseTemplate("tmp basic case 3", "description tmp case 3") { output =>
        val request = FakeRequest("DELETE", s"/api/case/template/${output.name}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val result = caseTemplateCtrl.delete(output.name)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val requestGet = FakeRequest("GET", s"/api/case/template/${output._id}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val resultGet = caseTemplateCtrl.get(output._id)(requestGet)

        status(resultGet) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultGet)}")
      }

      "update a template" in getAndTestCaseTemplate("tmp basic case 4", "description tmp case 4") { output =>
        val request = FakeRequest("PATCH", s"/api/case/template/${output.name}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(
            Json.parse("""{"displayName": "patched"}""")
          )
        val result = caseTemplateCtrl.update(output._id)(request)

        status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

        val requestGet = FakeRequest("GET", s"/api/case/template/${output._id}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val resultGet = caseTemplateCtrl.get(output._id)(requestGet)

        status(resultGet) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultGet)}")
      }
    }
  }
}
