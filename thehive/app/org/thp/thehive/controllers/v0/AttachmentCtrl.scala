package org.thp.thehive.controllers.v0

import java.nio.file.Files

import scala.util.{Failure, Success, Try}

import play.api.http.HttpEntity
import play.api.mvc._

import akka.stream.scaladsl.{FileIO, StreamConverters}
import javax.inject.{Inject, Singleton}
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.{CompressionLevel, EncryptionMethod}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.services.StorageSrv
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.controllers.HttpHeaderParameterEncoding

@Singleton
class AttachmentCtrl @Inject()(entryPoint: EntryPoint, appConfig: ApplicationConfig, storageSrv: StorageSrv) {
  val forbiddenChar: Seq[Char] = Seq('/', '\n', '\r', '\t', '\u0000', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':', ';')

  val passwordConfig: ConfigItem[String, String] = appConfig.item[String]("datastore.attachment.password", "Password used to protect attachment ZIP")

  def download(id: String, name: Option[String]): Action[AnyContent] =
    entryPoint("download attachment")
      .auth { _ =>
        if (!name.getOrElse("").intersect(forbiddenChar).isEmpty)
          Success(Results.BadRequest("File name is invalid"))
        else if (storageSrv.exists(id))
          Success(
            Result(
              header = ResponseHeader(
                200,
                Map(
                  "Content-Disposition"       -> s"""attachment; ${HttpHeaderParameterEncoding.encode("filename", name.getOrElse(id))}""",
                  "Content-Transfer-Encoding" -> "binary"
                )
              ),
              body = HttpEntity.Streamed(StreamConverters.fromInputStream(() => storageSrv.loadBinary(id)), None, None)
            )
          )
        else Failure(NotFoundError(s"Attachment $id not found"))
      }

  def downloadZip(hash: String, name: Option[String]): Action[AnyContent] =
    entryPoint("download attachment")
      .auth { _ =>
        if (!name.getOrElse("").intersect(forbiddenChar).isEmpty)
          Success(Results.BadRequest("File name is invalid"))
        else if (storageSrv.exists(hash))
          Try {
            val f = Files.createTempFile("downloadzip-", hash)
            Files.delete(f)
            val zipFile   = new ZipFile(f.toFile, password.toCharArray)
            val zipParams = new ZipParameters
            zipParams.setCompressionLevel(CompressionLevel.FASTEST)
            zipParams.setEncryptFiles(true)
            zipParams.setEncryptionMethod(EncryptionMethod.ZIP_STANDARD)
            zipParams.setFileNameInZip(name.getOrElse(hash))
//      zipParams.setSourceExternalStream(true)
            zipFile.addStream(storageSrv.loadBinary(hash), zipParams)

            Result(
              header = ResponseHeader(
                200,
                Map(
                  "Content-Disposition"       -> s"""attachment; filename="${name.getOrElse(hash)}.zip"""",
                  "Content-Type"              -> "application/zip",
                  "Content-Transfer-Encoding" -> "binary",
                  "Content-Length"            -> Files.size(f).toString
                )
              ),
              body = HttpEntity.Streamed(FileIO.fromPath(f), Some(Files.size(f)), Some("application/zip"))
            ) // FIXME remove temporary file (but when ?)
          } else Failure(NotFoundError(s"Attachment $hash not found"))
      }

  def password: String = passwordConfig.get
}
