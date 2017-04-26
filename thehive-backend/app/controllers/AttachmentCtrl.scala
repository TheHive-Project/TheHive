package controllers

import javax.inject.{ Inject, Singleton }

import akka.stream.scaladsl.FileIO
import play.api.Configuration
import play.api.http.HttpEntity
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import org.elastic4play.Timed
import org.elastic4play.services.{ AttachmentSrv, Role }
import org.elastic4play.models.AttachmentAttributeFormat
import org.elastic4play.models.AttachmentAttributeFormat
import org.elastic4play.controllers.Authenticated
import org.elastic4play.controllers.Renderer

/**
 * Controller used to access stored attachments (plain or zipped)
 */
@Singleton
class AttachmentCtrl(
    password: String,
    attachmentSrv: AttachmentSrv,
    authenticated: Authenticated,
    renderer: Renderer) extends Controller {

  @Inject() def this(
    configuration: Configuration,
    attachmentSrv: AttachmentSrv,
    authenticated: Authenticated,
    renderer: Renderer) =
    this(
      configuration.getString("datastore.attachment.password").get,
      attachmentSrv,
      authenticated,
      renderer)

  /**
   * Download an attachment, identified by its hash, in plain format
   * File name can be specified. This method is not protected : browser will
   * open the document directly. It must be used only for safe file
   */
  @Timed("controllers.AttachmentCtrl.download")
  def download(hash: String, name: Option[String]): Action[AnyContent] = authenticated(Role.read) { implicit request ⇒
    if (hash.startsWith("{{")) // angularjs hack
      NoContent
    else if (!name.getOrElse("").intersect(AttachmentAttributeFormat.forbiddenChar).isEmpty())
      BadRequest("File name is invalid")
    else
      Result(
        header = ResponseHeader(
          200,
          Map(
            "Content-Disposition" → s"""attachment; filename="${name.getOrElse(hash)}"""",
            "Content-Transfer-Encoding" → "binary")),
        body   = HttpEntity.Streamed(attachmentSrv.source(hash), None, None))
  }

  /**
   * Download an attachment, identified by its hash, in zip format.
   * Zip file is protected by the password "malware"
   * File name can be specified (zip extension is append)
   */
  @Timed("controllers.AttachmentCtrl.downloadZip")
  def downloadZip(hash: String, name: Option[String]): Action[AnyContent] = authenticated(Role.read) { implicit request ⇒
    if (!name.getOrElse("").intersect(AttachmentAttributeFormat.forbiddenChar).isEmpty())
      BadRequest("File name is invalid")
    else {
      val f = TemporaryFile("zip", hash).file
      f.delete()
      val zipFile = new ZipFile(f)
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
            "Content-Disposition" → s"""attachment; filename="${name.getOrElse(hash)}.zip"""",
            "Content-Type" → "application/zip",
            "Content-Transfer-Encoding" → "binary",
            "Content-Length" → f.length.toString)),
        body   = HttpEntity.Streamed(FileIO.fromPath(f.toPath), Some(f.length), Some("application/zip")))
    }
  }
}