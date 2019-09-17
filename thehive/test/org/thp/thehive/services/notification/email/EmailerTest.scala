package org.thp.thehive.services.notification.email

import com.github.jknack.handlebars.Handlebars
import gremlin.scala.{Key, P}
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.{AuditSrv, CaseSrv, OrganisationSrv, UserSrv}
import play.api.libs.mailer.MailerClient
import play.api.test.PlaySpecification

import scala.util.Try

class EmailerTest extends PlaySpecification {
  val dummyUserSrv                      = DummyUserSrv()
  implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], authContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] emailer service" should {
      val db: Database = app.instanceOf[Database]
      val template =
        """Dear {{user.name}}, <br/>
        you have a new notification: <br/>
        Audit ({{audit.requestId}}): {{audit.action}} {{audit.objectType}} {{audit.objectId}} by {{audit._createdBy}} on {{audit._createdAt}} <br/>
        Context {{context._model}} {{context._id}} ({{organisation.name}})"""
      val emailer = new Emailer(app.instanceOf[MailerClient], new Handlebars(), NotificationEmail(None, None, template))
      val caseSrv = app.instanceOf[CaseSrv]

      "send properly formatted email content" in {
        val case4 = db.tryTransaction(
          implicit graph =>
            for {
              c <- caseSrv
                .get("#4")
                .getOrFail()
              _ <- caseSrv.addTags(c, Set("emailer test"))
            } yield c
        )

        case4 must beSuccessfulTry.which { c4 =>
          val audit = db.roTransaction(
            implicit graph =>
              app
                .instanceOf[AuditSrv]
                .initSteps
                .has(Key("objectId"), P.eq(c4._id))
                .getOrFail()
          )

          audit must beSuccessfulTry

          db.roTransaction { implicit graph =>
            val user = app
              .instanceOf[UserSrv]
              .get("user1")
              .getOrFail()

            user must beSuccessfulTry

            val orga = app
              .instanceOf[OrganisationSrv]
              .get("cert")
              .getOrFail()

            orga must beSuccessfulTry
            emailer.execute(audit.get, Some(c4), orga.get, user.get) must beSuccessfulTry

            emailer.getMessage(audit.get, Some(c4), orga.get, user.get) must beSuccessfulTry.which(message => {
              message.contains(c4._id) must beTrue
              message.contains(c4._model.label) must beTrue
              message.contains(orga.get.name) must beTrue
              message.contains(audit.get.objectId.get) must beTrue
              message.contains(audit.get.requestId) must beTrue
            })
          }
        }
      }

    }
}
