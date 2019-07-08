package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.http.HttpEntity
import play.api.mvc._

import akka.stream.scaladsl.StreamConverters
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.services.StorageSrv

@Singleton
class AttachmentCtrl @Inject()(entryPoint: EntryPoint, storageSrv: StorageSrv) {
  val forbiddenChar: Seq[Char] = Seq('/', '\n', '\r', '\t', '\u0000', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':', ';')

  def download(id: String, name: Option[String]): Action[AnyContent] =
    entryPoint("download attachment")
      .auth { _ =>
        if (!name.getOrElse("").intersect(forbiddenChar).isEmpty)
          Success(Results.BadRequest("File name is invalid"))
        else
          Success(
            Result(
              header = ResponseHeader(
                200,
                Map("Content-Disposition" -> s"""attachment; filename="${name.getOrElse(id)}"""", "Content-Transfer-Encoding" -> "binary")
              ),
              body = HttpEntity.Streamed(StreamConverters.fromInputStream(() => storageSrv.loadBinary(id)), None, None)
            )
          )
      }

  def downloadZip(hash: String, name: Option[String]): Action[AnyContent] = ???
}
