package org.thp.thehive.connector.cortex.controllers.v0

import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.dto.v0.OutputAnalyzer
import org.thp.thehive.dto.v0.OutputObservable
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class JobCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
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
    val jobCtrl: JobCtrl = app.instanceOf[JobCtrl]

    s"[$name] job controller" should {
      "get a job" in {
        val requestSearch = FakeRequest("POST", s"/api/connector/cortex/job/_search?range=0-200&sort=-startDate")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
              {
                 "query":{}
              }
            """.stripMargin))
        val resultSearch = jobCtrl.search(requestSearch)

        status(resultSearch) shouldEqual 200
      }
    }
  }

//  def getObservables(app: AppBuilder) = {
//    val requestSearch = FakeRequest("POST", s"/api/case/artifact/_search?range=all&sort=-startDate&nstats=true")
//      .withHeaders("user" → "user2", "X-Organisation" → "default")
//      .withJsonBody(Json.parse(s"""
//              {
//                "query":{
//                   "_and":[
//                      {
//                         "_and":[
//                            {
//                               "_parent":{
//                                  "_type":"case",
//                                  "_query":{
//                                     "_id":"${resultCase._id}"
//                                  }
//                               }
//                            },
//                            {
//                               "status":"Ok"
//                            }
//                         ]
//                      }
//                   ]
//                }
//             }
//            """.stripMargin))
//    val resultSearch = observableCtrl.search(requestSearch)
//
//    status(resultSearch) shouldEqual 200
//
//    val resSearchObservables = contentAsJson(resultSearch).as[Seq[OutputObservable]]
//  }
}
