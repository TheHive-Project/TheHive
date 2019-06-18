package org.thp.cortex.client

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import org.specs2.mock.Mockito
import org.specs2.specification.core.Fragments
import org.thp.cortex.dto.client.OutputCortexAnalyzer
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{Action, Results}
import play.api.routing.Router
import play.api.routing.sird._
import play.api.test.{PlaySpecification, WithServer}
import play.api.{Configuration, Logger}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class CortexClientTest extends PlaySpecification with Mockito {

  sequential

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

  Fragments.foreach(Seq(janusGraphConfig)) { dbConfig ⇒
    s"[${dbConfig.get[String]("db.provider")}] CortexClient" should {
      val outputAnalyzer = OutputCortexAnalyzer(
        id = "test",
        name = "test",
        version = "test",
        description = "test",
        dataTypeList = List("test")
      )
      lazy val app = GuiceApplicationBuilder()
        .router(Router.from {
          case play.api.routing.sird.GET(p"/api/analyzer") ⇒
            Action {
              Results.Ok(Json.toJson(Seq(outputAnalyzer)))
            }
        })
        .build()

      implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
      implicit lazy val ws: CustomWSAPI      = app.injector.instanceOf[CustomWSAPI]
      implicit lazy val auth: Authentication = PasswordAuthentication("test", "test")
      implicit lazy val system: ActorSystem  = app.actorSystem
      implicit lazy val mat: Materializer    = app.materializer
      val mockLogger                         = mock[Logger]

      lazy val client = new CortexClient("test", s"http://127.0.0.1:3333", 3.seconds, 3) {
        override lazy val logger: Logger = mockLogger
      }

      "handle get analyzers properly" in new WithServer(app, port = 3333) {
        val analyzers: Seq[OutputCortexAnalyzer] = await(client.listAnalyser)

        analyzers.length shouldEqual 1
        analyzers shouldEqual Seq(outputAnalyzer)
      }
    }
  }
}
