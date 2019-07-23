package org.thp.thehive.models

import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, AuthSrv, Permission, UserSrv}
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.controllers.v0.TheHiveQueryExecutor
import org.thp.thehive.dto.v0.{OutputObservable, OutputTask}
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class EntityHelperTest extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bindInstance[AuthSrv](mock[AuthSrv])
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val entityHelper         = app.instanceOf[EntityHelper]
    val db                   = app.instanceOf[Database]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]
    implicit val authContext: AuthContext = new AuthContext {
      override def userId: String               = "user1"
      override def userName: String             = "user1"
      override def organisation: String         = "cert"
      override def requestId: String            = ""
      override def permissions: Set[Permission] = Permissions.all
    }

    def tasksList: Seq[OutputTask] = {
      val requestList = FakeRequest("GET", "/api/case/task/_search").withHeaders("user" -> "user1")
      val resultList  = theHiveQueryExecutor.task.search(requestList)

      status(resultList) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultList)}")

      contentAsJson(resultList).as[Seq[OutputTask]]
    }

    s"[$name] entity helper" should {

      "return appropriate entity threat levels" in db.transaction { implicit graph =>
        val failure = entityHelper.threatLevels("", "")

        failure must beFailedTry

        val t1 = tasksList.find(_.title == "case 1 task 1")

        t1 must beSome

        val task1       = t1.get
        val successTask = entityHelper.threatLevels("task", task1.id)
        val failureTask = entityHelper.threatLevels("task", task1.id)(graph, dummyUserSrv.authContext)

        successTask must beSuccessfulTry
        failureTask must beFailedTry

        val (tlpTask, papTask) = successTask.get

        tlpTask shouldEqual 2
        papTask shouldEqual 2
      }

      "return proper observable threat levels" in db.transaction { implicit graph =>
        val requestSearch = FakeRequest("POST", s"/api/case/artifact/_search?range=all&sort=-startDate&nstats=true")
          .withHeaders("user" -> "user1", "X-Organisation" -> "cert")
          .withJsonBody(Json.parse(s"""
              {
                "query":{
                   "_and":[
                      {
                         "_and":[
                            {
                               "tlp":3
                            }
                         ]
                      }
                   ]
                }
             }
            """.stripMargin))
        val resultSearch = theHiveQueryExecutor.observable.search(requestSearch)

        status(resultSearch) should equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultSearch)}")

        val resSearchObservables = contentAsJson(resultSearch).as[Seq[OutputObservable]]
        val o1                   = resSearchObservables.headOption

        o1 must beSome

        val observable1 = o1.get
        val successObs  = entityHelper.threatLevels("observable", observable1._id)

        successObs must beSuccessfulTry

        val (tlp, pap) = successObs.get

        tlp shouldEqual 3
        pap shouldEqual 2
      }

    }
  }
}
