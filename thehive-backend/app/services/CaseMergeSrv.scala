package services

import java.util.Date

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.math.BigDecimal.long2bigDecimal

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import play.api.libs.json.{ JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue }
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json

import org.elastic4play.controllers.{ AttachmentInputValue, Fields }
import org.elastic4play.models.BaseEntity
import org.elastic4play.services.AuthContext
import org.elastic4play.services.JsonFormat.log
import org.elastic4play.services.QueryDSL

import models.{ Artifact, ArtifactStatus, Case, CaseImpactStatus, CaseResolutionStatus, CaseStatus, JobStatus, Task }

@Singleton
class CaseMergeSrv @Inject() (caseSrv: CaseSrv,
                              taskSrv: TaskSrv,
                              logSrv: LogSrv,
                              artifactSrv: ArtifactSrv,
                              jobSrv: JobSrv,
                              implicit val ec: ExecutionContext,
                              implicit val mat: Materializer) {

  import QueryDSL._
  private[services] def concat[E](entities: Seq[E], sep: String, getId: E => Long, getStr: E => String) = {
    JsString(entities.map(e => s"#${getId(e)}:${getStr(e)}").mkString(sep))
  }

  private[services] def firstDate(dates: Seq[Date]) = Json.toJson(dates.min)

  private[services] def mergeResolutionStatus(cases: Seq[Case]) = {
    val resolutionStatus = cases
      .map(_.resolutionStatus())
      .reduce[Option[CaseResolutionStatus.Type]] {
        case (None, s)                                     => s
        case (s, None)                                     => s
        case (Some(CaseResolutionStatus.Other), s)         => s
        case (s, Some(CaseResolutionStatus.Other))         => s
        case (Some(CaseResolutionStatus.FalsePositive), s) => s
        case (s, Some(CaseResolutionStatus.FalsePositive)) => s
        case (Some(CaseResolutionStatus.Indeterminate), s) => s
        case (s, Some(CaseResolutionStatus.Indeterminate)) => s
        case (s, _)                                        => s //TruePositive
      }
    resolutionStatus.map(s => JsString(s.toString))
  }

  private[services] def mergeImpactStatus(cases: Seq[Case]) = {
    val impactStatus = cases
      .map(_.impactStatus())
      .reduce[Option[CaseImpactStatus.Type]] {
        case (None, s)                                 => s
        case (s, None)                                 => s
        case (Some(CaseImpactStatus.NotApplicable), s) => s
        case (s, Some(CaseImpactStatus.NotApplicable)) => s
        case (Some(CaseImpactStatus.NoImpact), s)      => s
        case (s, Some(CaseImpactStatus.NoImpact))      => s
        case (s, _)                                    => s // WithImpact
      }
    impactStatus.map(s => JsString(s.toString))
  }

  private[services] def mergeSummary(cases: Seq[Case]) = {
    val summary = cases
      .flatMap(c => c.summary().map(_ -> c.caseId()))
      .map {
        case (summary, caseId) => s"#$caseId:$summary"
      }
    if (summary.isEmpty)
      None
    else
      Some(JsString(summary.mkString(" / ")))
  }

  private[services] def mergeMetrics(cases: Seq[Case]): JsObject = {
    val metrics = for {
      caze <- cases
      metrics <- caze.metrics()
      metricsObject <- metrics.asOpt[JsObject]
    } yield metricsObject

    val mergedMetrics: Seq[(String, JsValue)] = metrics.flatMap(_.keys).distinct.map { key =>
      val metricValues = metrics.flatMap(m => (m \ key).asOpt[BigDecimal])
      if (metricValues.size != 1)
        key -> JsNull
      else
        key -> JsNumber(metricValues.head)
    }

    JsObject(mergedMetrics)
  }

  private[services] def baseFields(entity: BaseEntity): Fields = Fields(entity.attributes - "_id" - "_routing" - "_parent" - "_type" - "createdBy" - "createdAt" - "updatedBy" - "updatedAt" - "user")

  private[services] def mergeLogs(oldTask: Task, newTask: Task)(implicit authContext: AuthContext): Future[Done] = {
    logSrv.find("_parent" ~= oldTask.id, Some("all"), Nil)._1
      .mapAsyncUnordered(5) { log =>
        logSrv.create(newTask, baseFields(log))
      }
      .runWith(Sink.ignore)
  }

  private[services] def mergeTasksAndLogs(newCase: Case, cases: Seq[Case])(implicit authContext: AuthContext): Future[Done] = {
    taskSrv.find(or(cases.map("_parent" ~= _.id)), Some("all"), Nil)._1
      .mapAsyncUnordered(5) { task =>
        taskSrv.create(newCase, baseFields(task)).map(task -> _)
      }
      .flatMapConcat {
        case (oldTask, newTask) =>
          logSrv.find("_parent" ~= oldTask.id, Some("all"), Nil)._1
            .map(_ -> newTask)
      }
      .mapAsyncUnordered(5) {
        case (log, task) => logSrv.create(task, baseFields(log))
      }
      .runWith(Sink.ignore)
  }

  private[services] def mergeArtifactStatus(artifacts: Seq[Artifact]) = {
    val status = artifacts
      .map(_.status())
      .reduce[ArtifactStatus.Type] {
        case (ArtifactStatus.Deleted, s) => s
        case (s, _)                      => s
      }
      .toString
    JsString(status)
  }

  private[services] def mergeJobs(newArtifact: Artifact, artifacts: Seq[Artifact])(implicit authContext: AuthContext): Future[Done] = {
    jobSrv.find(and(or(artifacts.map("_parent" ~= _.id)), "status" ~= JobStatus.Success), Some("all"), Nil)._1
      .mapAsyncUnordered(5) { job =>
        jobSrv.create(newArtifact, baseFields(job))
      }
      .runWith(Sink.ignore)
  }

  private[services] def mergeArtifactsAndJobs(newCase: Case, cases: Seq[Case])(implicit authContext: AuthContext): Future[Done] = {
    val caseMap = cases.map(c => c.id -> c).toMap
    val caseFilter = or(cases.map("_parent" ~= _.id))
    // Find artifacts hold by cases
    artifactSrv.find(caseFilter, Some("all"), Nil)._1
      .map { artifact =>
        // For each artifact find similar artifacts
        val dataFilter = artifact.data().map("data" ~= _) orElse artifact.attachment().map("attachment.id" ~= _.id)
        val filter = and(caseFilter,
          "status" ~= "Ok",
          "dataType" ~= artifact.dataType(),
          dataFilter.get)
        artifactSrv.find(filter, Some("all"), Nil)._1
          .runWith(Sink.seq)
          .flatMap { sameArtifacts =>
            // Same artifacts are merged
            val firstArtifact = sameArtifacts.head
            val fields = firstArtifact.attachment().fold(Fields.empty) { a =>
              Fields.empty.set("attachment", AttachmentInputValue(a.name, a.hashes, a.size, a.contentType, a.id))
            }
              .set("data", firstArtifact.data().map(JsString))
              .set("dataType", firstArtifact.dataType())
              .set("message", concat[Artifact](sameArtifacts, "\n  \n", a => caseMap(a.parentId.get).caseId(), _.message()))
              .set("startDate", firstDate(sameArtifacts.map(_.startDate())))
              .set("tlp", JsNumber(sameArtifacts.map(_.tlp()).min))
              .set("tags", JsArray(sameArtifacts.flatMap(_.tags()).map(JsString)))
              .set("ioc", JsBoolean(sameArtifacts.map(_.ioc()).reduce(_ || _)))
              .set("status", mergeArtifactStatus(sameArtifacts))
            // Merged artifact is created under new case
            artifactSrv
              .create(newCase, fields)
              // Then jobs are imported
              .flatMap { newArtifact =>
                mergeJobs(newArtifact, sameArtifacts)
              }
              // Errors are logged and ignored (probably document already exists)
              .recover {
                case error =>
                  log.warn("Artifact creation fail", error)
                  Done
              }
          }
      }
      .runWith(Sink.ignore)
  }

  private[services] def mergeCases(cases: Seq[Case])(implicit authContext: AuthContext): Future[Case] = {
    val fields = Fields.empty
      .set("title", concat[Case](cases, " / ", _.caseId(), _.title()))
      .set("description", concat[Case](cases, "\n  \n", _.caseId(), _.description()))
      .set("severity", JsNumber(cases.map(_.severity()).max))
      .set("startDate", firstDate(cases.map(_.startDate())))
      .set("tags", JsArray(cases.flatMap(_.tags()).distinct.map(JsString)))
      .set("flag", JsBoolean(cases.map(_.flag()).reduce(_ || _)))
      .set("tlp", JsNumber(cases.map(_.tlp()).max))
      .set("status", JsString(CaseStatus.Open.toString))
      .set("metrics", mergeMetrics(cases))
      .set("isIncident", JsBoolean(cases.map(_.isIncident()).reduce(_ || _)))
      .set("resolutionStatus", mergeResolutionStatus(cases))
      .set("impactStatus", mergeImpactStatus(cases))
      .set("summary", mergeSummary(cases))
    caseSrv.create(fields)
  }

  def merge(caseIds: String*)(implicit authContext: AuthContext): Future[Case] = {
    for {
      cases <- Future.sequence(caseIds.map(caseSrv.get))
      newCase <- mergeCases(cases)
      _ <- mergeTasksAndLogs(newCase, cases)
      _ <- mergeArtifactsAndJobs(newCase, cases)
    } yield newCase
  }
}