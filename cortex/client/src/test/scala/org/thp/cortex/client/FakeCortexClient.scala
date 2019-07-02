package org.thp.cortex.client

import org.thp.cortex.dto.v0.{CortexOutputJob, OutputCortexAnalyzer}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.routing.sird._
import play.api.test._
import play.core.server.Server

import scala.io.Source

trait FakeCortexClient {

  lazy val analyzers: Seq[OutputCortexAnalyzer] = readResourceAsJson("/analyzers.json").as[Seq[OutputCortexAnalyzer]]
  lazy val jobs: Seq[CortexOutputJob]           = readResourceAsJson("/jobs.json").as[Seq[CortexOutputJob]]

  def readResourceAsJson(name: String): JsValue = {
    val dataSource = Source.fromFile(getClass.getResource(name).getPath)
    val data       = dataSource.mkString
    dataSource.close()
    Json.parse(data)
  }

  def withCortexClient[T](block: CortexClient => T)(implicit auth: Authentication, ws: CustomWSAPI): T =
    Server.withRouterFromComponents() { components =>
      import Results._
      import components.{fileMimeTypes, defaultActionBuilder => Action}
      {
        case GET(p"/api/analyzers")         => Action(_ => Ok.sendResource("analyzers.json")(fileMimeTypes))
        case GET(p"/api/analyzer")          => Action(Results.Ok.sendResource("analyzers.json")(fileMimeTypes))
        case GET(p"/api/analyzer/$id")      => Action(Results.Ok(Json.toJson(analyzers.find(_.id == id).get)))
        case POST(p"/api/analyzer/_search") => Action(Results.Ok(Json.toJson(analyzers)))
        case POST(p"/api/analyzer/$id/run") => Action(Results.Created(Json.toJson(jobs.find(_.workerId == id).get)))
      }
    } { implicit port =>
      WsTestClient.withClient { _ =>
        block(new CortexClient("test", s"http://127.0.0.1:$port/"))
      }
    }
}
