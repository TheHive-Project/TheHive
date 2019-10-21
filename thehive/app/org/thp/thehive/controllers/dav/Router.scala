package org.thp.thehive.controllers.dav

import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.StorageSrv
import org.thp.thehive.services.AttachmentSrv
import play.api.http.{HttpEntity, Status, Writeable}
import play.api.mvc._
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._
import scala.util.Success
import scala.util.matching.Regex
import scala.xml.{Node, NodeSeq}

import play.api.Logger

import org.thp.thehive.controllers.HttpHeaderParameterEncoding

@Singleton
class Router @Inject()(entrypoint: EntryPoint, vfs: VFS, db: Database, attachmentSrv: AttachmentSrv, storageSrv: StorageSrv) extends SimpleRouter {
  lazy val logger = Logger(getClass)

  object PROPFIND {

    def unapply(request: RequestHeader): Option[RequestHeader] =
      Some(request).filter(_.method.equalsIgnoreCase("PROPFIND"))
  }

  override def routes: Routes = {
    case OPTIONS(p"/$path*")                  => options()
    case PROPFIND(p"/$path*")                 => dav(path)
    case GET(p"/cases/$caseId/$attachmentId") => downloadFile(attachmentId)
    case HEAD(p"/$path")                      => head(path)
    case _                                    => debug()
  }

  def debug(): Action[AnyContent] = entrypoint("DAV options") { request =>
    println(s"request ${request.method} ${request.path}")
    request.headers.headers.foreach {
      case (k, v) => println(s"$k: $v")
    }
    println(request.body)
    Success(Results.Ok(""))
  }

  def options(): Action[AnyContent] =
    entrypoint("DAV options")
      .auth { _ =>
        Success(
          Results
            .Ok(EmptyResponse())(EmptyResponse.writeable("httpd/unix-directory"))
            .withHeaders(
              "DAV"           -> "1,2 <http://apache.org/dav/propset/fs/1>",
              "MS-Author-Via" -> "DAV",
              "Allow"         -> "OPTIONS,GET,HEAD,POST,PROPFIND"
            )
        )
      }

  def dav(path: String): Action[AnyContent] =
    entrypoint("dav")
      .extract("xml", FieldsParser.xml.on("xml"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val pathElements = path.split('/').toList
        val resources =
          if (request.headers.get("Depth").contains("1")) vfs.get(pathElements) ::: vfs.list(pathElements)
          else vfs.get(pathElements)
        val props: NodeSeq = request.body("xml") \ "prop" \ "_"
        val response = <D:multistatus xmlns:D="DAV:">
          {
          resources.map { resource =>
            val (knownProps, unknownProps) = props.foldLeft(List.empty[Node] -> List.empty[Node]) {
              case ((k, u), p) => resource.property(p).fold((k, p :: u))(v => (v :: k, u))
            }
            <D:response>
              <D:href>
                {s"${request.uri}${resource.url}"}
              </D:href>
              <D:propstat xmlns:D="DAV:">
                <D:prop>
                  {knownProps}
                </D:prop>
                <D:status>HTTP/1.1 200 OK</D:status>
              </D:propstat>
              <D:propstat xmlns:D="DAV:">
                <D:prop>
                  {unknownProps}
                </D:prop>
                <D:status>HTTP/1.1 404 Not Found</D:status>
              </D:propstat>
            </D:response>
          }
        }
        </D:multistatus>
        Success(Results.MultiStatus(response))
      }

  val rangeExtract: Regex = "^bytes=(\\d+)-(\\d+)$".r

  def downloadFile(id: String): Action[AnyContent] =
    entrypoint("download attachment")
      .authRoTransaction(db) { request => implicit graph =>
        attachmentSrv.getOrFail(id).map { attachment =>
          val range = request.headers.get("Range")
          range match {
            case Some(rangeExtract(from, to)) =>
              logger.debug(s"Download attachment $id with range $from-$to")
              val is = storageSrv.loadBinary(id)
              is.skip(from.toLong)
              val source = StreamConverters.fromInputStream(() => is)
              Result(
                header = ResponseHeader(Status.PARTIAL_CONTENT, Map("Content-Range" -> s"bytes $from-$to/${attachment.size}")),
                body = HttpEntity.Streamed(source.take(to.toLong - from.toLong), Some(attachment.size), Some(attachment.contentType))
              )
            case _ =>
              logger.debug(s"Download attachment $id")
              Result(
                header = ResponseHeader(Status.OK, Map("Cache-Control" -> "immutable")),
                body = HttpEntity.Streamed(StreamConverters.fromInputStream(() => storageSrv.loadBinary(id)), None, None)
              )
          }
        }
      }

  def head(path: String): Action[AnyContent] =
    entrypoint("head")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val pathElements = path.split('/').toList
        vfs
          .get(pathElements)
          .headOption
          .map {
            case AttachmentResource(a, _) =>
              Success(
                Results
                  .Ok(EmptyResponse())(EmptyResponse.writeable(a.contentType))
                  .withHeaders("Accept-Ranges" -> "Bytes", "Content-Length" -> a.size.toString)
              )
            case _ => Success(Results.Ok(EmptyResponse())(EmptyResponse.writeable("httpd/unix-directory")))
          }
          .getOrElse(Success(Results.NotFound))
      }
}

case class EmptyResponse()

object EmptyResponse {
  def writeable(contentType: String): Writeable[EmptyResponse] = new Writeable[EmptyResponse](_ => ByteString.empty, Some(contentType))
}
