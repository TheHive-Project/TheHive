package org.thp.thehive.controllers.v0

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.thp.scalligraph._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputAttachment, InputObservable}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services._
import play.api.Configuration
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import java.io.FilterInputStream
import java.nio.file.Files
import javax.inject.{Inject, Named, Singleton}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import shapeless._

import java.util.Base64

@Singleton
class ObservableCtrl @Inject() (
    configuration: Configuration,
    override val entrypoint: Entrypoint,
    @Named("with-thehive-schema") override val db: Database,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseSrv: CaseSrv,
    alertSrv: AlertSrv,
    attachmentSrv: AttachmentSrv,
    errorHandler: ErrorHandler,
    @Named("v0") override val queryExecutor: QueryExecutor,
    override val publicData: PublicObservable,
    temporaryFileCreator: DefaultTemporaryFileCreator
) extends ObservableRenderer
    with QueryCtrl {

  type AnyAttachmentType = InputAttachment :+: FFile :+: String :+: CNil

  def createInCase(caseId: String): Action[AnyContent] =
    entrypoint("create artifact in case")
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
                    .flatMap(obs => obs.attachment.map(createAttachmentObservableInCase(case0, obs, observableType, _)))
                else
                  inputAttachObs
                    .flatMap(obs => obs.data.map(createSimpleObservableInCase(case0, obs, observableType, _)))
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
      observableType: ObservableType with Entity,
      data: String
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        observableSrv
          .create(inputObservable.toObservable, observableType, data, inputObservable.tags, Nil)
          .flatMap(o => caseSrv.addObservable(`case`, o).map(_ => o))
      } match {
      case Success(o)     => Right(o.toJson)
      case Failure(error) => Left(errorHandler.toErrorResult(error)._2 ++ Json.obj("object" -> Json.obj("data" -> data)))
    }

  private def createAttachmentObservableInCase(
      `case`: Case with Entity,
      inputObservable: InputObservable,
      observableType: ObservableType with Entity,
      fileOrAttachment: Either[FFile, InputAttachment]
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        val observable = fileOrAttachment match {
          case Left(file) => observableSrv.create(inputObservable.toObservable, observableType, file, inputObservable.tags, Nil)
          case Right(attachment) =>
            for {
              attach <- attachmentSrv.duplicate(attachment.name, attachment.contentType, attachment.id)
              obs    <- observableSrv.create(inputObservable.toObservable, observableType, attach, inputObservable.tags, Nil)
            } yield obs
        }
        observable.flatMap(o => caseSrv.addObservable(`case`, o).map(_ => o))
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
                  .can(Permissions.manageAlert)
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
                        .map(createAttachmentObservableInAlert(alert, obs, observableType, _))
                    }
                else
                  inputAttachObs
                    .flatMap(obs => obs.data.map(createSimpleObservableInAlert(alert, obs, observableType, _)))
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
      observableType: ObservableType with Entity,
      data: String
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        observableSrv
          .create(inputObservable.toObservable, observableType, data, inputObservable.tags, Nil)
          .flatMap(o => alertSrv.addObservable(alert, o).map(_ => o))
      } match {
      case Success(o)     => Right(o.toJson)
      case Failure(error) => Left(errorHandler.toErrorResult(error)._2 ++ Json.obj("object" -> Json.obj("data" -> data)))
    }

  private def createAttachmentObservableInAlert(
      alert: Alert with Entity,
      inputObservable: InputObservable,
      observableType: ObservableType with Entity,
      attachment: AnyAttachmentType
  )(implicit authContext: AuthContext): Either[JsValue, JsValue] =
    db
      .tryTransaction { implicit graph =>
        object createAttachment extends Poly1 {
          implicit val fromFile: Case.Aux[FFile, Try[RichObservable]] = at[FFile] { file =>
            observableSrv.create(inputObservable.toObservable, observableType, file, inputObservable.tags, Nil)
          }
          implicit val fromAttachment: Case.Aux[InputAttachment, Try[RichObservable]] = at[InputAttachment] { attachment =>
            for {
              attach <- attachmentSrv.duplicate(attachment.name, attachment.contentType, attachment.id)
              obs    <- observableSrv.create(inputObservable.toObservable, observableType, attach, inputObservable.tags, Nil)
            } yield obs
          }

          implicit val fromString: Case.Aux[String, Try[RichObservable]] = at[String] { data =>
            data.split(';') match {
              case Array(filename, contentType, value) =>
                val data = Base64.getDecoder.decode(value)
                attachmentSrv
                  .create(filename, contentType, data)
                  .flatMap(attachment => observableSrv.create(inputObservable.toObservable, observableType, attachment, inputObservable.tags, Nil))
              case Array(filename, contentType) =>
                attachmentSrv
                  .create(filename, contentType, Array.emptyByteArray)
                  .flatMap(attachment => observableSrv.create(inputObservable.toObservable, observableType, attachment, inputObservable.tags, Nil))
              case data =>
                Failure(InvalidFormatAttributeError("artifacts.data", "filename;contentType;base64value", Set.empty, FString(data.mkString(";"))))
            }
          }
        }

        attachment
          .fold(createAttachment)
          .flatMap(o => alertSrv.addObservable(alert, o).map(_ => o))
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
          .visible
          .richObservable
          .getOrFail("Observable")
          .map { observable =>
            Results.Ok(observable.toJson)
          }
      }

  def update(observableId: String): Action[AnyContent] =
    entrypoint("update observable")
      .extract("observable", FieldsParser.update("observable", publicData.publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("observable")
        observableSrv
          .update(
            _.get(EntityIdOrName(observableId)).canManage,
            propertyUpdaters
          )
          .flatMap {
            case (observables, _) =>
              observables
                .richObservable
                .getOrFail("Observable")
                .map(richObservable => Results.Ok(richObservable.toJson))
          }
      }

  def findSimilar(observableId: String): Action[AnyContent] =
    entrypoint("find similar")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val observables = observableSrv
          .get(EntityIdOrName(observableId))
          .visible
          .filteredSimilar
          .visible
          .richObservableWithCustomRenderer(observableLinkRenderer)
          .toSeq

        Success(Results.Ok(observables.toJson))
      }

  def bulkUpdate: Action[AnyContent] =
    entrypoint("bulk update")
      .extract("input", FieldsParser.update("observable", publicData.publicProperties))
      .extract("ids", FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val properties: Seq[PropertyUpdater] = request.body("input")
        val ids: Seq[String]                 = request.body("ids")
        ids
          .toTry { id =>
            observableSrv
              .update(_.get(EntityIdOrName(id)).canManage, properties)
          }
          .map(_ => Results.NoContent)
      }

  def delete(observableId: String): Action[AnyContent] =
    entrypoint("delete")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          observable <-
            observableSrv
              .get(EntityIdOrName(observableId))
              .canManage
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

@Singleton
class PublicObservable @Inject() (
    observableSrv: ObservableSrv,
    organisationSrv: OrganisationSrv
) extends PublicData
    with ObservableRenderer {
  override val entityName: String = "observable"
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
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Observable], IteratorOutput](
      "page",
      FieldsParser[OutputParam],
      {
        case (OutputParam(from, to, withStats, 0), observableSteps, authContext) =>
          observableSteps
            .richPage(from, to, withTotal = true) {
              case o if withStats =>
                o.richObservableWithCustomRenderer(observableStatsRenderer(authContext))(authContext)
                  .domainMap(ros => (ros._1, ros._2, None: Option[RichCase]))
              case o =>
                o.richObservable.domainMap(ro => (ro, JsObject.empty, None))
            }
        case (OutputParam(from, to, _, _), observableSteps, authContext) =>
          observableSteps.richPage(from, to, withTotal = true)(
            _.richObservableWithCustomRenderer(o => o.`case`.richCase(authContext))(authContext).domainMap(roc =>
              (roc._1, JsObject.empty, Some(roc._2): Option[RichCase])
            )
          )
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
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Observable]
    .property("status", UMapping.string)(_.select(_.constant("Ok")).readonly)
    .property("startDate", UMapping.date)(_.select(_._createdAt).readonly)
    .property("ioc", UMapping.boolean)(_.field.updatable)
    .property("sighted", UMapping.boolean)(_.field.updatable)
    .property("ignoreSimilarity", UMapping.boolean)(_.field.updatable)
    .property("tags", UMapping.string.set)(
      _.select(_.tags.displayName)
        .filter((_, cases) =>
          cases
            .tags
            .graphMap[String, String, Converter.Identity[String]](
              { v =>
                val namespace = UMapping.string.getProperty(v, "namespace")
                val predicate = UMapping.string.getProperty(v, "predicate")
                val value     = UMapping.string.optional.getProperty(v, "value")
                Tag(namespace, predicate, value, None, 0).toString
              },
              Converter.identity[String]
            )
        )
        .converter(_ => Converter.identity[String])
        .custom { (_, value, vertex, _, graph, authContext) =>
          observableSrv
            .get(vertex)(graph)
            .getOrFail("Observable")
            .flatMap(observable => observableSrv.updateTagNames(observable, value)(graph, authContext))
            .map(_ => Json.obj("tags" -> value))
        }
    )
    .property("message", UMapping.string)(_.field.updatable)
    .property("tlp", UMapping.int)(_.field.updatable)
    .property("dataType", UMapping.string)(_.select(_.observableType.value(_.name)).readonly)
    .property("data", UMapping.string.optional)(_.select(_.data.value(_.data)).readonly)
    .property("attachment.name", UMapping.string.optional)(_.select(_.attachments.value(_.name)).readonly)
    .property("attachment.hashes", UMapping.hash.sequence)(_.select(_.attachments.value(_.hashes)).readonly)
    .property("attachment.size", UMapping.long.optional)(_.select(_.attachments.value(_.size)).readonly)
    .property("attachment.contentType", UMapping.string.optional)(_.select(_.attachments.value(_.contentType)).readonly)
    .property("attachment.id", UMapping.string.optional)(_.select(_.attachments.value(_.attachmentId)).readonly)
    .build
}
