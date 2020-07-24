package org.thp.thehive.controllers.v0

import java.nio.file.Files

import akka.stream.scaladsl.FileIO
import javax.inject.{Inject, Named, Singleton}
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.{CompressionLevel, EncryptionMethod}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.HttpHeaderParameterEncoding
import org.thp.thehive.services.AttachmentSrv
import play.api.http.HttpEntity
import play.api.mvc._

import scala.util.{Failure, Success, Try}

@Singleton
class AttachmentCtrl @Inject() (
    entrypoint: Entrypoint,
    appConfig: ApplicationConfig,
    attachmentSrv: AttachmentSrv,
    @Named("with-thehive-schema") db: Database
) {
  val forbiddenChar: Seq[Char] = Seq('/', '\n', '\r', '\t', '\u0000', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':', ';')

  val passwordConfig: ConfigItem[String, String] = appConfig.item[String]("datastore.attachment.password", "Password used to protect attachment ZIP")

  def download(id: String, name: Option[String]): Action[AnyContent] =
    entrypoint("download attachment")
      .authRoTransaction(db) { implicit authContext => implicit graph =>
        if (name.getOrElse("").intersect(forbiddenChar).nonEmpty)
          Success(Results.BadRequest("File name is invalid"))
        else
          attachmentSrv
            .get(id)
            .visible
            .getOrFail("Attachment")
            .filter(attachmentSrv.exists)
            .map { attachment =>
              Result(
                header = ResponseHeader(
                  200,
                  Map(
                    "Content-Disposition"       -> s"""attachment; ${HttpHeaderParameterEncoding.encode("filename", name.getOrElse(id))}""",
                    "Content-Transfer-Encoding" -> "binary"
                  )
                ),
                body = HttpEntity.Streamed(attachmentSrv.source(attachment), None, None)
              )
            }
            .recoverWith {
              case _: NoSuchElementException => Failure(NotFoundError(s"Attachment $id not found"))
            }
      }

  def downloadZip(id: String, name: Option[String]): Action[AnyContent] =
    entrypoint("download attachment")
      .authRoTransaction(db) { implicit authContext => implicit graph =>
        if (name.getOrElse("").intersect(forbiddenChar).nonEmpty)
          Success(Results.BadRequest("File name is invalid"))
        else
          attachmentSrv
            .get(id)
            .visible
            .getOrFail("Attachment")
            .filter(attachmentSrv.exists)
            .flatMap { attachment =>
              Try {
                val f = Files.createTempFile("downloadzip-", id)
                Files.delete(f)
                val zipFile   = new ZipFile(f.toFile, password.toCharArray)
                val zipParams = new ZipParameters
                zipParams.setCompressionLevel(CompressionLevel.FASTEST)
                zipParams.setEncryptFiles(true)
                zipParams.setEncryptionMethod(EncryptionMethod.ZIP_STANDARD)
                zipParams.setFileNameInZip(name.getOrElse(id))
                //      zipParams.setSourceExternalStream(true)
                zipFile.addStream(attachmentSrv.stream(attachment), zipParams)

                Result(
                  header = ResponseHeader(
                    200,
                    Map(
                      "Content-Disposition"       -> s"""attachment; filename="${name.getOrElse(id)}.zip"""",
                      "Content-Type"              -> "application/zip",
                      "Content-Transfer-Encoding" -> "binary",
                      "Content-Length"            -> Files.size(f).toString
                    )
                  ),
                  body = HttpEntity.Streamed(FileIO.fromPath(f), Some(Files.size(f)), Some("application/zip"))
                ) // FIXME remove temporary file (but when ?)
              }
            }
            .recoverWith {
              case _: NoSuchElementException => Failure(NotFoundError(s"Attachment $id not found"))
            }
      }

  def password: String = passwordConfig.get
}
