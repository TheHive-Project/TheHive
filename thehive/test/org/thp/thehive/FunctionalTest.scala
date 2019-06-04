package org.thp.thehive

import java.util.Date

import scala.concurrent.{ExecutionContext, Promise}

import play.api.i18n.{I18nModule ⇒ PlayI18nModule}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{SimpleModule, bind, BuiltinModule ⇒ PlayBuiltinModule}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.{AhcWSModule ⇒ PlayAhcWSModule}
import play.api.mvc.{CookiesModule ⇒ PlayCookiesModule}
import play.api.test.{Helpers, PlaySpecification, TestServer}
import play.api.{Configuration, Environment}

import _root_.controllers.{AssetsConfiguration, AssetsConfigurationProvider, AssetsMetadata, AssetsMetadataProvider}
import com.typesafe.config.ConfigFactory
import org.specs2.specification.core.Fragments
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.scalligraph.{ScalligraphApplicationLoader, ScalligraphModule}
import org.thp.thehive.client.{ApplicationError, Authentication, TheHiveClient}
import org.thp.thehive.controllers.v1.{TestCase, TestUser}
import org.thp.thehive.dto.v1._
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.UserSrv

case class TestTask(
    title: String,
    description: Option[String],
    status: String,
    flag: Boolean,
    startDate: Option[Date],
    endDate: Option[Date],
    order: Int,
    dueDate: Option[Date]
)

object TestTask {

  def apply(task: OutputTask): TestTask =
    TestTask(task.title, task.description, task.status, task.flag, task.startDate, task.endDate, task.order, task.dueDate)
}

class FunctionalTest extends PlaySpecification {

  sequential

//  case class StableAudit(
//      _createdBy: String,
//      _updatedBy: Option[String] = None,
//      operation: String,
//      attributeName: Option[String] = None,
//      oldValue: Option[String] = None,
//      newValue: Option[String] = None,
//      objType: String,
//      summary: Map[String, Map[String, Int]])
//  object StableAudit {
//    def apply(audit: OutputAudit): StableAudit =
//      StableAudit(
//        audit._createdBy,
//        audit._updatedBy,
//        audit.operation,
//        audit.attributeName,
//        audit.oldValue,
//        audit.newValue,
//        audit.obj._type,
//        audit.summary)
//  }

  val janusGraphConfig =
    Configuration(ConfigFactory.parseString("""
                                              |db {
                                              |  provider: janusgraph
                                              |  storage.backend: inmemory
                                              |}
                                              |storage {
                                              |  provider: localfs
                                              |  localfs.location: /tmp
                                              |}
                                              |auth.provider: [local]
    """.stripMargin))
//  Configuration(ConfigFactory.parseString("""
//      |db {
//      |  provider: janusgraph
//      |  storage.backend: berkeleyje
//      |  storage.directory: /tmp/thehive-test.db
//      |}
//      |storage {
//      |  provider: localfs
//      |  localfs.location: /tmp
//      |}
//      |auth.provider: [local]
//    """.stripMargin))

  val orientdbConfig = Configuration(ConfigFactory.parseString("""
                                                                 |db.provider: orientdb
                                                                 |storage {
                                                                 |  provider: localfs
                                                                 |  localfs.location: /tmp
                                                                 |}
                                                                 |
                                                                 |auth.provider: [local]
   """.stripMargin))

  val neo4jConfig = Configuration(ConfigFactory.parseString("""
                                                              |db.provider: neo4j
                                                              |storage {
                                                              |  provider: localfs
                                                              |  localfs.location: /tmp
                                                              |}
                                                              |auth.provider: [local]
    """.stripMargin))
  Fragments.foreach(Seq(janusGraphConfig /*, orientdbConfig , neo4jConfig*/ )) { dbConfig ⇒
    val serverPromise: Promise[TestServer] = Promise[TestServer]
    lazy val server: TestServer            = serverPromise.future.value.get.get

    s"[${dbConfig.get[String]("db.provider")}] TheHive" should {
      lazy val app = server.application

      implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
      implicit lazy val ws: WSClient         = app.injector.instanceOf[WSClient]
      lazy val client                        = new TheHiveClient(s"http://127.0.0.1:${server.runningHttpPort.get}")

      "start the application" in {
        val applicationBuilder = GuiceApplicationBuilder()
          .configure(dbConfig)
          .load(
            new PlayBuiltinModule,
            new PlayI18nModule,
            new PlayCookiesModule,
            new PlayAhcWSModule,
            new ScalligraphModule,
            new TheHiveModule(Environment.simple(), dbConfig),
            new SimpleModule(
              bind[AssetsMetadata].toProvider[AssetsMetadataProvider],
              bind[AssetsConfiguration].toProvider[AssetsConfigurationProvider],
              bind[StorageSrv].to[LocalFileSystemStorageSrv]
//              bind[Database].to[AuditedDatabase]
            )
          )
        val application = applicationBuilder
          .load(ScalligraphApplicationLoader.loadModules(applicationBuilder.loadModules))
          .build()

        serverPromise.success(TestServer(port = Helpers.testServerPort, application = application))
        server.start()
        1 must_=== 1
      }

      var adminUser: TestUser = null
      var user2: TestUser     = null
      var user3: TestUser     = null
      var case1: TestCase     = null
      var case2: TestCase     = null
      var case2Id: String     = null
      var case3: TestCase     = null
      var task1: TestTask     = null

      {
        implicit val auth: Authentication = Authentication(UserSrv.initUser, UserSrv.initUserPassword)

        "get admin user" in {
          val asyncResp = client.user.get("admin")
          adminUser = TestUser(await(asyncResp))
          adminUser.login must_=== "admin"
        }

        "create new user" in {
          val asyncResp = client.user.create(InputUser("toom", "Thomas", Some("secret"), "analyst"))
          user2 = TestUser(await(asyncResp))
          val expected = TestUser("toom", "Thomas", "analyst", Set("manageAlert", "manageCase", "manageTask"), "default")
          user2 must_=== expected
        }

        "list users" in {
          val asyncResp = client.query(Json.obj("_name" → "listUser"), Json.obj("_name" → "toList"))
          val users     = (await(asyncResp) \ "result").as[Seq[OutputUser]].map(TestUser.apply)
          users must contain(exactly(adminUser, user2))
        }

        "return an authentication error if password is wrong" in {
          val asyncResp = client.query(Json.obj("_name" → "listUser"), Json.obj("_name" → "toList"))(ec, auth.copy(password = "nopassword"))
          val expected  = ApplicationError(401, Json.obj("type" → "AuthenticationError", "message" → "Authentication failure"))
          await(asyncResp) must throwA(expected)
        }

        "create a simple case" in {
          val asyncResp = client.`case`.create(InputCase("First case", "This case is the first case of functional tests"))
          case1 = TestCase(await(asyncResp))
          val expected = TestCase(
            title = "First case",
            description = "This case is the first case of functional tests",
            severity = 2,
            startDate = case1.startDate,
            flag = false,
            tlp = 2,
            pap = 2,
            status = "open",
            user = None
          )

          case1 must_=== expected
        }

        "create a custom field" in {
          val asyncResp = client.customFields.create(InputCustomField("businessUnit", "Business unit impacted by the incident", "string"))
          val expected  = OutputCustomField("businessUnit", "Business unit impacted by the incident", "string")
          await(asyncResp) must_=== expected
        }

        "create a case with custom fields" in {
          val asyncResp = client
            .`case`
            .create(
              InputCase(
                title = "Second case",
                description = "This case contains status, summary and custom fields",
                status = Some("resolved"),
                summary = Some("no comment"),
                customFieldValue = Seq(InputCustomFieldValue("businessUnit", Some("HR")))
              )
            )
          val outputCase = await(asyncResp)
          case2Id = outputCase._id
          case2 = TestCase(outputCase)
          val expected = TestCase(
            title = "Second case",
            description = "This case contains status, summary and custom fields",
            severity = 2,
            startDate = case2.startDate,
            flag = false,
            tlp = 2,
            pap = 2,
            status = "open",
            user = None,
            summary = Some("no comment"),
            customFields = Set(OutputCustomFieldValue("businessUnit", "Business unit impacted by the incident", "string", Some("HR")))
          )
          case2 must_=== expected
        }

        "list audit" in {
//          val asyncResp = client.audit.list
//          await(asyncResp).map(StableAudit.apply) must contain(
//            exactly(
////              StableAudit(_createdBy="system", operation="Creation", objType="ImpactStatus",     summary=Map("ImpactStatus"     → Map("Creation" → 1))),
////              StableAudit(_createdBy="system", operation="Creation", objType="ImpactStatus",     summary=Map("ImpactStatus"     → Map("Creation" → 1))),
////              StableAudit(_createdBy="system", operation="Creation", objType="ImpactStatus",     summary=Map("ImpactStatus"     → Map("Creation" → 1))),
////              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
////              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
////              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
////              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
////              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
////              StableAudit(_createdBy="system", operation="Creation", objType="Organisation",     summary=Map("Organisation"     → Map("Creation" → 1))),
//              StableAudit(_createdBy="system", operation="Creation", objType="User",             summary=Map("User"             → Map("Update"   → 1, "Creation" → 1))),
//              StableAudit(_createdBy="admin",  operation="Creation", objType="User",             summary=Map("User"             → Map("Update"   → 1, "Creation" → 1))),
//              StableAudit(_createdBy="admin",  operation="Creation", objType="Case",             summary=Map("Case"             → Map("Creation" → 1))),
//              StableAudit(_createdBy="admin",  operation="Creation", objType="CustomField",      summary=Map("CustomField"      → Map("Creation" → 1))),
//              StableAudit(_createdBy="admin",  operation="Creation", objType="Case",             summary=Map("Case"             → Map("Creation" → 1)))
//            ))
          pending
        }

        "list cases with custom fields" in {
          val asyncResp = client.query(
            Json.obj("_name" → "listCase"),
            Json.obj(
              "_name" → "filter",
              "_and" → Json
                .arr(Json.obj("_is" → Json.obj("customFieldName" → "businessUnit")), Json.obj("_is" → Json.obj("customFieldValue" → "HR")))
            ),
            Json.obj("_name" → "toList")
          )
          val cases = (await(asyncResp) \ "result").as[Seq[OutputCase]].map(TestCase.apply)
          cases must contain(exactly(case2))
        }

        "add a task to case 2" in {
          val asyncResp = client.task.create(InputTask(case2Id, "identification"))
          task1 = TestTask(await(asyncResp))
          val expected = TestTask(
            title = "identification",
            description = None,
            status = "waiting",
            flag = false,
            startDate = None,
            endDate = None,
            order = 0,
            dueDate = None
          )
          task1 must_=== expected
        }

        "list task for case 2" in {
          val asyncResp =
            client.query(Json.obj("_name" → "getCase", "id" → case2Id), Json.obj("_name" → "listTask"), Json.obj("_name" → "toList"))
          val tasks = (await(asyncResp) \ "result").as[Seq[OutputTask]].map(TestTask.apply)
          tasks must contain(exactly(task1))
        }

        "list cases" in {
          val asyncResp = client.query(Json.obj("_name" → "listCase"), Json.obj("_name" → "toList"))
          val cases     = (await(asyncResp) \ "result").as[Seq[OutputCase]].map(TestCase.apply)
          cases must contain(exactly(case1, case2))
        }

        "create a new organisation" in {
          val asyncResp = client.organisation.create(InputOrganisation("test"))
          await(asyncResp) must_=== OutputOrganisation("test")
        }

        "list organisations" in {
          val asyncResp     = client.query(Json.obj("_name" → "listOrganisation"), Json.obj("_name" → "toList"))
          val organisations = (await(asyncResp) \ "result").as[Seq[OutputOrganisation]]
          organisations must contain(exactly(OutputOrganisation("test"), OutputOrganisation("default")))
        }

        "create a new user in the test organisation" in {
          val asyncResp =
            client.user.create(InputUser("testAdmin", "Admin user in test organisation", Some("secret"), "admin", Some("test")))
          user3 = TestUser(await(asyncResp))
          val expected = TestUser("testAdmin", "Admin user in test organisation", "admin", Permissions.all.map(_.toString), "test")
          user3 must_== expected
        }
      }

      {
        implicit val auth: Authentication = Authentication("testAdmin", "secret")

        "list cases in test organisation" in {
          val asyncResp = client.query(Json.obj("_name" → "listCase"), Json.obj("_name" → "toList"))
          val cases     = (await(asyncResp) \ "result").as[Seq[OutputCase]]
          cases must beEmpty
        }

        "create a case in test organisation" in {
          val asyncResp =
            client.`case`.create(InputCase("test case", "Case in test organisation", severity = Some(1), pap = Some(1), user = Some("testAdmin")))
          case3 = TestCase(await(asyncResp))
          val expected = TestCase(
            title = "test case",
            description = "Case in test organisation",
            severity = 1,
            startDate = case3.startDate,
            flag = false,
            tlp = 2,
            pap = 1,
            status = "open",
            user = Some("testAdmin")
          )

          case3 must_=== expected
        }

        "list cases in test organisation" in {
          val asyncResp = client.query(Json.obj("_name" → "listCase"), Json.obj("_name" → "toList"))
          val cases     = (await(asyncResp) \ "result").as[Seq[OutputCase]].map(TestCase.apply)
          cases must contain(exactly(case3))
        }
      }

      {
//        implicit val auth: Authentication = Authentication(UserSrv.initUser, UserSrv.initUserPassword)

        "share a case from default organisation to test organisation" in {
//          val asyncResp = client.share.create(InputShare(case2._id, "test"))
//          await(asyncResp) must_=== OutputShare(case2._id, "test")
          pending
        }
      }

      {
        implicit val auth: Authentication = Authentication("testAdmin", "secret")

        "list cases in test organisation (should contain shared case)" in {
//          val asyncResp = client.query(Json.obj("_name" -> "listCase"), Json.obj("_name" -> "toList"))
//          val cases = (await(asyncResp) \ "result").as[Seq[OutputCase]].map(TestCase.apply)
//          cases must contain(exactly(case2, case3))
          pending
        }

        "create an alert" in {
          val asyncResp = client.alert.create(InputAlert("test", "source1", "sourceRef1", "new alert", "test alert"))
          val alert     = await(asyncResp)
          alert must_== OutputAlert(
            _id = alert._id,
            _createdBy = "testAdmin",
            _createdAt = alert._createdAt,
            `type` = "test",
            source = "source1",
            sourceRef = "sourceRef1",
            title = "new alert",
            description = "test alert",
            severity = 2,
            date = alert.date,
            tags = Set.empty,
            flag = false,
            tlp = 2,
            pap = 2,
            read = false,
            follow = true,
            customFields = Set.empty
          )
        }
      }

      "stop the application and drop database" in {
        server.stop()
        //app.injector.instanceOf[Database].drop()
        1 must_=== 1
      }
    }
  }
}
