package services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue.jsValueToJsLookup

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import models.{CaseResolutionStatus, CaseStatus, _}

import org.elastic4play.ConflictError
import org.elastic4play.controllers.Fields
import org.elastic4play.database.{DBRemove, ModifyConfig}
import org.elastic4play.services._
import org.elastic4play.utils.{RichFuture, RichOr}

case class RemoveJobsOf(artifactId: String) extends EventMessage

@Singleton
class ArtifactSrv @Inject()(
    artifactModel: ArtifactModel,
    caseModel: CaseModel,
    auditSrv: AuditSrv,
    eventSrv: EventSrv,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    fieldsSrv: FieldsSrv,
    dbRemove: DBRemove,
    implicit val mat: Materializer
) {

  private[ArtifactSrv] lazy val logger = Logger(getClass)

  def create(caseId: String, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Artifact] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze =>
        create(caze, fields)
      }

  def create(caze: Case, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Artifact] =
    createSrv[ArtifactModel, Artifact, Case](artifactModel, caze, fields)
      .recoverWith {
        case _: ConflictError => updateIfDeleted(caze, fields) // if the artifact already exists, search it and update it
      }

  private def updateIfDeleted(caze: Case, fields: Fields, modifyConfig: ModifyConfig = ModifyConfig.default)(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Future[Artifact] =
    fieldsSrv.parse(fields, artifactModel).toFuture.flatMap { attrs =>
      val updatedArtifact = for {
        id       <- artifactModel.computeId(caze, attrs)
        artifact <- getSrv[ArtifactModel, Artifact](artifactModel, id)
        if artifact.status() == ArtifactStatus.Deleted
        updatedArtifact <- updateSrv[ArtifactModel, Artifact](
          artifactModel,
          artifact.id,
          fields
            .unset("data")
            .unset("dataType")
            .set("status", "Ok"),
          modifyConfig
        )
      } yield updatedArtifact
      updatedArtifact.recoverWith {
        case _ => Future.failed(ConflictError("Artifact already exists", attrs))
      }
    }

  def create(caseId: String, fieldSet: Seq[Fields])(implicit authContext: AuthContext, ec: ExecutionContext): Future[Seq[Try[Artifact]]] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze =>
        create(caze, fieldSet)
      }

  def create(caze: Case, fieldSet: Seq[Fields])(implicit authContext: AuthContext, ec: ExecutionContext): Future[Seq[Try[Artifact]]] =
    createSrv[ArtifactModel, Artifact, Case](artifactModel, fieldSet.map(caze -> _))
      .flatMap {
        // if there is failure
        case t if t.exists(_.isFailure) =>
          Future.traverse(t.zip(fieldSet)) {
            case (Failure(ConflictError(_, _)), fields) => updateIfDeleted(caze, fields).toTry
            case (r, _)                                 => Future.successful(r)
          }
        case t => Future.successful(t)
      }

  def get(id: String)(implicit ec: ExecutionContext): Future[Artifact] =
    getSrv[ArtifactModel, Artifact](artifactModel, id)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig = ModifyConfig.default)(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Future[Artifact] =
    updateSrv[ArtifactModel, Artifact](artifactModel, id, fields, modifyConfig)

  def bulkUpdate(ids: Seq[String], fields: Fields, modifyConfig: ModifyConfig = ModifyConfig.default)(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Future[Seq[Try[Artifact]]] =
    updateSrv.apply[ArtifactModel, Artifact](artifactModel, ids, fields, modifyConfig)

  def delete(id: String)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Artifact] =
    deleteSrv[ArtifactModel, Artifact](artifactModel, id)

  def realDelete(artifact: Artifact)(implicit ec: ExecutionContext): Future[Unit] =
    for {
      _ <- auditSrv
        .findFor(artifact, Some("all"), Nil)
        ._1
        .mapAsync(1)(auditSrv.realDelete)
        .runWith(Sink.ignore)
      _ = eventSrv.publish(RemoveJobsOf(artifact.id))
      _ <- dbRemove(artifact)
    } yield ()

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String])(implicit ec: ExecutionContext): (Source[Artifact, NotUsed], Future[Long]) =
    findSrv[ArtifactModel, Artifact](artifactModel, queryDef, range, sortBy)

  def stats(queryDef: QueryDef, aggs: Seq[Agg])(implicit ec: ExecutionContext): Future[JsObject] = findSrv(artifactModel, queryDef, aggs: _*)

  def isSeen(artifact: Artifact)(implicit ec: ExecutionContext): Future[Long] = {
    import org.elastic4play.services.QueryDSL._
    findSrv(artifactModel, similarArtifactFilter(artifact), selectCount).map { stats =>
      (stats \ "count").asOpt[Long].getOrElse(1L)
    }
  }

  def findSimilar(artifact: Artifact, range: Option[String], sortBy: Seq[String])(
      implicit ec: ExecutionContext
  ): (Source[Artifact, NotUsed], Future[Long]) =
    find(similarArtifactFilter(artifact), range, sortBy)

  def findSimilar(
      dataType: String,
      data: Either[String, Attachment],
      filter: Option[QueryDef],
      range: Option[String],
      sortBy: Seq[String]
  )(implicit ec: ExecutionContext): (Source[Artifact, NotUsed], Future[Long]) =
    find(similarArtifactFilter(dataType, data, filter.getOrElse(org.elastic4play.services.QueryDSL.any)), range, sortBy)

  private[services] def similarArtifactFilter(artifact: Artifact): QueryDef = {
    import org.elastic4play.services.QueryDSL._
    val data = (artifact.data(), artifact.attachment()) match {
      case (Some(_data), None)      => Left(_data)
      case (None, Some(attachment)) => Right(attachment)
      case _                        => sys.error("")
    }
    val filter = parent("case", not(withId(artifact.parentId.get)))
    similarArtifactFilter(artifact.dataType(), data, filter)
  }

  private[services] def similarArtifactFilter(dataType: String, data: Either[String, Attachment], filter: QueryDef): QueryDef = {
    import org.elastic4play.services.QueryDSL._
    data match {
      // artifact is an hash
      case Left(d) if dataType == "hash" =>
        and(
          filter,
          parent("case", and("status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          or(and("data" ~= d, "dataType" ~= dataType), "attachment.hashes" ~= d)
        )
      // artifact contains data but not an hash
      case Left(d) =>
        and(
          filter,
          parent("case", and("status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          "data" ~= d,
          "dataType" ~= dataType
        )
      // artifact is a file
      case Right(attachment) =>
        val hashes = attachment.hashes.map(_.toString)
        val hashFilter = hashes.map { h =>
          "attachment.hashes" ~= h
        }
        and(
          filter,
          parent("case", and("status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          or(
            hashFilter :+
              and("dataType" ~= "hash", or(hashes.map { h =>
                "data" ~= h
              }))
          )
        )
    }
  }
}
