package org.thp.cortex.client

import akka.actor.ActorSystem
import play.api.{BuiltInComponentsFromContext, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.routing.Router
import play.api.routing.sird._
import play.api.test._
import play.core.server.Server
import play.filters.HttpFiltersComponents

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait FakeCortexClient {

  def withCortexClient[T](block: CortexClient => T): T =
    Server.withApplicationFromContext() { context =>
      new BuiltInComponentsFromContext(
        context
      ) with HttpFiltersComponents {
        override def router: Router = Router.from {
          case GET(p"/api/analyzers") =>
            Action { _ =>
              Results.Ok.sendResource("analyzers.json")(fileMimeTypes)
            }
        }

        override lazy val configuration: Configuration = context.initialConfiguration ++ Configuration("akka.remote.netty.tcp.port" -> 3333)
      }.application
    } { implicit port =>
      WsTestClient.withClient { _ =>
        implicit lazy val auth: Authentication = PasswordAuthentication("test", "test")
        implicit lazy val system: ActorSystem  = GuiceApplicationBuilder().injector.instanceOf[ActorSystem]
        implicit lazy val ws: CustomWSAPI      = GuiceApplicationBuilder().injector.instanceOf[CustomWSAPI]

        block(new CortexClient("test", "", 1.second, 3))
      }
    }
}
