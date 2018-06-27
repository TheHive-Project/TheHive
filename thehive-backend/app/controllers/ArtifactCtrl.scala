package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import scala.collection.JavaConverters._

import play.api.http.Status
import play.api.libs.json.{ JsArray, JsString }
import play.api.mvc._
import play.api.Logger
import play.api.Configuration

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.FileHeader

import models.Roles
import services.ArtifactSrv

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer, FileInputValue, JsonInputValue }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.{ aggReads, queryReads }
import org.elastic4play.services._
import org.elastic4play.{ BadRequestError, Timed, InternalError }

import java.io.FileOutputStream
import java.nio.file.{ Files, Paths, Path }

@Singleton
class ArtifactCtrl @Inject() (
    artifactSrv: ArtifactSrv,
    auxSrv: AuxSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    components: ControllerComponents,
    fieldsBodyParser: FieldsBodyParser,
    configuration: Configuration,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  private[ArtifactCtrl] lazy val logger = Logger(getClass)

  private final val BUFF_SIZE = 4096;

  @Timed
  def create(caseId: String): Action[Fields] = authenticated(Roles.write).async(fieldsBodyParser) { implicit request ⇒
    val fields = request.body
    val data = fields.getStrings("data")
      .getOrElse(fields.getString("data").toSeq)
      .map(_.trim) // most observables don't accept leading or trailing space
      .filterNot(_.isEmpty)
    // if data is not multivalued, use simple API (not bulk API)
    if (data.isEmpty) {

      if (fields.getString("dataType").getOrElse("") == "zipfile") {

        val contentType = fields.get("attachment").get.jsonValue("contentType").as[String]
        if (contentType != "application/x-zip-compressed")
          throw new InternalError("contentType is not application/x-zip-compressed")

        val filepath = Paths.get(fields.get("attachment").get.jsonValue("filepath").as[String])
        val zipFile = new ZipFile(filepath.toString)
        val destPath = filepath.getParent
        val files = zipFile.getFileHeaders.asScala

        if (zipFile.isEncrypted()) {
          val tags = fields.get("tags").get.jsonValue.as[List[String]]
          val pw = tags.filter(_.startsWith("password:")).map(_.split(":")(1)).lift(0)
            .getOrElse(configuration.get[String]("datastore.attachment.password"))
          zipFile.setPassword(pw)
        }

        // extract a file from the archive and make sure its size matches the header (to protect against zip bombs)
        def extractAndCheckSize(header: FileHeader, dest: Path) {
          val file = Paths.get(destPath.toString(), header.getFileName())
          if (file.toFile.exists)
            throw new InternalError("Error extracting file: already exists")
          else if (!header.isDirectory()) {
            val parent = file.getParent.toFile
            if (!parent.exists)
              parent.mkdirs

            val is = zipFile.getInputStream(header)
            val os = new FileOutputStream(file.toFile)
            val buff = new Array[Byte](4096)
            val size = header.getUncompressedSize

            var bytesRead = 0
            var b = 0
            while ({ b = is.read(buff); b } != -1 && bytesRead <= size) {
              os.write(buff, 0, b)
              bytesRead += b
            }

            if (bytesRead != size) {
              file.toFile.delete()
              throw new InternalError("Error extracting file: output size doesn't match header")
            }

            os.close()
            is.close()
          }
        }

        files.foreach(header ⇒ extractAndCheckSize(header.asInstanceOf[FileHeader], destPath))

        val multiFields = files.filter(!_.asInstanceOf[FileHeader].isDirectory()).map(file ⇒ {
          val filePath = Paths.get(destPath.toString, file.asInstanceOf[FileHeader].getFileName())
          val contentType = Files.probeContentType(filePath)
          fields
            .set("dataType", JsonInputValue(JsString("file")))
            .set("attachment", FileInputValue(filePath.getFileName.toString, filePath, contentType))
        })

        artifactSrv.create(caseId, multiFields)
          .map(multiResult ⇒ renderer.toMultiOutput(CREATED, multiResult))

      }
      else
        artifactSrv.create(caseId, fields)
          .map(artifact ⇒ renderer.toOutput(CREATED, artifact))
    }
    else if (data.length == 1) {
      artifactSrv.create(caseId, fields.set("data", data.head))
        .map(artifact ⇒ renderer.toOutput(CREATED, artifact))
    }
    else {
      val multiFields = data.map(fields.set("data", _))
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
