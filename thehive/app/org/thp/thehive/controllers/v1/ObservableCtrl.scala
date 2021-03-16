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
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services._
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}
import play.api.{Configuration, Logger}
import shapeless.{:+:, CNil, Coproduct, Poly1}

import java.io.FilterInputStream
import java.nio.file.Files
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class ObservableCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    properties: Properties,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseSrv: CaseSrv,
    alertSrv: AlertSrv,
    organisationSrv: OrganisationSrv,
    attachmentSrv: AttachmentSrv,
    errorHandler: ErrorHandler,
    temporaryFileCreator: DefaultTemporaryFileCreator,
    configuration: Configuration
) extends QueryableCtrl
    with ObservableRenderer {

  type AnyAttachmentType = InputAttachment :+: FFile :+: String :+: CNil

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
    (idOrName, graph, authContext) => observableSrv.get(idOrName)(graph).visible(organisationSrv)(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Observable], IteratorOutput](
    "page",
    {
      case (OutputParam(from, to, extraData), observableSteps, authContext) =>
        observableSteps.richPage(from, to, extraData.contains("total")) {
          _.richObservableWithCustomRenderer(organisationSrv, observableStatsRenderer(organisationSrv, extraData - "total")(authContext))(authContext)
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
      (observableSteps, authContext) => observableSteps.filteredSimilar.visible(organisationSrv)(authContext)
    ),
    Query[Traversal.V[Observable], Traversal.V[Case]]("case", (observableSteps, _) => observableSteps.`case`),
    Query[Traversal.V[Observable], Traversal.V[Alert]]("alert", (observableSteps, _) => observableSteps.alert),
    Query[Traversal.V[Observable], Traversal.V[Share]]("shares", (observableSteps, authContext) => observableSteps.shares.visible(authContext))
  )

  def createInCase(caseId: String): Action[AnyContent] =
    entrypoint("create observable in case")
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
            } yield (case0, observableType)
          }
          .map {
            case (case0, observableType) =>
              val successesAndFailures =
                if (observableType.isAttachment)
                  inputAttachObs
                    .flatMap(obs => obs.attachment.map(createAttachmentObservableInCase(case0, obs, _)))
                else
                  inputAttachObs
                    .flatMap(obs => obs.data.map(createSimpleObservableInCase(case0, obs, _)))
              val (successes, failures) = successesAndFailures
                .foldLeft[(Seq[JsValue], Seq[JsValue])]((Nil, Nil)) {
                  case ((s, f), Right(o)) => (s :+ o, f)
                  case ((s, f), Left(o))  => (s, f :+ o)
                }
              if (failures.isEmpty) Results.Created(JsArray(successes))
              else Results.MultiStatus(Json.obj("success" -> successes, "failure" -> failures))
          }
      }

  private def createSimpleObservableInCase(
      `case`: Case with Entity,
      inputObservable: InputObservable,
      data: String
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        caseSrv.createObservable(`case`, inputObservable.toObservable, data)
      } match {
      case Success(o)     => Right(o.toJson)
      case Failure(error) => Left(errorHandler.toErrorResult(error)._2 ++ Json.obj("object" -> Json.obj("data" -> data)))
    }

  private def createAttachmentObservableInCase(
      `case`: Case with Entity,
      inputObservable: InputObservable,
      fileOrAttachment: Either[FFile, InputAttachment]
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        fileOrAttachment match {
          case Left(file) =>
            caseSrv.createObservable(`case`, inputObservable.toObservable, file)
          case Right(attachment) =>
            for {
              attach <- attachmentSrv.duplicate(attachment.name, attachment.contentType, attachment.id)
              obs    <- caseSrv.createObservable(`case`, inputObservable.toObservable, attach)
            } yield obs
        }
      } match {
      case Success(o) => Right(o.toJson)
      case _ =>
        val filename = fileOrAttachment.fold(_.filename, _.name)
        Left(Json.obj("object" -> Json.obj("data" -> s"file:$filename", "attachment" -> Json.obj("name" -> filename))))
    }

  def createInAlert(alertId: String): Action[AnyContent] =
    entrypoint("create artifact in alert")
      .extract("artifact", FieldsParser[InputObservable])
      .extract("isZip", FieldsParser.boolean.optional.on("isZip"))
      .extract("zipPassword", FieldsParser.string.optional.on("zipPassword"))
      .auth { implicit request =>
        val inputObservable: InputObservable = request.body("artifact")
        val isZip: Option[Boolean]           = request.body("isZip")
        val zipPassword: Option[String]      = request.body("zipPassword")
        val inputAttachObs                   = if (isZip.contains(true)) getZipFiles(inputObservable, zipPassword) else Seq(inputObservable)

        db
          .roTransaction { implicit graph =>
            for {
              alert <-
                alertSrv
                  .get(EntityIdOrName(alertId))
                  .can(organisationSrv, Permissions.manageAlert)
                  .orFail(AuthorizationError("Operation not permitted"))
              observableType <- observableTypeSrv.getOrFail(EntityName(inputObservable.dataType))
            } yield (alert, observableType)
          }
          .map {
            case (alert, observableType) =>
              val successesAndFailures =
                if (observableType.isAttachment)
                  inputAttachObs
                    .flatMap { obs =>
                      (obs.attachment.map(_.fold(Coproduct[AnyAttachmentType](_), Coproduct[AnyAttachmentType](_))) ++
                        obs.data.map(Coproduct[AnyAttachmentType](_)))
                        .map(createAttachmentObservableInAlert(alert, obs, _))
                    }
                else
                  inputAttachObs
                    .flatMap(obs => obs.data.map(createSimpleObservableInAlert(alert, obs, _)))
              val (successes, failures) = successesAndFailures
                .foldLeft[(Seq[JsValue], Seq[JsValue])]((Nil, Nil)) {
                  case ((s, f), Right(o)) => (s :+ o, f)
                  case ((s, f), Left(o))  => (s, f :+ o)
                }
              if (failures.isEmpty) Results.Created(JsArray(successes))
              else Results.MultiStatus(Json.obj("success" -> successes, "failure" -> failures))
          }
      }

  private def createSimpleObservableInAlert(
      alert: Alert with Entity,
      inputObservable: InputObservable,
      data: String
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        alertSrv.createObservable(alert, inputObservable.toObservable, data)
      } match {
      case Success(o)     => Right(o.toJson)
      case Failure(error) => Left(errorHandler.toErrorResult(error)._2 ++ Json.obj("object" -> Json.obj("data" -> data)))
    }

  private def createAttachmentObservableInAlert(
      alert: Alert with Entity,
      inputObservable: InputObservable,
      attachment: AnyAttachmentType
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        object createAttachment extends Poly1 {
          implicit val fromFile: Case.Aux[FFile, Try[RichObservable]] = at[FFile] { file =>
            alertSrv.createObservable(alert, inputObservable.toObservable, file)
          }
          implicit val fromAttachment: Case.Aux[InputAttachment, Try[RichObservable]] = at[InputAttachment] { attachment =>
            for {
              attach <- attachmentSrv.duplicate(attachment.name, attachment.contentType, attachment.id)
              obs    <- alertSrv.createObservable(alert, inputObservable.toObservable, attach)
            } yield obs
          }

          implicit val fromString: Case.Aux[String, Try[RichObservable]] = at[String] { data =>
            data.split(';') match {
              case Array(filename, contentType, value) =>
                val data = Base64.getDecoder.decode(value)
                attachmentSrv
                  .create(filename, contentType, data)
                  .flatMap(attachment => alertSrv.createObservable(alert, inputObservable.toObservable, attachment))
              case Array(filename, contentType) =>
                attachmentSrv
                  .create(filename, contentType, Array.emptyByteArray)
                  .flatMap(attachment => alertSrv.createObservable(alert, inputObservable.toObservable, attachment))
              case data =>
                Failure(InvalidFormatAttributeError("artifacts.data", "filename;contentType;base64value", Set.empty, FString(data.mkString(";"))))
            }
          }
        }
        attachment.fold(createAttachment)
      } match {
      case Success(o) => Right(o.toJson)
      case _ =>
        object attachmentName extends Poly1 {
          implicit val fromFile: Case.Aux[FFile, String]                 = at[FFile](_.filename)
          implicit val fromAttachment: Case.Aux[InputAttachment, String] = at[InputAttachment](_.name)
          implicit val fromString: Case.Aux[String, String] = at[String] { data =>
            if (data.contains(';')) data.takeWhile(_ != ';') else "no name"
          }
        }
        val filename = attachment.fold(attachmentName)
        Left(Json.obj("object" -> Json.obj("data" -> s"file:$filename", "attachment" -> Json.obj("name" -> filename))))
    }

  def get(observableId: String): Action[AnyContent] =
    entrypoint("get observable")
      .authRoTransaction(db) { implicit request => implicit graph =>
        observableSrv
          .get(EntityIdOrName(observableId))
          .visible(organisationSrv)
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
          .update(_.get(EntityIdOrName(observableId)).canManage(organisationSrv), propertyUpdaters)
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
              .update(_.get(EntityIdOrName(id)).canManage(organisationSrv), properties)
          }
          .map(_ => Results.NoContent)
      }

  def delete(observableId: String): Action[AnyContent] =
    entrypoint("delete observable")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          observable <-
            observableSrv
              .get(EntityIdOrName(observableId))
              .canManage(organisationSrv)
              .getOrFail("Observable")
          _ <- observableSrv.delete(observable)
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
