package controllers

import java.nio.file.Files

import akka.stream.scaladsl.FileIO
import javax.inject.{Inject, Singleton}
import models.Roles
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.{CompressionLevel, EncryptionMethod}
import org.elastic4play.Timed
import org.elastic4play.controllers.Authenticated
import org.elastic4play.models.AttachmentAttributeFormat
import org.elastic4play.services.AttachmentSrv
import play.api.http.HttpEntity
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.mvc._
import play.api.{Configuration, mvc}

/**
  * Controller used to access stored attachments (plain or zipped)
  */
@Singleton
class AttachmentCtrl(
    password: String,
    tempFileCreator: DefaultTemporaryFileCreator,
    attachmentSrv: AttachmentSrv,
    authenticated: Authenticated,
    components: ControllerComponents,
) extends AbstractController(components) {

  @Inject() def this(
      configuration: Configuration,
      tempFileCreator: DefaultTemporaryFileCreator,
      attachmentSrv: AttachmentSrv,
      authenticated: Authenticated,
      components: ControllerComponents,
  ) =
    this(configuration.get[String]("datastore.attachment.password"), tempFileCreator, attachmentSrv, authenticated, components)

  /**
    * Download an attachment, identified by its hash, in plain format
    * File name can be specified. This method is not protected : browser will
    * open the document directly. It must be used only for safe file
    */
  @Timed("controllers.AttachmentCtrl.download")
  def download(hash: String, name: Option[String]): Action[AnyContent] = authenticated(Roles.read) { _ ⇒
    if (hash.startsWith("{{")) // angularjs hack
      NoContent
    else if (!name.getOrElse("").intersect(AttachmentAttributeFormat.forbiddenChar).isEmpty)
      mvc.Results.BadRequest("File name is invalid")
    else
      Result(
        header = ResponseHeader(
          200,
          Map("Content-Disposition" → s"""attachment; filename="${name.getOrElse(hash)}"""", "Content-Transfer-Encoding" → "binary")
        ),
        body = HttpEntity.Streamed(attachmentSrv.source(hash), None, None)
      )
  }

  /**
    * Download an attachment, identified by its hash, in zip format.
    * Zip file is protected by the password "malware"
    * File name can be specified (zip extension is append)
    */
  @Timed("controllers.AttachmentCtrl.downloadZip")
  def downloadZip(hash: String, name: Option[String]): Action[AnyContent] = authenticated(Roles.read) { _ ⇒
    if (!name.getOrElse("").intersect(AttachmentAttributeFormat.forbiddenChar).isEmpty)
      BadRequest("File name is invalid")
    else {
      val f = tempFileCreator.create("zip", hash).path
      Files.delete(f)
      val zipFile   = new ZipFile(f.toFile)
      zipFile.setPassword(password.toCharArray)
      val zipParams = new ZipParameters
      zipParams.setCompressionLevel(CompressionLevel.FASTEST)
      zipParams.setEncryptFiles(true)
      zipParams.setEncryptionMethod(EncryptionMethod.ZIP_STANDARD)
//      zipParams.setsetPassword(password.toCharArray)
      zipParams.setFileNameInZip(name.getOrElse(hash))
//      zipParams.setSourceExternalStream(true)
      zipFile.addStream(attachmentSrv.stream(hash), zipParams)

      Result(
        header = ResponseHeader(
          200,
          Map(
            "Content-Disposition"       → s"""attachment; filename="${name.getOrElse(hash)}.zip"""",
            "Content-Type"              → "application/zip",
            "Content-Transfer-Encoding" → "binary",
            "Content-Length"            → Files.size(f).toString
          )
        ),
        body = HttpEntity.Streamed(FileIO.fromPath(f), Some(Files.size(f)), Some("application/zip"))
      )
    }
  }
}
