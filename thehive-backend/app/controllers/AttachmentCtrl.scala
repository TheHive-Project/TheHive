package controllers

import java.net.URLEncoder
import java.nio.file.Files
import javax.inject.{ Inject, Singleton }

import play.api.http.HttpEntity
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.mvc._
import play.api.{ Configuration, mvc }

import akka.stream.scaladsl.FileIO
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import models.Roles

import org.elastic4play.Timed
import org.elastic4play.controllers.{ Authenticated, Renderer }
import org.elastic4play.models.AttachmentAttributeFormat
import org.elastic4play.services.AttachmentSrv

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
    renderer: Renderer) extends AbstractController(components) {

  @Inject() def this(
      configuration: Configuration,
      tempFileCreator: DefaultTemporaryFileCreator,
      attachmentSrv: AttachmentSrv,
      authenticated: Authenticated,
      components: ControllerComponents,
      renderer: Renderer) =
    this(
      configuration.get[String]("datastore.attachment.password"),
      tempFileCreator,
      attachmentSrv,
      authenticated,
      components,
      renderer)

  /**
    * Download an attachment, identified by its hash, in plain format
    * File name can be specified. This method is not protected : browser will
    * open the document directly. It must be used only for safe file
    */
  @Timed("controllers.AttachmentCtrl.download")
  def download(hash: String, name: Option[String]): Action[AnyContent] = authenticated(Roles.read) { implicit request ⇒
    if (hash.startsWith("{{")) // angularjs hack
      NoContent
    else if (!name.getOrElse("").intersect(AttachmentAttributeFormat.forbiddenChar).isEmpty)
      mvc.Results.BadRequest("File name is invalid")
    else
      Result(
        header = ResponseHeader(
          200,
          Map(
            "Content-Disposition" → s"""attachment; filename="${URLEncoder.encode(name.getOrElse(hash), "utf-8")}"""",
            "Content-Transfer-Encoding" → "binary")),
        body = HttpEntity.Streamed(attachmentSrv.source(hash), None, None))
  }

  /**
    * Download an attachment, identified by its hash, in zip format.
    * Zip file is protected by the password "malware"
    * File name can be specified (zip extension is append)
    */
  @Timed("controllers.AttachmentCtrl.downloadZip")
  def downloadZip(hash: String, name: Option[String]): Action[AnyContent] = authenticated(Roles.read) { implicit request ⇒
    if (!name.getOrElse("").intersect(AttachmentAttributeFormat.forbiddenChar).isEmpty)
      BadRequest("File name is invalid")
    else {
      val f = tempFileCreator.create("zip", hash).path
      Files.delete(f)
      val zipFile = new ZipFile(f.toFile)
      val zipParams = new ZipParameters
      zipParams.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_FASTEST)
      zipParams.setEncryptFiles(true)
      zipParams.setEncryptionMethod(Zip4jConstants.ENC_METHOD_STANDARD)
      zipParams.setPassword(password)
      zipParams.setFileNameInZip(name.getOrElse(hash))
      zipParams.setSourceExternalStream(true)
      zipFile.addStream(attachmentSrv.stream(hash), zipParams)

      Result(
        header = ResponseHeader(
          200,
          Map(
            "Content-Disposition" → s"""attachment; filename="${URLEncoder.encode(name.getOrElse(hash), "utf-8")}.zip"""",
            "Content-Type" → "application/zip",
            "Content-Transfer-Encoding" → "binary",
            "Content-Length" → Files.size(f).toString)),
        body = HttpEntity.Streamed(FileIO.fromPath(f), Some(Files.size(f)), Some("application/zip")))
    }
  }
}