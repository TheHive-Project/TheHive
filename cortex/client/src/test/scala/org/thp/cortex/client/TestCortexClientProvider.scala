package org.thp.cortex.client

import java.net.URLEncoder
import java.nio.file.{Path, Paths}

import scala.io.Source
import scala.util.matching.Regex

import play.api.http.{FileMimeTypes, HttpEntity}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results._
import play.api.mvc._
import play.api.test.Helpers._

import akka.stream.scaladsl._
import javax.inject.{Inject, Provider}
import mockws.MockWS
import org.thp.client.NoAuthentication
import org.thp.cortex.dto.v0.{CortexOutputJob, OutputCortexWorker}

class TestCortexClientProvider @Inject()(Action: DefaultActionBuilder, implicit val fileMimeTypes: FileMimeTypes) extends Provider[CortexClient] {
  lazy val analyzers: Seq[OutputCortexWorker]  = readResourceAsJson("/analyzers.json").as[Seq[OutputCortexWorker]]
  lazy val jobs: Seq[CortexOutputJob]          = readResourceAsJson("/jobs.json").as[Seq[CortexOutputJob]]
  lazy val responders: Seq[OutputCortexWorker] = readResourceAsJson("/responders.json").as[Seq[OutputCortexWorker]]
  val apiJobIdWaitReport: Regex                = "^/api/job/([^/]*)/waitreport\\?atMost=\\d+ \\w+$".r
  val apiAnalyzerId: Regex                     = "^/api/analyzer/([^/]*)$".r
  val apiAnalyzerDataType: Regex               = "^/api/analyzer/type/([^/]*)$".r
  val apiAnalyzerIdRun: Regex                  = "^/api/analyzer/([^/]*)/run$".r
  val apiDatastoreId: Regex                    = "^/api/datastore/([^/]*)$".r
  val apiResponderId: Regex                    = "^/api/responder/([^/]*)$".r
  val apiResponderIdRun: Regex                 = "^/api/responder/([^/]*)/run$".r

  val ws = MockWS {
    case (GET, apiJobIdWaitReport(id))        => Action(Results.Ok(Json.toJson(jobs.find(_.id == id).get)))
    case (GET, "/api/analyzers")              => Action(Ok.sendResource("analyzers.json"))
    case (GET, "/api/analyzer")               => Action(Results.Ok.sendResource("analyzers.json"))
    case (GET, apiAnalyzerDataType(dataType)) => Action(Results.Ok(Json.toJson(analyzers.filter(_.dataTypeList.contains(dataType)))))
    case (GET, apiAnalyzerId(id))             => Action(analyzers.find(_.id == id).map(a => Results.Ok(Json.toJson(a))).getOrElse(Results.NotFound))
    case (POST, "/api/analyzer/_search")      => Action(Results.Ok(Json.toJson(analyzers)))
    case (POST, apiAnalyzerIdRun(id))         => Action(Results.Created(Json.toJson(jobs.find(_.workerId == id).get)))
    case (GET, apiDatastoreId(id)) =>
      val filename = URLEncoder.encode(s"$id.test.txt", "utf-8")
      Action(
        Result(
          header = ResponseHeader(200, Map("Content-Disposition" -> s"""attachment; filename="$filename"""", "Content-Transfer-Encoding" -> "binary")),
          body = HttpEntity.Streamed(FileIO.fromPath(fileResource(id)), None, None)
        )
      )
    case (GET, apiResponderId(id))        => Action(Results.Ok(Json.toJson(responders.find(_.id == id).get)))
    case (POST, "/api/responder/_search") => Action(Results.Ok(Json.toJson(responders)))
    case (POST, apiResponderIdRun(id))    => Action(Results.Created(Json.toJson(jobs.find(_.workerId == id).get)))
    case (method, path)                   => Action(Results.NotFound(s"$method $path"))
  }

  def readResourceAsJson(name: String): JsValue = {
    val dataSource = Source.fromFile(getClass.getResource(name).getPath)
    val data       = dataSource.mkString
    dataSource.close()
    Json.parse(data)
  }

  def fileResource(id: String): Path = Paths.get(getClass.getResource(s"/$id.test.txt").getPath)

  def apply[T](block: CortexClient => T): T =
    block(get())

  override def get(): CortexClient =
    new CortexClient("test", "", Seq("*"), Nil, ws, NoAuthentication)
}
