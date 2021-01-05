package org.thp.thehive.controllers.v1

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.thp.scalligraph._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.{InputAttachment, InputObservable}
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services._
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}
import play.api.{Configuration, Logger}

import java.io.FilterInputStream
import java.nio.file.Files
import javax.inject.{Inject, Singleton}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

@Singleton
class ObservableCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    properties: Properties,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseSrv: CaseSrv,
    organisationSrv: OrganisationSrv,
    attachmentSrv: AttachmentSrv,
    errorHandler: ErrorHandler,
    temporaryFileCreator: DefaultTemporaryFileCreator,
    configuration: Configuration
) extends QueryableCtrl
    with ObservableRenderer {

  lazy val logger: Logger                         = Logger(getClass)
  override val entityName: String                 = "observable"
  override val publicProperties: PublicProperties = properties.observable
  override val initialQuery: Query =
    Query.init[Traversal.V[Observable]](
      "listObservable",
      (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.observables
    )
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Observable]](
    "getObservable",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => observableSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Observable], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    {
      case (OutputParam(from, to, extraData), observableSteps, authContext) =>
        observableSteps.richPage(from, to, extraData.contains("total")) {
          _.richObservableWithCustomRenderer(observableStatsRenderer(extraData - "total")(authContext))(authContext)
        }
    }
  )
  override val outputQuery: Query = Query.output[RichObservable, Traversal.V[Observable]](_.richObservable)

  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Observable], Traversal.V[Organisation]](
      "organisations",
      (observableSteps, authContext) => observableSteps.organisations.visible(authContext)
    ),
    Query[Traversal.V[Observable], Traversal.V[Observable]](
      "similar",
      (observableSteps, authContext) => observableSteps.filteredSimilar.visible(authContext)
    ),
    Query[Traversal.V[Observable], Traversal.V[Case]]("case", (observableSteps, _) => observableSteps.`case`),
    Query[Traversal.V[Observable], Traversal.V[Alert]]("alert", (observableSteps, _) => observableSteps.alert)
  )

  def create(caseId: String): Action[AnyContent] =
    entrypoint("create observable")
      .extract("observable", FieldsParser[InputObservable])
      .extract("isZip", FieldsParser.boolean.optional.on("isZip"))
      .extract("zipPassword", FieldsParser.string.optional.on("zipPassword"))
      .auth { implicit request =>
        val inputObservable: InputObservable = request.body("observable")
        val isZip: Option[Boolean]           = request.body("isZip")
        val zipPassword: Option[String]      = request.body("zipPassword")
        val inputAttachObs                   = if (isZip.contains(true)) getZipFiles(inputObservable, zipPassword) else Seq(inputObservable)

        db
          .roTransaction { implicit graph =>
            for {
              case0 <-
                caseSrv
                  .get(EntityIdOrName(caseId))
                  .can(Permissions.manageObservable)
                  .orFail(AuthorizationError("Operation not permitted"))
              observableType <- observableTypeSrv.getOrFail(EntityName(inputObservable.dataType))
              organisation   <- organisationSrv.current.getOrFail("Organisation")
            } yield (case0, observableType, organisation)
          }
          .map {
            case (case0, observableType, organisation) =>
              val successesAndFailures =
                if (observableType.isAttachment)
                  inputAttachObs
                    .flatMap(obs => obs.attachment.map(createAttachmentObservable(organisation, case0, obs, observableType, _)))
                else
                  inputAttachObs
                    .flatMap(obs => obs.data.map(createSimpleObservable(organisation, case0, obs, observableType, _)))
              val (successes, failures) = successesAndFailures
                .foldLeft[(Seq[JsValue], Seq[JsValue])]((Nil, Nil)) {
                  case ((s, f), Right(o)) => (s :+ o, f)
                  case ((s, f), Left(o))  => (s, f :+ o)
                }
              if (failures.isEmpty) Results.Created(JsArray(successes))
              else Results.MultiStatus(Json.obj("success" -> successes, "failure" -> failures))
          }
      }

  def createSimpleObservable(
      organisation: Organisation with Entity,
      `case`: Case with Entity,
      inputObservable: InputObservable,
      observableType: ObservableType with Entity,
      data: String
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        observableSrv
          .create(inputObservable.toObservable(organisation._id), observableType, data, inputObservable.tags, Nil)
          .flatMap(o => caseSrv.addObservable(`case`, o).map(_ => o))
      } match {
      case Success(o)     => Right(o.toJson)
      case Failure(error) => Left(errorHandler.toErrorResult(error)._2 ++ Json.obj("object" -> Json.obj("data" -> data)))
    }

  def createAttachmentObservable(
      organisation: Organisation with Entity,
      `case`: Case with Entity,
      inputObservable: InputObservable,
      observableType: ObservableType with Entity,
      fileOrAttachment: Either[FFile, InputAttachment]
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        val observable = fileOrAttachment match {
          case Left(file) => observableSrv.create(inputObservable.toObservable(organisation._id), observableType, file, inputObservable.tags, Nil)
          case Right(attachment) =>
            for {
              attach <- attachmentSrv.duplicate(attachment.name, attachment.contentType, attachment.id)
              obs    <- observableSrv.create(inputObservable.toObservable(organisation._id), observableType, attach, inputObservable.tags, Nil)
            } yield obs
        }
        observable.flatMap(o => caseSrv.addObservable(`case`, o).map(_ => o))
      } match {
      case Success(o) => Right(o.toJson)
      case _ =>
        val filename = fileOrAttachment.fold(_.filename, _.name)
        Left(Json.obj("object" -> Json.obj("data" -> s"file:$filename", "attachment" -> Json.obj("name" -> filename))))
    }

  def get(observableId: String): Action[AnyContent] =
    entrypoint("get observable")
      .authRoTransaction(db) { _ => implicit graph =>
        observableSrv
          .get(EntityIdOrName(observableId))
          //            .availableFor(request.organisation)
          .richObservable
          .getOrFail("Observable")
          .map { observable =>
            Results.Ok(observable.toJson)
          }
      }

  def update(observableId: String): Action[AnyContent] =
    entrypoint("update observable")
      .extract("observable", FieldsParser.update("observable", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("observable")
        observableSrv
          .update(
            _.get(EntityIdOrName(observableId)).can(Permissions.manageObservable),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def bulkUpdate: Action[AnyContent] =
    entrypoint("bulk update")
      .extract("input", FieldsParser.update("observable", publicProperties))
      .extract("ids", FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val properties: Seq[PropertyUpdater] = request.body("input")
        val ids: Seq[String]                 = request.body("ids")
        ids
          .toTry { id =>
            observableSrv
              .update(_.get(EntityIdOrName(id)).can(Permissions.manageObservable), properties)
          }
          .map(_ => Results.NoContent)
      }

  def delete(obsId: String): Action[AnyContent] =
    entrypoint("delete")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          observable <-
            observableSrv
              .get(EntityIdOrName(obsId))
              .can(Permissions.manageObservable)
              .getOrFail("Observable")
          _ <- observableSrv.remove(observable)
        } yield Results.NoContent
      }

  // extract a file from the archive and make sure its size matches the header (to protect against zip bombs)
  private def extractAndCheckSize(zipFile: ZipFile, header: FileHeader): Option[FFile] = {
    val fileName = header.getFileName
    if (fileName.contains('/')) None
    else {
      val file = temporaryFileCreator.create("zip")

      val input = zipFile.getInputStream(header)
      val size  = header.getUncompressedSize
      val sizedInput: FilterInputStream = new FilterInputStream(input) {
        var totalRead = 0

        override def read(): Int =
          if (totalRead < size) {
            totalRead += 1
            super.read()
          } else throw BadRequestError("Error extracting file: output size doesn't match header")
      }
      Files.delete(file)
      val fileSize = Files.copy(sizedInput, file)
      if (fileSize != size) {
        file.toFile.delete()
        throw InternalError("Error extracting file: output size doesn't match header")
      }
      input.close()
      val contentType = Option(Files.probeContentType(file)).getOrElse("application/octet-stream")
      Some(FFile(header.getFileName, file, contentType))
    }
  }

  private def getZipFiles(observable: InputObservable, zipPassword: Option[String]): Seq[InputObservable] =
    observable.attachment.flatMap(_.swap.toSeq).flatMap { attachment =>
      val zipFile                = new ZipFile(attachment.filepath.toFile)
      val files: Seq[FileHeader] = zipFile.getFileHeaders.asScala.asInstanceOf[Seq[FileHeader]]

      if (zipFile.isEncrypted)
        zipFile.setPassword(zipPassword.getOrElse(configuration.get[String]("datastore.attachment.password")).toCharArray)

      files
        .filterNot(_.isDirectory)
        .flatMap(extractAndCheckSize(zipFile, _))
        .map(ffile => observable.copy(attachment = Seq(Left(ffile))))
    }
}
