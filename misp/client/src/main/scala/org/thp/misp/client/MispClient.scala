package org.thp.misp.client

import java.util.Date

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.{WSClient, WSRequest}

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.thp.client.{ApplicationError, Authentication, CustomWSAPI}
import org.thp.misp.dto.{Attribute, Event}

object MispPurpose extends Enumeration {
  val ImportOnly, ExportOnly, ImportAndExport = Value
}

class MispClient(
    val name: String,
    baseUrl: String,
    auth: Authentication,
    ws: WSClient,
    maxAge: Option[Duration],
    maxAttributes: Option[Int],
    maxSize: Option[Long],
    excludedOrganisations: Seq[String],
    excludedTags: Set[String],
    whitelistTags: Set[String]
) {
  lazy val logger = Logger(getClass)

  private def configuredProxy: Option[String] = ws match {
    case c: CustomWSAPI => c.proxy.map(p => s"http://${p.host}:${p.port}")
    case _              => None
  }
  logger.info(s"""Add MISP connection $name
                 |  url:              $baseUrl
                 |  proxy:            ${configuredProxy.getOrElse("<not set>")}
                 |  filters:
                 |    max age:        ${maxAge.fold("<not set>")(_.toCoarsest.toString)}
                 |    max attributes: ${maxAttributes.getOrElse("<not set>")}
                 |    max size:       ${maxSize.getOrElse("<not set>")}
                 |    excluded orgs:  ${excludedOrganisations.mkString}
                 |    excluded tags:  ${excludedTags.mkString}
                 |    whitelist tags: ${whitelistTags.mkString}
                 |""".stripMargin)

  private def request(url: String): WSRequest =
    auth(ws.url(s"$baseUrl/$url"))
      .withHttpHeaders("Accept" -> "application/json")

  private def get(url: String)(implicit ec: ExecutionContext): Future[JsValue] = // TODO add size restriction
    request(url).get().transform {
      case Success(r) if r.status == Status.OK => Success(r.json)
      case Success(r)                          => Try(r.json)
      case Failure(t)                          => throw t
    }

  private def post(url: String, body: JsValue)(implicit ec: ExecutionContext): Future[JsValue] = // TODO add size restriction
    request(url).post(body).transform {
      case Success(r) if r.status == Status.OK => Success(r.json)
      case Success(r)                          => Try(r.json)
      case Failure(t)                          => throw t
    }

//  private def getStream(url: String)(implicit ec: ExecutionContext): Future[Source[ByteString, Any]] =
//    request(url).stream().transform {
//      case Success(r) if r.status == Status.OK => Success(r.bodyAsSource)
//      case Success(r)                               => Try(r.bodyAsSource)
//      case Failure(t)                               => throw t
//    }

  def getEvent(eventId: String)(implicit ec: ExecutionContext): Future[Event] = {
    logger.debug(s"Get MISP event $eventId")
    require(!eventId.isEmpty)
    get(s"events/$eventId")
      .map(e => (e \ "Event").as[Event])
  }

  def searchEvents(publishDate: Option[Date] = None)(implicit ec: ExecutionContext): Future[Seq[Event]] = {
    val query = publishDate.fold(JsObject.empty)(d => Json.obj("searchpublish_timestamp" -> (d.getTime / 1000)))
    logger.debug(s"Search MISP events ")
    post("events/index", query)
      .map(_.as[Seq[Event]].filterNot(isExcluded))
  }

  def isExcluded(event: Event): Boolean = {
    val eventTags = event.tags.map(_.name).toSet
    if (whitelistTags.nonEmpty && (whitelistTags & eventTags).isEmpty) {
      logger.debug(s"event ${event.id} is ignored because it doesn't contain any of whitelist tags (${whitelistTags.mkString(",")})")
      true
    } else if (excludedOrganisations.contains(event.orgc)) {
      logger.debug(s"event ${event.id} is ignored because its organisation (${event.orgc}) is excluded")
      true
    } else {
      val t = excludedTags.intersect(eventTags)
      if ((excludedTags & eventTags).nonEmpty) {
        logger.debug(s"event ${event.id} is ignored because one of its tags (${t.mkString(",")}) is excluded")
        true
      } else false
    }
  }

  def searchAttributes(eventId: String, publishDate: Option[Date])(implicit ec: ExecutionContext): Future[Seq[Attribute]] =
    post("attributes/restSearch/json", Json.obj("request" -> Json.obj("timestamp" -> publishDate.fold(0L)(_.getTime / 1000), "eventid" -> eventId)))
    // add ("deleted" → 1) to see also deleted attributes
    // add ("deleted" → "only") to see only deleted attributes
      .map { jsBody =>
        (jsBody \ "response" \\ "Attribute")
          .flatMap(_.as[Seq[Attribute]])
//            .filter(_.date after refDate)
      }

  private val fileNameExtractor = """attachment; filename="(.*)"""".r

  def downloadAttachment(attachmentId: String)(implicit ec: ExecutionContext): Future[(String, String, Source[ByteString, _])] =
    request(s"attributes/download/$attachmentId").stream().transform {
      case Success(r) if r.status == Status.OK =>
        val filename = r
          .headers
          .get("Content-Disposition")
          .flatMap(_.collectFirst { case fileNameExtractor(name) => name })
          .getOrElse("noname")
        val contentType = r.headers.getOrElse("Content-Type", Seq("application/octet-stream")).head
        Success((filename, contentType, r.bodyAsSource))
      case Success(r) => Failure(ApplicationError(r))
      case Failure(t) => throw t
    }
}
