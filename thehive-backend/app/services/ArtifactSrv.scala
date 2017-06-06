package services

import javax.inject.{ Inject, Singleton }

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.{ CaseResolutionStatus, CaseStatus, _ }
import org.elastic4play.ConflictError
import org.elastic4play.controllers.Fields
import org.elastic4play.services._
import org.elastic4play.utils.{ RichFuture, RichOr }
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue.jsValueToJsLookup

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Try }

@Singleton
class ArtifactSrv @Inject() (
    artifactModel: ArtifactModel,
    caseModel: CaseModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    fieldsSrv: FieldsSrv,
    implicit val ec: ExecutionContext) {

  private[ArtifactSrv] lazy val logger = Logger(getClass)

  def create(caseId: String, fields: Fields)(implicit authContext: AuthContext): Future[Artifact] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze ⇒ create(caze, fields) }

  def create(caze: Case, fields: Fields)(implicit authContext: AuthContext): Future[Artifact] = {
    createSrv[ArtifactModel, Artifact, Case](artifactModel, caze, fields)
      .fallbackTo(updateIfDeleted(caze, fields)) // maybe the artifact already exists. If so, search it and update it
  }

  private def updateIfDeleted(caze: Case, fields: Fields)(implicit authContext: AuthContext): Future[Artifact] = {
    fieldsSrv.parse(fields, artifactModel).toFuture.flatMap { attrs ⇒
      val updatedArtifact = for {
        id ← artifactModel.computeId(Some(caze), attrs)
        artifact ← getSrv[ArtifactModel, Artifact](artifactModel, id)
        if artifact.status() == ArtifactStatus.Deleted
        updatedArtifact ← updateSrv[ArtifactModel, Artifact](artifactModel, artifact.id, fields.unset("data").unset("dataType").unset("attachment").set("status", "Ok"))
      } yield updatedArtifact
      updatedArtifact.recoverWith {
        case _ ⇒ Future.failed(ConflictError("Artifact already exists", attrs))
      }
    }
  }

  def create(caseId: String, fieldSet: Seq[Fields])(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] =
    getSrv[CaseModel, Case](caseModel, caseId)
      .flatMap { caze ⇒ create(caze, fieldSet) }

  def create(caze: Case, fieldSet: Seq[Fields])(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] =
    createSrv[ArtifactModel, Artifact, Case](artifactModel, fieldSet.map(caze → _))
      .flatMap {
        // if there is failure
        case t if t.exists(_.isFailure) ⇒
          Future.traverse(t.zip(fieldSet)) {
            case (Failure(ConflictError(_, _)), fields) ⇒ updateIfDeleted(caze, fields).toTry
            case (r, _)                                 ⇒ Future.successful(r)
          }
        case t ⇒ Future.successful(t)
      }

  def get(id: String, fields: Option[Seq[String]] = None)(implicit authContext: AuthContext): Future[Artifact] = {
    val fieldAttribute = fields.map { _.flatMap(f ⇒ artifactModel.attributes.find(_.name == f)) }
    getSrv[ArtifactModel, Artifact](artifactModel, id, fieldAttribute)
  }

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[Artifact] =
    updateSrv[ArtifactModel, Artifact](artifactModel, id, fields)

  def bulkUpdate(ids: Seq[String], fields: Fields)(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] = {
    updateSrv.apply[ArtifactModel, Artifact](artifactModel, ids, fields)
  }

  def delete(id: String)(implicit Context: AuthContext): Future[Artifact] =
    deleteSrv[ArtifactModel, Artifact](artifactModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Artifact, NotUsed], Future[Long]) = {
    findSrv[ArtifactModel, Artifact](artifactModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(artifactModel, queryDef, aggs: _*)

  def isSeen(artifact: Artifact): Future[Long] = {
    import org.elastic4play.services.QueryDSL._
    findSrv(artifactModel, similarArtifactFilter(artifact), selectCount).map { stats ⇒
      (stats \ "count").asOpt[Long].getOrElse(1L)
    }
  }

  def findSimilar(artifact: Artifact, range: Option[String], sortBy: Seq[String]): (Source[Artifact, NotUsed], Future[Long]) = {
    find(similarArtifactFilter(artifact), range, sortBy)
  }

  def findSimilar(dataType: String, data: Either[String, Attachment], filter: Option[QueryDef], range: Option[String], sortBy: Seq[String]): (Source[Artifact, NotUsed], Future[Long]) = {
    find(similarArtifactFilter(dataType, data, filter.getOrElse(org.elastic4play.services.QueryDSL.any)), range, sortBy)
  }

  private[services] def similarArtifactFilter(artifact: Artifact): QueryDef = {
    import org.elastic4play.services.QueryDSL._
    val data = (artifact.data(), artifact.attachment()) match {
      case (Some(_data), None)      ⇒ Left(_data)
      case (None, Some(attachment)) ⇒ Right(attachment)
      case _                        ⇒ sys.error("")
    }
    val filter = parent("case", not(withId(artifact.parentId.get)))
    similarArtifactFilter(artifact.dataType(), data, filter)
  }

  private[services] def similarArtifactFilter(dataType: String, data: Either[String, Attachment], filter: QueryDef): QueryDef = {
    import org.elastic4play.services.QueryDSL._
    data match {
      // artifact is an hash
      case Left(d) if dataType == "hash" ⇒
        and(
          filter,
          parent("case", and("status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          or(
            and(
              "data" ~= d,
              "dataType" ~= dataType),
            "attachment.hashes" ~= d))
      // artifact contains data but not an hash
      case Left(d) ⇒
        and(
          filter,
          parent("case", and("status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          "data" ~= d,
          "dataType" ~= dataType)
      // artifact is a file
      case Right(attachment) ⇒
        val hashes = attachment.hashes.map(_.toString)
        val hashFilter = hashes.map { h ⇒ "attachment.hashes" ~= h }
        and(
          filter,
          parent("case", and("status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
          "status" ~= "Ok",
          or(
            hashFilter :+
              and(
                "dataType" ~= "hash",
                or(hashes.map { h ⇒ "data" ~= h }))))
    }
  }
}
