package org.thp.thehive.controllers.v0

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.thp.scalligraph._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity, IndexType, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputAttachment, InputObservable}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Configuration
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}
import shapeless._

import java.io.FilterInputStream
import java.nio.file.Files
import java.util.Base64
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class ObservableCtrl(
    configuration: Configuration,
    override val entrypoint: Entrypoint,
    override val db: Database,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseSrv: CaseSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    alertSrv: AlertSrv,
    attachmentSrv: AttachmentSrv,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicObservable,
    temporaryFileCreator: TemporaryFileCreator
) extends ObservableRenderer
    with QueryCtrl
    with TheHiveOps {

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
              observableType <- observableTypeSrv.getOrFail(EntityName(inputObservable.dataType.value))
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
                    .flatMap(obs => obs.data.map(d => createSimpleObservableInCase(case0, obs, d.value)))
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
      case Failure(error) => Left(ErrorHandler.toErrorResult(error)._2 + ("object" -> Json.obj("data" -> data)))
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
              attach <- attachmentSrv.duplicate(attachment.name.value, attachment.contentType.value, attachment.id.value)
              obs    <- caseSrv.createObservable(`case`, inputObservable.toObservable, attach)
            } yield obs
        }
      } match {
      case Success(o) => Right(o.toJson)
      case Failure(error) =>
        val filename = fileOrAttachment.fold(_.filename, _.name.value)
        Left(ErrorHandler.toErrorResult(error)._2 + ("object" -> Json.obj("data" -> s"file:$filename", "attachment" -> Json.obj("name" -> filename))))
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
              observableType <- observableTypeSrv.getOrFail(EntityName(inputObservable.dataType.value))
            } yield (alert, observableType)
          }
          .map {
            case (alert, observableType) =>
              val successesAndFailures =
                if (observableType.isAttachment)
                  inputAttachObs
                    .flatMap { obs =>
                      (obs.attachment.map(_.fold(Coproduct[AnyAttachmentType](_), Coproduct[AnyAttachmentType](_))) ++
                        obs.data.map(d => Coproduct[AnyAttachmentType](d.value)))
                        .map(createAttachmentObservableInAlert(alert, obs, _))
                    }
                else
                  inputAttachObs
                    .flatMap(obs => obs.data.map(d => createSimpleObservableInAlert(alert, obs, d.value)))
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
      case Failure(error) => Left(ErrorHandler.toErrorResult(error)._2 + ("object" -> Json.obj("data" -> data)))
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
              attach <- attachmentSrv.duplicate(attachment.name.value, attachment.contentType.value, attachment.id.value)
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
      case Failure(error) =>
        object attachmentName extends Poly1 {
          implicit val fromFile: Case.Aux[FFile, String]                 = at[FFile](_.filename)
          implicit val fromAttachment: Case.Aux[InputAttachment, String] = at[InputAttachment](_.name.value)
          implicit val fromString: Case.Aux[String, String] = at[String] { data =>
            if (data.contains(';')) data.takeWhile(_ != ';') else "no name"
          }
        }
        val filename = attachment.fold(attachmentName)
        Left(ErrorHandler.toErrorResult(error)._2 + ("object" -> Json.obj("data" -> s"file:$filename", "attachment" -> Json.obj("name" -> filename))))
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
          .richObservableWithCustomRenderer(organisationSrv, observableLinkRenderer)
          .toIterator
          .filterNot(_._2.keys.isEmpty)
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
      val zipFile = new ZipFile(attachment.filepath.toFile)
      val files   = zipFile.getFileHeaders.asScala

      if (zipFile.isEncrypted)
        zipFile.setPassword(zipPassword.getOrElse(configuration.get[String]("datastore.attachment.password")).toCharArray)

      files
        .filterNot(_.isDirectory)
        .flatMap(extractAndCheckSize(zipFile, _))
        .map(ffile => observable.copy(attachment = Seq(Left(ffile))))
    }
}

class PublicObservable(
    observableTypeSrv: ObservableTypeSrv,
    observableSrv: ObservableSrv,
    searchSrv: SearchSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv
) extends PublicData
    with ObservableRenderer {
  override val entityName: String = "observable"
  override val initialQuery: Query =
    Query.init[Traversal.V[Observable]](
      "listObservable",
      (graph, authContext) => observableSrv.startTraversal(graph).visible(authContext)
    )
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Observable]](
    "getObservable",
    (idOrName, graph, authContext) => observableSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Observable], IteratorOutput](
      "page",
      {
        case (OutputParam(from, to, withStats, 0), observableSteps, authContext) =>
          observableSteps
            .richPage(from, to, withTotal = true) {
              case o if withStats =>
                o.richObservableWithCustomRenderer(organisationSrv, observableStatsRenderer(authContext))(authContext)
                  .domainMap(ros => (ros._1, ros._2, None: Option[Either[RichCase, RichAlert]]))
              case o =>
                o.richObservable.domainMap(ro => (ro, JsObject.empty, None))
            }
        case (OutputParam(from, to, _, _), observableSteps, authContext) =>
          observableSteps.richPage(from, to, withTotal = true)(
            _.richObservableWithCustomRenderer(
              organisationSrv,
              o => o.project(_.by(_.`case`.richCase(authContext).option).by(_.alert.richAlert.option))
            )(authContext).domainMap(roc =>
              (
                roc._1,
                JsObject.empty,
                roc._2 match {
                  case (Some(c), _) => Some(Left(c))
                  case (_, Some(a)) => Some(Right(a))
                  case _            => None
                }
              )
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
    .property("keyword", UMapping.string)(
      _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
        .filter[String](IndexType.fulltext) {
          case (_, t, _, Right(p))   => searchSrv("Observable", p.getValue)(t)
          case (_, t, _, Left(true)) => t
          case (_, t, _, _)          => t.empty
        }
        .readonly
    )
    .property("status", UMapping.string)(
      _.select(_.constant("Ok"))
        .filter[String](IndexType.standard) {
          case (_, observables, _, Right(statusPredicate)) if statusPredicate.test("Ok") => observables
          case (_, observables, _, Right(_))                                             => observables.empty
          case (_, observables, _, Left(true))                                           => observables
          case (_, observables, _, Left(false))                                          => observables.empty
        }
        .readonly
    )
    .property("startDate", UMapping.date)(_.rename("_createdAt").readonly)
    .property("ioc", UMapping.boolean)(_.field.updatable)
    .property("sighted", UMapping.boolean)(_.field.updatable)
    .property("ignoreSimilarity", UMapping.boolean)(_.field.updatable)
    .property("tags", UMapping.string.sequence)(
      _.field
        .custom { (_, value, vertex, graph, authContext) =>
          observableSrv
            .get(vertex)(graph)
            .getOrFail("Observable")
            .flatMap(observable => observableSrv.updateTags(observable, value.toSet)(graph, authContext))
            .map(_ => Json.obj("tags" -> value))
        }
    )
    .property("message", UMapping.string)(_.field.updatable)
    .property("tlp", UMapping.int)(_.field.updatable)
    .property("dataType", UMapping.string)(_.field.custom { (_, value, vertex, graph, authContext) =>
      val observable = observableSrv.model.converter(vertex)
      for {
        currentDataType <- observableTypeSrv.getByName(observable.dataType)(graph).getOrFail("ObservableType")
        newDataType     <- observableTypeSrv.getByName(value)(graph).getOrFail("ObservableType")
        isSameType = currentDataType.isAttachment == newDataType.isAttachment
        _ <- if (isSameType) Success(()) else Failure(BadRequestError("Can not update dataType: isAttachment does not match"))
        _ <- observableSrv.updateType(observable, newDataType)(graph, authContext)
      } yield Json.obj("dataType" -> value)
    })
    .property("data", UMapping.string.optional)(_.field.readonly)
    .property("attachment.name", UMapping.string.optional)(_.select(_.attachments.value(_.name)).readonly)
    .property("attachment.hashes", UMapping.hash.sequence)(_.select(_.attachments.value(_.hashes)).readonly)
    .property("attachment.size", UMapping.long.optional)(_.select(_.attachments.value(_.size)).readonly)
    .property("attachment.contentType", UMapping.string.optional)(_.select(_.attachments.value(_.contentType)).readonly)
    .property("attachment.id", UMapping.string.optional)(_.rename("attachmentId").readonly)
    .property("relatedId", UMapping.entityId)(_.field.readonly)
    .build
}
