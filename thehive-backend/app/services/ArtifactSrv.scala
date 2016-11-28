package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Try }

import play.api.libs.json.JsValue.jsValueToJsLookup

import org.elastic4play.CreateError
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ Agg, AuthContext, CreateSrv, DeleteSrv, FieldsSrv, FindSrv, GetSrv, QueryDSL, QueryDef, UpdateSrv }

import models.{ Artifact, ArtifactModel, ArtifactStatus, Case, CaseModel, JobModel }
import org.elastic4play.utils.{ RichFuture, RichOr }
import models.CaseStatus
import models.CaseResolutionStatus

@Singleton
class ArtifactSrv @Inject() (
    artifactModel: ArtifactModel,
    caseModel: CaseModel,
    jobModel: JobModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    fieldsSrv: FieldsSrv,
    implicit val ec: ExecutionContext) {

  def create(caseId: String, fields: Fields)(implicit authContext: AuthContext): Future[Artifact] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze => create(caze, fields) }

  def create(caze: Case, fields: Fields)(implicit authContext: AuthContext): Future[Artifact] = {
    createSrv[ArtifactModel, Artifact, Case](artifactModel, caze, fields)
      .fallbackTo(updateIfDeleted(caze, fields)) // maybe the artifact already exists. If so, search it and update it
  }

  private def updateIfDeleted(caze: Case, fields: Fields)(implicit authContext: AuthContext): Future[Artifact] = {
    fieldsSrv.parse(fields, artifactModel).toFuture.flatMap { attrs =>
      val updatedArtifact = for {
        id <- artifactModel.computeId(Some(caze), attrs)
        artifact <- getSrv[ArtifactModel, Artifact](artifactModel, id)
        if artifact.status() == ArtifactStatus.Deleted
        updatedArtifact <- updateSrv[ArtifactModel, Artifact](artifactModel, artifact.id, fields.unset("data").unset("dataType").unset("attachment").set("status", "Ok"))
      } yield updatedArtifact
      updatedArtifact.recoverWith {
        case _ => Future.failed(CreateError(Some("CONFLICT"), "Artifact already exists", attrs))
      }
    }
  }

  def create(caseId: String, fieldSet: Seq[Fields])(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze => create(caze, fieldSet) }

  def create(caze: Case, fieldSet: Seq[Fields])(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] =
    createSrv[ArtifactModel, Artifact, Case](artifactModel, fieldSet.map(caze -> _))
      .flatMap {
        // if there is failure
        case t if t.exists(_.isFailure) =>
          Future.sequence {
            // map operation result with related fields
            t.zip(fieldSet)
              .map {
                // for each failure
                case (Failure(_), fields) => updateIfDeleted(caze, fields).toTry
                case (s, fields)          => Future.successful(s)
              }
          }
        case t => Future.successful(t)
      }

  def get(id: String, fields: Option[Seq[String]] = None)(implicit authContext: AuthContext) = {
    val fieldAttribute = fields.map { _.flatMap(f => artifactModel.attributes.find(_.name == f)) }
    getSrv[ArtifactModel, Artifact](artifactModel, id, fieldAttribute)
  }

  def update(id: String, fields: Fields)(implicit authContext: AuthContext) =
    updateSrv[ArtifactModel, Artifact](artifactModel, id, fields)

  def bulkUpdate(ids: Seq[String], fields: Fields)(implicit authContext: AuthContext) = {
    updateSrv.apply[ArtifactModel, Artifact](artifactModel, ids, fields)
  }

  def delete(id: String)(implicit Context: AuthContext) =
    deleteSrv[ArtifactModel, Artifact](artifactModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]) = {
    findSrv[ArtifactModel, Artifact](artifactModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]) = findSrv(artifactModel, queryDef, aggs: _*)

  def isSeen(artifact: Artifact): Future[Long] = {
    import org.elastic4play.services.QueryDSL._
    findSrv(artifactModel, similarArtifactFilter(artifact), selectCount).map { stats =>
      (stats \ "count").asOpt[Long].getOrElse(1L)
    }
  }

  def findSimilar(artifact: Artifact, range: Option[String], sortBy: Seq[String]) =
    find(similarArtifactFilter(artifact), range, sortBy)

  private[services] def similarArtifactFilter(artifact: Artifact): QueryDef = {
    import org.elastic4play.services.QueryDSL._
    val dataType = artifact.dataType()
    artifact.data() match {
      // artifact is an hash
      case Some(d) if dataType == "hash" =>
        and(
          parent("case", and(not(withId(artifact.parentId.get)), "status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          or(
            and(
              "data" ~= d,
              "dataType" ~= dataType),
            "attachment.hashes" ~= d))
      // artifact contains data but not an hash
      case Some(d) =>
        and(
          parent("case", and(not(withId(artifact.parentId.get)), "status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          "data" ~= d,
          "dataType" ~= dataType)
      // artifact is a file
      case None =>
        val hashes = artifact.attachment().toSeq.flatMap(_.hashes).map(_.toString)
        val hashFilter = hashes.map { h => "attachment.hashes" ~= h }
        and(
          parent("case", and(not(withId(artifact.parentId.get)), "status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          or(
            hashFilter :+
              and(
                "dataType" ~= "hash",
                or(hashes.map { h => "data" ~= h }))))
    }
  }
}
