package org.thp.misp.client

import akka.NotUsed
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.{JsonFraming, Source}
import akka.util.ByteString
import org.thp.client.{ApplicationError, Authentication, ProxyWS}
import org.thp.misp.dto._
import org.thp.scalligraph.InternalError
import org.thp.scalligraph.utils.FunctionalCondition._
import play.api.Logger
import play.api.http.Status
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest}

import java.util.Date
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object MispPurpose extends Enumeration {
  val ImportOnly, ExportOnly, ImportAndExport = Value
}

class MispClient(
    val name: String,
    val baseUrl: String,
    auth: Authentication,
    ws: WSClient,
    maxAge: Option[Duration],
    excludedOrganisations: Seq[String],
    whitelistOrganisations: Seq[String],
    excludedTags: Set[String],
    whitelistTags: Set[String]
) {
  lazy val logger: Logger                              = Logger(getClass)
  val strippedUrl: String                              = baseUrl.replaceFirst("/*$", "")
  private var _currentOrganisationName: Future[String] = getCurrentOrganisationName(ExecutionContext.global)

  def currentOrganisationName: Try[String] =
    _currentOrganisationName.value match {
      case Some(s: Success[_]) => s
      case None                => Try(Await.result(_currentOrganisationName, 1.second)) // Failure(InternalError(s"MISP server $name is not yet inaccessible"))
      case Some(Failure(t)) =>
        _currentOrganisationName = getCurrentOrganisationName(ExecutionContext.global)
        Failure(InternalError(s"MISP server $name is inaccessible", t))
    }

  private def configuredProxy: Option[String] =
    ws match {
      case c: ProxyWS => c.proxy.map(p => s"http://${p.host}:${p.port}")
      case _          => None
    }
  logger.info(s"""Add MISP connection $name
                 |  url:              $baseUrl
                 |  proxy:            ${configuredProxy.getOrElse("<not set>")}
                 |  filters:
                 |    max age:        ${maxAge.fold("<not set>")(_.toCoarsest.toString)}
                 |    excluded orgs:  ${excludedOrganisations.mkString}
                 |    excluded tags:  ${excludedTags.mkString}
                 |    whitelist tags: ${whitelistTags.mkString}
                 |""".stripMargin)

  private def request(url: String): WSRequest =
    auth(ws.url(s"$strippedUrl/$url").withHttpHeaders("Accept" -> "application/json"))

  private def get(url: String)(implicit ec: ExecutionContext): Future[JsValue] = {
    logger.trace(s"MISP request: GET $url")
    request(url).get().transform {
      case Success(r) if r.status == Status.OK =>
        logger.trace(s"MISP response: ${r.status} ${r.statusText}\n${r.body}")
        Success(r.json)
      case Success(r) =>
        logger.trace(s"MISP response: ${r.status} ${r.statusText}\n${r.body}")
        Try(r.json)
      case Failure(t) =>
        logger.trace(s"MISP error: $t")
        throw t
    }
  }

  private def post(url: String, body: JsValue)(implicit ec: ExecutionContext): Future[JsValue] = {
    logger.trace(s"MISP request: POST $url\n$body")
    request(url).post(body).transform {
      case Success(r) if r.status == Status.OK =>
        logger.trace(s"MISP response: ${r.status} ${r.statusText}\n${r.body}")
        Success(r.json)
      case Success(r) =>
        logger.trace(s"MISP response: ${r.status} ${r.statusText}\n${r.body}")
        Try(r.json)
      case Failure(t) =>
        logger.trace(s"MISP error: $t")
        throw t
    }
  }

  private def post(url: String, body: Source[ByteString, _])(implicit ec: ExecutionContext): Future[JsValue] = {
    logger.trace(s"MISP request: POST $url (stream body)")
    request(url).post(body).transform {
      case Success(r) if r.status == Status.OK =>
        logger.trace(s"MISP response: ${r.status} ${r.statusText}\n${r.body}")
        Success(r.json)
      case Success(r) =>
        logger.trace(s"MISP response: ${r.status} ${r.statusText}\n${r.body}")
        Try(r.json)
      case Failure(t) =>
        logger.trace(s"MISP error: $t")
        throw t
    }
  }

  //
//  private def getStream(url: String)(implicit ec: ExecutionContext): Future[Source[ByteString, Any]] =
//    request(url).withMethod("GET").stream().transform {
//      case Success(r) if r.status == Status.OK => Success(r.bodyAsSource)
//      case Success(r)                          => Try(r.bodyAsSource)
//      case Failure(t)                          => throw t
//    }

  private def postStream(url: String, body: JsValue)(implicit ec: ExecutionContext): Future[Source[ByteString, Any]] = {
    logger.trace(s"MISP request: POST $url\n$body")
    request(url).withMethod("POST").withBody(body).stream().transform {
      case Success(r) if r.status == Status.OK =>
        logger.trace(s"MISP response: ${r.status} ${r.statusText} (stream body)")
        Success(r.bodyAsSource)
      case Success(r) =>
        logger.trace(s"MISP response: ${r.status} ${r.statusText} (stream body)")
        Try(r.bodyAsSource)
      case Failure(t) =>
        logger.trace(s"MISP error: $t")
        throw t
    }
  }

  def getCurrentUser(implicit ec: ExecutionContext): Future[User] = {
    logger.debug("Get current user")
    get("users/view/me")
      .map(u => (u \ "User").as[User])
  }

  def getOrganisation(organisationId: String)(implicit ec: ExecutionContext): Future[Organisation] = {
    logger.debug(s"Get organisation $organisationId")
    get(s"organisations/view/$organisationId")
      .map(o => (o \ "Organisation").as[Organisation])
  }

  def getCurrentOrganisationName(implicit ec: ExecutionContext): Future[String] =
    getCurrentUser.flatMap(user => getOrganisation(user.orgId)).map(_.name)

  def getEvent(eventId: String)(implicit ec: ExecutionContext): Future[Event] = {
    logger.debug(s"Get MISP event $eventId")
    require(eventId.nonEmpty)
    get(s"events/$eventId")
      .map(e => (e \ "Event").as[Event])
  }

  def getVersion(implicit ec: ExecutionContext): Future[String] =
    get("servers/getVersion")
      .map(s => (s \ "version").as[String])

  def getStatus(implicit ec: ExecutionContext): Future[JsObject] =
    getVersion
      .map(version => Json.obj("name" -> name, "version" -> version, "status" -> "OK", "url" -> baseUrl))
      .recover { case _ => Json.obj("name" -> name, "version" -> "", "status" -> "ERROR", "url" -> baseUrl) }

  def searchEvents(publishDate: Option[Date] = None)(implicit ec: ExecutionContext): Source[Event, NotUsed] = {
    val fromDate = (maxAge.map(a => System.currentTimeMillis() - a.toMillis).toSeq ++ publishDate.map(_.getTime))
      .sorted(Ordering[Long].reverse)
      .headOption
      .map(d => "searchpublish_timestamp" -> JsNumber((d / 1000) + 1))
    val tagFilter          = (whitelistTags ++ excludedTags.map("!" + _)).map(JsString.apply)
    val organisationFilter = (whitelistOrganisations ++ excludedOrganisations.map("!" + _)).map(JsString.apply)
    val query = JsObject
      .empty
      .merge(fromDate)(_ + _)
      .when(tagFilter.nonEmpty)(_ + ("searchtag" -> JsArray(tagFilter.toSeq)))
      .when(organisationFilter.nonEmpty)(_ + ("searchorg" -> JsArray(organisationFilter)))
    logger.debug("Search MISP events")
    Source
      .futureSource(postStream("events/index", query))
      .via(JsonFraming.objectScanner(Int.MaxValue))
      .mapConcat { data =>
        val maybeEvent = Try(Json.parse(data.toArray[Byte]).as[Event])
        maybeEvent.fold(error => { logger.warn(s"Event has invalid format: ${data.decodeString("UTF-8")}", error); Nil }, List(_))
      }
      .filterNot(isExcluded)
      .mapMaterializedValue(_ => NotUsed)
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

  def searchAttributes(eventId: String, publishDate: Option[Date])(implicit ec: ExecutionContext): Source[Attribute, NotUsed] = {
    logger.debug(s"Search MISP attributes for event #$eventId ${publishDate.fold("")("from " + _)}")
    Source
      .futureSource(
        postStream(
          "attributes/restSearch/json",
          Json.obj("request" -> Json.obj("timestamp" -> publishDate.fold(0L)(_.getTime / 1000), "eventid" -> eventId))
        )
      )
      // add ("deleted" → 1) to see also deleted attributes
      // add ("deleted" → "only") to see only deleted attributes
      //      .via(JsonFraming.objectScanner(Int.MaxValue))
      .via(JsonReader.select("$.response.Attribute[*]"))
      .mapConcat { data =>
        val maybeAttribute = Try(Json.parse(data.toArray[Byte]).as[Attribute])
        maybeAttribute.fold(error => { logger.warn(s"Attribute has invalid format: ${data.decodeString("UTF-8")}", error); Nil }, List(_))
      }
      .mapAsyncUnordered(2) {
        case attribute @ Attribute(id, "malware-sample" | "attachment", _, _, _, _, _, _, _, None, _, _, _, _) =>
          // TODO need to unzip malware samples ?
          downloadAttachment(id).map {
            case (filename, contentType, src) => attribute.copy(data = Some((filename, contentType, src)))
          }
        case attribute => Future.successful(attribute)
      }
      .mapMaterializedValue(_ => NotUsed)
  }

  //            .filter(_.date after refDate)

  private val fileNameExtractor = """attachment; filename="(.*)"""".r

  def downloadAttachment(attachmentId: String)(implicit ec: ExecutionContext): Future[(String, String, Source[ByteString, _])] =
    request(s"attributes/download/$attachmentId").stream().transform {
      case Success(r) if r.status == Status.OK =>
        val filename = r
          .headers
          .get("Content-Disposition")
          .flatMap(_.collectFirst { case fileNameExtractor(name) => name })
          .getOrElse("noname")
        val contentType = r.headers.get("Content-Type").flatMap(_.headOption).getOrElse("application/octet-stream")
        Success((filename, contentType, r.bodyAsSource))
      case Success(r) => Failure(ApplicationError(r))
      case Failure(t) => throw t
    }

  def uploadAttachment(eventId: String, comment: String, filename: String, data: Source[ByteString, _])(implicit
      ec: ExecutionContext
  ): Future[JsValue] = {
    val stream = data
      .via(Base64Flow.encode())
      .intersperse(
        ByteString(
          s"""{"request":{"category":"Payload delivery","type":"malware-sample","comment":${JsString(
            comment
          ).toString},"files":[{"filename":${JsString(
            filename
          ).toString},"data":""""
        ),
        ByteString.empty,
        ByteString(""""}]}}""")
      )
    post(s"events/upload_sample/$eventId", stream)
  }

  def createEvent(
      info: String,
      date: Date,
      threatLevel: Int,
      published: Boolean,
      analysis: Int,
      distribution: Int,
      attributes: Seq[Attribute],
      tags: Seq[Tag],
      extendsEvent: Option[String] = None
  )(implicit ec: ExecutionContext): Future[String] = {
    logger.debug(s"Create MISP event $info, with ${attributes.size} attributes")

    val (stringAttributes, fileAttribtutes) = attributes.partition(_.data.isEmpty)
    val event = Json.obj(
      "Event" -> Json.obj(
        "date"            -> Event.simpleDateFormat.format(date),
        "threat_level_id" -> threatLevel.toString,
        "info"            -> info,
        "published"       -> published,
        "analysis"        -> analysis.toString,
        "distribution"    -> distribution,
        "Attribute"       -> stringAttributes,
        "Tag"             -> tags,
        "extends_uuid"    -> extendsEvent
      )
    )
    post("events", event)
      .map { e =>
        (e \ "Event" \ "id").as[String]
      }
      .flatMap { eventId =>
        Future
          .traverse(fileAttribtutes) { attr =>
            uploadAttachment(eventId, attr.comment.getOrElse(attr.value), attr.value, attr.data.get._3)
          }
          .map(_ => eventId)
      }
  }
}
