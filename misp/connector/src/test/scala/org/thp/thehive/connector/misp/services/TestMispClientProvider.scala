package org.thp.thehive.connector.misp.services

import scala.io.Source

import play.api.http.FileMimeTypes
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.{DefaultActionBuilder, Results}
import play.api.test.Helpers.{GET, POST}

import javax.inject.{Inject, Provider}
import mockws.MockWS
import org.thp.client.NoAuthentication
import org.thp.misp.client.MispPurpose

class TestMispClientProvider @Inject()(Action: DefaultActionBuilder, implicit val fileMimeTypes: FileMimeTypes) extends Provider[TheHiveMispClient] {
  val baseUrl = "https://misp.test/"

  val ws = MockWS {
    case (GET, "https://misp.test/users/view/me")        => Action(Results.Ok.sendResource("user.json"))
    case (GET, "https://misp.test/organisations/view/1") => Action(Results.Ok.sendResource("organisation.json"))
    case (POST, "https://misp.test/events/index")        => Action(Results.Ok.sendResource("events.json"))
    case (POST, "https://misp.test/attributes/restSearch/json") =>
      Action { request =>
        val predicate = request
          .body
          .asJson
          .fold((_: JsValue) => true) { json =>
            println(s"Filter is $json")
            (attr: JsValue) =>
              (json \ "request" \ "timestamp").asOpt[Long].fold(true)(d => (attr \ "timestamp").asOpt[String].exists(_.toLong > d * 1000)) &&
                (json \ "request" \ "eventid").asOpt[String].fold(true)(e => (attr \ "event_id").asOpt[String].filter(_ == "1").contains(e))
          }
        val attributes = readResourceAsJson("/attributes.json").as[Seq[JsValue]].filter { a =>
          val f = predicate(a)
          println(s"attribute: $a => $f")
          f
        }
        Results.Ok(Json.obj("response" -> Json.obj("Attribute" -> attributes)))
      }
    case (GET, "https://misp.test/attributes/download/3") => Action(Results.Ok.sendResource("user.json"))
    case (GET, "https://misp.test/attributes/download/9") => Action(Results.Ok.sendResource("user.json"))
    case _                                                => Action(Results.NotFound)
  }

  def readResourceAsJson(name: String): JsValue = {
    val dataSource = Source.fromFile(getClass.getResource(name).getPath)
    val data       = dataSource.mkString
    dataSource.close()
    Json.parse(data)
  }

  override def get(): TheHiveMispClient = new TheHiveMispClient(
    name = "test",
    baseUrl = baseUrl,
    auth = NoAuthentication,
    ws = ws,
    maxAge = None,
    excludedOrganisations = Nil,
    excludedTags = Set.empty,
    whitelistTags = Set.empty,
    purpose = MispPurpose.ImportAndExport,
    caseTemplate = None,
    artifactTags = Seq("TEST"),
    exportCaseTags = true,
    includedTheHiveOrganisations = Seq("*"),
    excludedTheHiveOrganisations = Nil
  )
}
