package org.thp.thehive.services.notification.notifiers

import java.util.{HashMap => JHashMap}

import gremlin.scala.{Key, P}
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.DatabaseBuilder
import org.thp.thehive.services.{AuditSrv, CaseSrv, UserSrv}
import play.api.test.PlaySpecification

import scala.collection.JavaConverters._
import scala.util.Try

class NotificationTemplateTest extends PlaySpecification {
  val dummyUserSrv                      = DummyUserSrv(userId = "admin@thehive.local")
  implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
//    specs(dbProvider.name, app)
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], authContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val templateEngine = new Object with Template {}

    s"[$name] template engine" should {
      "plop" in {
        val template =
          """Dear {{user.name}},
            |you have a new notification:
            |
            |The {{audit.objectType}} {{audit.objectId}} has been {{audit.action}}d by {{audit._createdBy}}
            |
            |{{#with object~}}
            |  {{#if (eq _type "Case")}}
            |    {{~title}}
            |  {{else}}
            |    {{~_type}} is not a case
            |  {{/if}}
            |{{~/with}}
            |
            |Audit ({{audit.requestId}}): {{audit.action}} {{audit.objectType}} {{audit.objectId}} by {{audit._createdBy}}
            |Context {{context._id}}""".stripMargin

        val model = new JHashMap[String, AnyRef]
        model.put(
          "audit",
          Map("objectType" -> "Case", "objectId" -> "2231", "action" -> "create", "_createdBy" -> "admin@thehive.local", "requestId" -> "testRequest").asJava
        )
        model.put("object", Map("_type" -> "Case", "title" -> "case title").asJava)
        model.put("user", Map("name"    -> "Thomas").asJava)
        model.put("context", Map("_id"  -> "2231").asJava)
        val message = templateEngine.handlebars.compileInline(template).apply(model)
        message must beEqualTo("""Dear Thomas,
                                 |you have a new notification:
                                 |
                                 |The Case 2231 has been created by admin@thehive.local
                                 |
                                 |case title
                                 |  
                                 |
                                 |Audit (testRequest): create Case 2231 by admin@thehive.local
                                 |Context 2231""".stripMargin)
      }
    }

    s"[$name] template engine" should {
      val db: Database = app.instanceOf[Database]
      val template =
        """Dear {{user.name}},
          |you have a new notification:
          |
          |The {{audit.objectType}} {{audit.objectId}} has been {{audit.action}}d by {{audit._createdBy}}
          |
          |{{#with object~}}
          |  {{#if (eq _type "Case")}}
          |    {{~title}}
          |  {{else}}
          |    {{~_type}} is not a case
          |  {{/if}}
          |{{~/with}}
          |
          |Audit ({{audit.requestId}}): {{audit.action}} {{audit.objectType}} {{audit.objectId}} by {{audit._createdBy}}
          |Context {{context._id}}""".stripMargin
      val caseSrv  = app.instanceOf[CaseSrv]
      val auditSrv = app.instanceOf[AuditSrv]
      val userSrv  = app.instanceOf[UserSrv]

      "build properly message" in {

        val message = db.tryTransaction { implicit graph =>
          for {
            case4 <- caseSrv.get("#4").getOrFail()
            _     <- caseSrv.addTags(case4, Set("emailer test"))
            _     <- caseSrv.addTags(case4, Set("emailer test")) // this is needed to make AuditSrv write Audit in DB
            audit <- auditSrv.initSteps.has(Key("objectId"), P.eq(case4._id)).getOrFail()
            user  <- userSrv.get("user1@thehive.local").getOrFail()
            msg   <- templateEngine.buildMessage(template, audit, Some(case4), Some(case4), user, "http://localhost/")
          } yield msg
        }
        message must beSuccessfulTry.which(
          _ must beMatching("""(?m)Dear Thomas,
                              |you have a new notification:
                              |
                              |The Case \d+ has been updated by admin@thehive.local
                              |
                              |case#4
                              |  
                              |
                              |Audit \(testRequest\): update Case \d+ by admin@thehive.local
                              |Context \d+""".stripMargin)
        )
      }
    }
  }
}
