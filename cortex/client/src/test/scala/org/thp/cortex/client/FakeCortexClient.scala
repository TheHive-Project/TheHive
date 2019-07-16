package org.thp.cortex.client

import java.net.URLEncoder
import java.nio.file.{Path, Paths}

import akka.stream.scaladsl._
import org.thp.cortex.dto.v0.{CortexOutputJob, OutputCortexAnalyzer, OutputCortexResponder}
import play.api.http.{FileMimeTypes, HttpEntity}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.routing.sird._
import play.core.server.Server

import scala.io.Source

trait FakeCortexClient {

  lazy val analyzers: Seq[OutputCortexAnalyzer] = readResourceAsJson("/analyzers.json").as[Seq[OutputCortexAnalyzer]]
  lazy val jobs: Seq[CortexOutputJob]           = readResourceAsJson("/jobs.json").as[Seq[CortexOutputJob]]
  lazy val responders: Seq[OutputCortexResponder] = readResourceAsJson("/responders.json").as[Seq[OutputCortexResponder]]

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

      implicit val mimeTypes: FileMimeTypes = fileMimeTypes

      {
        case GET(p"/api/job/$id/waitreport") => Action(Results.Ok(Json.toJson(jobs.find(_.id == id).get)))
        case GET(p"/api/analyzers")          => Action(_ => Ok.sendResource("analyzers.json"))
        case GET(p"/api/analyzer")           => Action(Results.Ok.sendResource("analyzers.json"))
        case GET(p"/api/analyzer/$id")       => Action(Results.Ok(Json.toJson(analyzers.find(_.id == id).get)))
        case POST(p"/api/analyzer/_search")  => Action(Results.Ok(Json.toJson(analyzers)))
        case POST(p"/api/analyzer/$id/run")  => Action(Results.Created(Json.toJson(jobs.find(_.workerId == id).get)))
        case GET(p"/api/datastore/$id") =>
          Action(
            Result(
              header = ResponseHeader(200, Map("Content-Disposition"                -> s"""attachment; filename="${URLEncoder
                .encode(s"$id.test.txt", "utf-8")}"""", "Content-Transfer-Encoding" -> "binary")),
              body = HttpEntity.Streamed(FileIO.fromPath(fileResource(id)), None, None)
            )
          )
        case GET(p"/api/responder/$id")       => Action(Results.Ok(Json.toJson(responders.find(_.id == id).get)))
        case POST(p"/api/responder/_search")  => Action(Results.Ok(Json.toJson(responders)))
      }
    } { port =>
      block(new CortexClient("test", s"http://127.0.0.1:$port/"))
    }

  def fileResource(id: String): Path = Paths.get(getClass.getResource(s"/$id.test.txt").getPath)
}
