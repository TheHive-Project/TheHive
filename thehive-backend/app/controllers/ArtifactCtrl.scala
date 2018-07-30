package controllers

import java.io.FilterInputStream
import java.nio.file.Files

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

import play.api.{ Configuration, Logger }
import play.api.http.Status
import play.api.libs.json.JsArray
import play.api.mvc._

import javax.inject.{ Inject, Singleton }
import models.Roles
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.FileHeader
import services.ArtifactSrv

import org.elastic4play.controllers._
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }
import org.elastic4play.services._
import org.elastic4play.{ BadRequestError, InternalError, Timed }

@Singleton
class ArtifactCtrl @Inject() (
    artifactSrv: ArtifactSrv,
    auxSrv: AuxSrv,
    tempSrv: TempSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    configuration: Configuration,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  private[ArtifactCtrl] lazy val logger = Logger(getClass)

  // extract a file from the archive and make sure its size matches the header (to protect against zip bombs)
  private def extractAndCheckSize(zipFile: ZipFile, header: FileHeader)(implicit authContext: AuthContext): FileInputValue = {
    val file = tempSrv.newTemporaryFile(header.getFileName, "-fromZipFile")

    val input = zipFile.getInputStream(header)
    val size = header.getUncompressedSize
    val sizedInput: FilterInputStream = new FilterInputStream(input) {
      var totalRead = 0

      override def read(): Int = {
        if (totalRead < size) {
          totalRead += 1
          super.read()
        }
        else throw BadRequestError("Error extracting file: output size doesn't match header")
      }
    }
    Files.delete(file)
    val fileSize = Files.copy(sizedInput, file)
    if (fileSize != size) {
      file.toFile.delete()
      throw InternalError("Error extracting file: output size doesn't match header")
    }
    input.close()
    FileInputValue(header.getFileName, file, Files.probeContentType(file))
  }

  @Timed
  def create(caseId: String): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) {
    implicit request ⇒
      val fields = request.body
      val data = fields.getStrings("data")
        .getOrElse(fields.getString("data").toSeq)
        .map(_.trim) // most observables don't accept leading or trailing space
        .filterNot(_.isEmpty)
      // if data is not multivalued, use simple API (not bulk API)
      if (data.isEmpty) {

        fields.get("attachment")
          .collect {
            case FileInputValue(_, filepath, _) if fields.getBoolean("isZip").getOrElse(false) ⇒

              val zipFile = new ZipFile(filepath.toFile)
              val files: Seq[FileHeader] = zipFile.getFileHeaders.asScala.asInstanceOf[Seq[FileHeader]]

              if (zipFile.isEncrypted) {
                val pw = fields.getString("zipPassword")
                  .getOrElse(configuration.get[String]("datastore.attachment.password"))
                zipFile.setPassword(pw)
              }

              val multiFields = files.filterNot(_.isDirectory)
                .map(extractAndCheckSize(zipFile, _))
                .map { fiv ⇒
                  fields
                    .unset("isZip")
                    .unset("zipPassword")
                    .set("dataType", "file")
                    .set("attachment", fiv)
                }
              artifactSrv.create(caseId, multiFields)
                .map(multiResult ⇒ renderer.toMultiOutput(CREATED, multiResult))
          }
          .getOrElse {
            artifactSrv.create(caseId, fields.unset("isZip").unset("zipPassword"))
              .map(artifact ⇒ renderer.toOutput(CREATED, artifact))
          }
      }
      else if (data.length == 1) {
        artifactSrv.create(caseId, fields.set("data", data.head).unset("isZip").unset("zipPassword"))
          .map(artifact ⇒ renderer.toOutput(CREATED, artifact))
      }
      else {
        val multiFields = data.map(fields.set("data", _).unset("isZip").unset("zipPassword"))
        artifactSrv.create(caseId, multiFields)
          .map(multiResult ⇒ renderer.toMultiOutput(CREATED, multiResult))
      }
  }

  @Timed
  def get(id: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    artifactSrv.get(id)
      .map(artifact ⇒ renderer.toOutput(OK, artifact))
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    artifactSrv.update(id, request.body)
      .map(artifact ⇒ renderer.toOutput(OK, artifact))
  }

  @Timed
  def bulkUpdate(): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    request.body.getStrings("ids").fold(Future.successful(Ok(JsArray()))) { ids ⇒
      artifactSrv.bulkUpdate(ids, request.body.unset("ids")).map(multiResult ⇒ renderer.toMultiOutput(OK, multiResult))
    }
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Roles.write).async { implicit request ⇒
    artifactSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def findInCase(caseId: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    import org.elastic4play.services.QueryDSL._
    val childQuery = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val query = and(childQuery, "_parent" ~= caseId)
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)

    val (artifacts, total) = artifactSrv.find(query, range, sort)
    renderer.toOutput(OK, artifacts, total)
  }

  @Timed
  def find(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val nparent = request.body.getLong("nparent").getOrElse(0L).toInt
    val withStats = request.body.getBoolean("nstats").getOrElse(false)

    val (artifacts, total) = artifactSrv.find(query, range, sort)
    val artifactWithCase = auxSrv(artifacts, nparent, withStats, removeUnaudited = false)
    renderer.toOutput(OK, artifactWithCase, total)
  }

  @Timed
  def findSimilar(artifactId: String): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    artifactSrv.get(artifactId).flatMap { artifact ⇒
      val range = request.body.getString("range")
      val sort = request.body.getStrings("sort").getOrElse(Nil)

      val (artifacts, total) = artifactSrv.findSimilar(artifact, range, sort)
      val artifactWithCase = auxSrv(artifacts, 1, withStats = false, removeUnaudited = true)
      renderer.toOutput(OK, artifactWithCase, total)
    }
  }

  @Timed
  def stats(): Action[Fields] = authenticated(Roles.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val aggs = request.body.getValue("stats").getOrElse(throw BadRequestError("Parameter \"stats\" is missing")).as[Seq[Agg]]
    artifactSrv.stats(query, aggs).map(s ⇒ Ok(s))
  }
}
