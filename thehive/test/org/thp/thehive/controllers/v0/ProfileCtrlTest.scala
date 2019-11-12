package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.{AppBuilder, AuthenticationError}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputProfile
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class ProfileCtrlTest extends PlaySpecification with Mockito {
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
    val profileCtrl: ProfileCtrl = app.instanceOf[ProfileCtrl]
    val theHiveQueryExecutor     = app.instanceOf[TheHiveQueryExecutor]

    def createProfile(name: String, permissions: Set[String]) = {
      val request = FakeRequest("POST", "/api/profile")
        .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")
        .withJsonBody(Json.parse(s"""{"name": "$name", "permissions": ${Json.toJson(permissions)}}"""))
      val result = profileCtrl.create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsJson(result).as[OutputProfile]
    }

    s"$name profile controller" should {

      "create a profile if allowed" in {
        val profile = createProfile("name test 1", Set("lol"))

        profile.name shouldEqual "name test 1"
        profile.permissions shouldEqual List("lol")

        val requestFailed = FakeRequest("POST", "/api/profile")
          .withHeaders("user" -> "user4@thehive.local", "X-Organisation" -> "admin")
          .withJsonBody(Json.parse(s"""{"name": "$name", "permissions": ${Json.toJson(Set("perm"))}}"""))
        val resultFailed = profileCtrl.create(requestFailed)

        status(resultFailed) must throwA[AuthenticationError]
      }

      "get a profile" in {
        val profile = createProfile("name test 2", Set("manageCase", "manageUser"))

        val request = FakeRequest("GET", s"/api/profile/${profile.id}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val result = profileCtrl.get(profile.id)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        contentAsJson(result).as[OutputProfile].name shouldEqual "name test 2"
      }

      "update a profile" in {
        val profile = createProfile("title test 3", Set.empty)

        val request = FakeRequest("PATCH", s"/api/profile/${profile.id}")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")
          .withJsonBody(Json.parse("""{"permissions": ["manageTask"]}"""))
        val result = profileCtrl.update(profile.id)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val updatedProfile = contentAsJson(result).as[OutputProfile]

        updatedProfile.permissions shouldEqual List("manageTask")
      }

      "delete a profile if allowed" in {
        val profile = createProfile("title test 4", Set.empty)

        val request = FakeRequest("DELETE", s"/api/profile/${profile.id}")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")
        val result = profileCtrl.delete(profile.id)(request)

        status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

        val requestGet = FakeRequest("GET", s"/api/profile/${profile.id}")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")
        val resultGet = profileCtrl.get(profile.id)(requestGet)

        status(resultGet) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultGet)}")

        val requestFailed = FakeRequest("DELETE", s"/api/profile/all")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")
        val resultFailed = profileCtrl.delete("all")(requestFailed)

        status(resultFailed) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")
      }

      "search a profile" in {
        createProfile("title test 5", Set(Permissions.manageCase,
          Permissions.manageObservable,
          Permissions.manageAlert,
          Permissions.manageTask,
          Permissions.manageAction,
          Permissions.manageAnalyse))
        createProfile("title test 6", Set(Permissions.manageCase,
          Permissions.manageObservable))
        createProfile("title test 7", Set.empty)
        val json = Json.parse("""{
             "range":"all",
             "sort":[
                "-updatedAt",
                "-createdAt"
             ],
             "query":{
             }
          }""".stripMargin)

        val request = FakeRequest("POST", s"/api/profile/_search")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(json)
        val result = theHiveQueryExecutor.profile.search(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        contentAsJson(result).as[List[OutputProfile]] must not(beEmpty)
      }
    }
  }
}
