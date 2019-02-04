package org.thp.thehive

import scala.concurrent.{ExecutionContext, Promise}

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{bind, SimpleModule}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.test.{Helpers, PlaySpecification, TestServer}
import play.api.{Configuration, Environment}

import com.typesafe.config.ConfigFactory
import org.specs2.specification.core.Fragments
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.{ScalligraphApplicationLoader, ScalligraphModule}
import org.thp.thehive.client.{ApplicationError, Authentication, TheHiveClient}
import org.thp.thehive.dto.v1._
import org.thp.thehive.services.AuditedDatabase

class FunctionalTest extends PlaySpecification {

  sequential

  case class StableAudit(
      _createdBy: String,
      _updatedBy: Option[String] = None,
      operation: String,
      attributeName: Option[String] = None,
      oldValue: Option[String] = None,
      newValue: Option[String] = None,
      objType: String,
      summary: Map[String, Map[String, Int]])
  object StableAudit {
    def apply(audit: OutputAudit): StableAudit =
      StableAudit(
        audit._createdBy,
        audit._updatedBy,
        audit.operation,
        audit.attributeName,
        audit.oldValue,
        audit.newValue,
        audit.obj._type,
        audit.summary)
  }

  val janusGraphConfig =
    Configuration(ConfigFactory.parseString("""
      |db.provider: janusgraph
      |storage.backend: inmemory
      |auth.provider: [local]
    """.stripMargin))
//  Configuration(ConfigFactory.parseString("""
//      |db.provider: janusgraph
//      |storage.backend: berkeleyje
//      |storage.directory: /tmp/thehive-test.db
//      |auth.provider: [local]
//    """.stripMargin))

  val orientdbConfig = Configuration(ConfigFactory.parseString("""
     |db.provider: orientdb
     |auth.provider: [local]
   """.stripMargin))

  val neo4jConfig = Configuration(ConfigFactory.parseString("""
      |db.provider: neo4j
      |auth.provider: [local]
    """.stripMargin))
  Fragments.foreach(Seq(janusGraphConfig, orientdbConfig /*, neo4jConfig*/ )) { dbConfig ⇒
    val serverPromise: Promise[TestServer] = Promise[TestServer]
    lazy val server: TestServer            = serverPromise.future.value.get.get

    s"[${dbConfig.get[String]("db.provider")}] TheHive" should {
      lazy val app                 = server.application
      lazy val initialUserLogin    = "admin"
      lazy val initialUserName     = "Administrator"
      lazy val initialUserPassword = "azerty"

      implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
      implicit lazy val ws: WSClient         = app.injector.instanceOf[WSClient]
      lazy val client                        = new TheHiveClient(s"http://127.0.0.1:${server.runningHttpPort.get}")

      "start the application" in {
        val applicationBuilder = GuiceApplicationBuilder()
          .configure(dbConfig)
          .load(
            new play.api.inject.BuiltinModule,
            new play.api.i18n.I18nModule,
            new play.api.mvc.CookiesModule,
            new play.api.libs.ws.ahc.AhcWSModule,
            new ScalligraphModule,
            new TheHiveModule(Environment.simple(), dbConfig),
            new SimpleModule(bind[Database].to[AuditedDatabase])
          )
        val application = applicationBuilder
          .load(ScalligraphApplicationLoader.loadModules(applicationBuilder.loadModules))
          .build()

        serverPromise.success(TestServer(port = Helpers.testServerPort, application = application))
        server.start()
        1 must_=== 1
      }

      var user1: OutputUser = null
      var user2: OutputUser = null
      var user3: OutputUser = null
      var case1: OutputCase = null
      var case2: OutputCase = null
      var case3: OutputCase = null
      var task1: OutputTask = null

      "create initial user" in {
        val asyncResp =
          client.user.createInitial(InputUser(initialUserLogin, initialUserName, Seq("read", "write", "admin"), Some(initialUserPassword)))
        user1 = await(asyncResp)
        val expected = OutputUser(initialUserLogin, initialUserName, Set("read", "write", "admin"), "default")
        user1 must_=== expected
      }

      {
        implicit val auth: Authentication = Authentication(initialUserLogin, initialUserPassword)

        "create new user" in {
          val asyncResp = client.user.create(InputUser("toom", "Thomas", Seq("read", "write"), Some("secret")))
          user2 = await(asyncResp)
          val expected = OutputUser("toom", "Thomas", Set("read", "write"), "default")
          user2 must_=== expected
        }

        "list users" in {
          val asyncResp = client.user.list
          await(asyncResp) must contain(exactly(user1, user2))
        }

        "return an authentication error if password is wrong" in {
          val asyncResp = client.user.list(ec, auth.copy(password = "nopassword"))
          val expected  = ApplicationError(401, Json.obj("type" → "AuthenticationError", "message" → "Authentication failure"))
          await(asyncResp) must throwA(expected)
        }

        "create a simple case" in {
          val asyncResp = client.`case`.create(InputCase("First case", "This case is the first case of functional tests"))
          case1 = await(asyncResp)
          val expected = OutputCase(
            _id = case1._id,
            _createdBy = "admin",
            _createdAt = case1._createdAt,
            number = 1,
            title = "First case",
            description = "This case is the first case of functional tests",
            severity = 2,
            startDate = case1.startDate,
            flag = false,
            tlp = 2,
            pap = 2,
            status = "open",
            user = Some("admin")
          )

          case1 must_=== expected
        }

        "create a custom field" in {
          val asyncResp = client.customFields.create(InputCustomField("businessUnit", "Business unit impacted by the incident", "string"))
          val expected  = OutputCustomField("businessUnit", "Business unit impacted by the incident", "string")
          await(asyncResp) must_=== expected
        }

        "create a case with custom fields" in {
          val asyncResp = client.`case`.create(
            InputCase(
              title = "Second case",
              description = "This case contains status, summary and custom fields",
              status = Some("resolved"),
              summary = Some("no comment"),
              customFieldValue = Seq(InputCustomFieldValue("businessUnit", Some("HR")))
            ))
          case2 = await(asyncResp)
          val expected = OutputCase(
            _id = case2._id,
            _createdBy = "admin",
            _createdAt = case2._createdAt,
            number = 2,
            title = "Second case",
            description = "This case contains status, summary and custom fields",
            severity = 2,
            startDate = case2.startDate,
            flag = false,
            tlp = 2,
            pap = 2,
            status = "open",
            user = Some("admin"),
            summary = Some("no comment"),
            customFields = Set(OutputCustomFieldValue("businessUnit", "Business unit impacted by the incident", "string", Some("HR")))
          )
          case2 must_=== expected
        }

        "list audit" in {
          // format: off
          val asyncResp = client.audit.list
          await(asyncResp).map(StableAudit.apply) must contain(
            exactly(
              StableAudit(_createdBy="system", operation="Creation", objType="ImpactStatus",     summary=Map("ImpactStatus"     → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="ImpactStatus",     summary=Map("ImpactStatus"     → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="ImpactStatus",     summary=Map("ImpactStatus"     → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="ResolutionStatus", summary=Map("ResolutionStatus" → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="Organisation",     summary=Map("Organisation"     → Map("Creation" → 1))),
              StableAudit(_createdBy="system", operation="Creation", objType="User",             summary=Map("User"             → Map("Update"   → 1, "Creation" → 1))),
              StableAudit(_createdBy="admin",  operation="Creation", objType="User",             summary=Map("User"             → Map("Update"   → 1, "Creation" → 1))),
              StableAudit(_createdBy="admin",  operation="Creation", objType="Case",             summary=Map("Case"             → Map("Creation" → 1))),
              StableAudit(_createdBy="admin",  operation="Creation", objType="CustomField",      summary=Map("CustomField"      → Map("Creation" → 1))),
              StableAudit(_createdBy="admin",  operation="Creation", objType="Case",             summary=Map("Case"             → Map("Creation" → 1)))
            ))
          // format: on
        }

        "list cases with custom fields" in {
          val asyncResp = client.query(
            Json.obj("_name" → "listCase"),
            Json.obj(
              "_name" → "filter",
              "_and" → Json
                .arr(Json.obj("_is" → Json.obj("customFieldName" → "businessUnit")), Json.obj("_is" → Json.obj("customFieldValue" → "HR")))),
            Json.obj("_name" → "toList")
          )
          (await(asyncResp) \ "result").as[Seq[JsValue]] must contain(exactly(Json.toJson(case2)))
        }

        "add a task to case 2" in {
          val asyncResp = client.task.create(InputTask(case2._id, "identification"))
          task1 = await(asyncResp)
          val expected = OutputTask(
            title = "identification",
            description = None,
            status = "waiting",
            flag = false,
            startDate = None,
            endDate = None,
            order = 0,
            dueDate = None)
          task1 must_=== expected
        }

        "list task for case 2" in {
          val asyncResp =
            client.query(Json.obj("_name" → "getCase", "id" → case2._id), Json.obj("_name" → "listTask"), Json.obj("_name" → "toList"))
          (await(asyncResp) \ "result").as[Seq[JsValue]] must contain(exactly(Json.toJson(task1)))
        }

        "list cases" in {
          val asyncResp = client.`case`.list
          await(asyncResp) must contain(exactly(case1, case2))
        }

        "create a new organisation" in {
          val asyncResp = client.organisation.create(InputOrganisation("test"))
          await(asyncResp) must_=== OutputOrganisation("test")
        }

        "list organisations" in {
          val asyncResp = client.organisation.list
          await(asyncResp) must contain(exactly(OutputOrganisation("test"), OutputOrganisation("default")))
        }

        "create a new user in the test organisation" in {
          val asyncResp =
            client.user.create(InputUser("testAdmin", "Admin user in test organisation", Seq("read", "write", "admin"), Some("secret"), Some("test")))
          user3 = await(asyncResp)
          val expected = OutputUser("testAdmin", "Admin user in test organisation", Set("read", "write", "admin"), "test")
          user3 must_=== expected
        }
      }

      {
        implicit val auth: Authentication = Authentication("testAdmin", "secret")

        "list cases in test organisation" in {
          val asyncResp = client.`case`.list
          await(asyncResp) must beEmpty
        }

        "create a case in test organisation" in {
          val asyncResp = client.`case`.create(InputCase("test case", "Case in test organisation", severity = Some(1), pap = Some(1)))
          case3 = await(asyncResp)
          val expected = OutputCase(
            _id = case3._id,
            _createdBy = "testAdmin",
            _createdAt = case3._createdAt,
            number = 3,
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
          val asyncResp = client.`case`.list
          await(asyncResp) must contain(exactly(case3))
        }
      }

      {
        implicit val auth: Authentication = Authentication(initialUserLogin, initialUserPassword)

        "share a case from default organisation to test organisation" in {
          val asyncResp = client.share.create(InputShare(case2._id, "test"))
          await(asyncResp) must_=== OutputShare(case2._id, "test")
        }
      }

      {
        implicit val auth: Authentication = Authentication("testAdmin", "secret")

        "list cases in test organisation (should contain shared case)" in {
          val asyncResp = client.`case`.list
          await(asyncResp) must contain(exactly(case2, case3))
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
            status = "New",
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
