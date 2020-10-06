package services

import java.util.Date
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigDecimal.long2bigDecimal
import scala.util.Failure

import play.api.Logger
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import models._

import org.elastic4play.controllers.{AttachmentInputValue, Fields}
import org.elastic4play.models.BaseEntity
import org.elastic4play.services.{AuthContext, EventMessage, EventSrv}

case class MergeArtifact(newArtifact: Artifact, artifacts: Seq[Artifact], authContext: AuthContext) extends EventMessage

@Singleton
class CaseMergeSrv @Inject()(
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    logSrv: LogSrv,
    artifactSrv: ArtifactSrv,
    eventSrv: EventSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  private[CaseMergeSrv] lazy val logger = Logger(getClass)

  import org.elastic4play.services.QueryDSL._

  private[services] def concatOpt[E](entities: Seq[E], sep: String, getId: E ⇒ Long, getStr: E ⇒ Option[String]) =
    JsString(entities.flatMap(e ⇒ getStr(e).map(s ⇒ s"#${getId(e)}:$s")).mkString(sep))

  private[services] def concat[E](entities: Seq[E], sep: String, getId: E ⇒ Long, getStr: E ⇒ String) =
    JsString(entities.map(e ⇒ s"#${getId(e)}:${getStr(e)}").mkString(sep))

  private[services] def concatCaseDescription(cases: Seq[Case]) = {
    val str = cases
      .filterNot(_.description().trim.isEmpty)
      .map { caze ⇒
        s"#### ${caze.title()} ([#${caze.caseId()}](#/case/${caze.id}/details))\n\n${caze.description()}"
      }
      .mkString("\n  \n")
    JsString(str)
  }

  private[services] def firstDate(dates: Seq[Date]) = Json.toJson(dates.min)

  private[services] def mergeResolutionStatus(cases: Seq[Case]) = {
    val resolutionStatus = cases
      .map(_.resolutionStatus())
      .reduce[Option[CaseResolutionStatus.Type]] {
        case (None, s)                                     ⇒ s
        case (s, None)                                     ⇒ s
        case (Some(CaseResolutionStatus.Other), s)         ⇒ s
        case (s, Some(CaseResolutionStatus.Other))         ⇒ s
        case (Some(CaseResolutionStatus.FalsePositive), s) ⇒ s
        case (s, Some(CaseResolutionStatus.FalsePositive)) ⇒ s
        case (Some(CaseResolutionStatus.Indeterminate), s) ⇒ s
        case (s, Some(CaseResolutionStatus.Indeterminate)) ⇒ s
        case (s, _)                                        ⇒ s //TruePositive
      }
    resolutionStatus.map(s ⇒ JsString(s.toString))
  }

  private[services] def mergeImpactStatus(cases: Seq[Case]) = {
    val impactStatus = cases
      .map(_.impactStatus())
      .reduce[Option[CaseImpactStatus.Type]] {
        case (None, s)                                 ⇒ s
        case (s, None)                                 ⇒ s
        case (Some(CaseImpactStatus.NotApplicable), s) ⇒ s
        case (s, Some(CaseImpactStatus.NotApplicable)) ⇒ s
        case (Some(CaseImpactStatus.NoImpact), s)      ⇒ s
        case (s, Some(CaseImpactStatus.NoImpact))      ⇒ s
        case (s, _)                                    ⇒ s // WithImpact
      }
    impactStatus.map(s ⇒ JsString(s.toString))
  }

  private[services] def mergeSummary(cases: Seq[Case]) = {
    val summary = cases
      .flatMap(c ⇒ c.summary().map(_ → c.caseId()))
      .map {
        case (_summary, caseId) ⇒ s"#$caseId:${_summary}"
      }
    if (summary.isEmpty)
      None
    else
      Some(JsString(summary.mkString(" / ")))
  }

  private[services] def mergeMetrics(cases: Seq[Case]): JsObject = {
    val metrics = for {
      caze          ← cases
      metricsObject ← caze.metrics().asOpt[JsObject]
    } yield metricsObject

    val mergedMetrics: Seq[(String, JsValue)] = metrics.flatMap(_.keys).distinct.map { key ⇒
      val metricValues = metrics.flatMap(m ⇒ (m \ key).asOpt[BigDecimal])
      if (metricValues.lengthCompare(1) != 0)
        key → JsNull
      else
        key → JsNumber(metricValues.head)
    }

    JsObject(mergedMetrics)
  }

  private[services] def mergeCustomFields(cases: Seq[Case]): JsObject = {
    val customFields = for {
      caze               ← cases
      customFieldsObject ← caze.customFields().asOpt[JsObject]
    } yield customFieldsObject

    val mergedCustomFieldsObject: Seq[(String, JsValue)] = customFields.flatMap(_.keys).distinct.flatMap { key ⇒
      val customFieldsValues = customFields.flatMap(cf ⇒ (cf \ key).asOpt[JsObject]).distinct
      if (customFieldsValues.lengthCompare(1) != 0)
        None
      else
        Some(key → customFieldsValues.head)
    }

    JsObject(mergedCustomFieldsObject)
  }

  private[services] def baseFields(entity: BaseEntity): Fields =
    Fields(entity.attributes - "_id" - "_routing" - "_parent" - "_type" - "_seqNo" - "_primaryTerm"  - "createdBy" - "createdAt" - "updatedBy" - "updatedAt" - "user")

  private[services] def mergeLogs(oldTask: Task, newTask: Task)(implicit authContext: AuthContext): Future[Done] =
    logSrv
      .find(withParent("case_task", oldTask.id), Some("all"), Nil)
      ._1
      .mapAsyncUnordered(5) { log ⇒
        logSrv.create(newTask, baseFields(log))
      }
      .runWith(Sink.ignore)

  private[services] def mergeTasksAndLogs(newCase: Case, cases: Seq[Case])(implicit authContext: AuthContext): Future[Done] = {
    val (tasks, futureTaskCount) = taskSrv.find(
      and(parent("case", withId(cases.map(_.id): _*)), "status" ~!= TaskStatus.Cancel, "status" ~!= TaskStatus.Waiting),
      Some("all"),
      Nil
    )

    futureTaskCount.foreach(count ⇒ logger.info(s"Creating $count task(s):"))

    tasks
      .mapAsyncUnordered(5) { task ⇒
        taskSrv.create(newCase, baseFields(task)).map(task → _)
      }
      .flatMapConcat {
        case (oldTask, newTask) ⇒
          logger.info(s"\ttask : ${oldTask.id} → ${newTask.id} : ${newTask.title()}")
          val (logs, futureLogCount) = logSrv.find(and(parent("case_task", withId(oldTask.id)), "status" ~!= LogStatus.Deleted), Some("all"), Nil)
          futureLogCount.foreach { count ⇒
            logger.info(s"Creating $count log(s) in task ${newTask.id}")
          }
          logs.map(_ → newTask)
      }
      .mapAsyncUnordered(5) {
        case (log, task) ⇒
          val fields = log.attachment().fold(baseFields(log)) { a ⇒
            baseFields(log).set("attachment", AttachmentInputValue(a.name, a.hashes, a.size, a.contentType, a.id))
          }
          logSrv.create(task, fields)
      }
      .runWith(Sink.ignore)
      .andThen {
        case _ ⇒
          taskSrv
            .find(and(parent("case", withId(cases.map(_.id): _*)), "status" ~= TaskStatus.Waiting), Some("all"), Nil)
            ._1
            .fold(Seq.empty[Task]) {
              case (uniqueTasks, task) if !uniqueTasks.exists(_.title() == task.title()) ⇒
                uniqueTasks :+ task
              case (uniqueTasks, _) ⇒ uniqueTasks
            }
            .map(_.map(baseFields))
            .mapAsyncUnordered(5) { tasksFields ⇒
              taskSrv.create(newCase, tasksFields)
            }
            .mapConcat(_.toList)
            .map {
              case Failure(error) ⇒ logger.warn("Task creation fails", error)
              case _              ⇒
            }
            .runWith(Sink.ignore)
      }
  }

  private[services] def mergeArtifactStatus(artifacts: Seq[Artifact]) = {
    val status = artifacts
      .map(_.status())
      .reduce[ArtifactStatus.Type] {
        case (ArtifactStatus.Deleted, s) ⇒ s
        case (s, _)                      ⇒ s
      }
      .toString
    JsString(status)
  }

  private[services] def mergeArtifactsAndJobs(newCase: Case, cases: Seq[Case])(implicit authContext: AuthContext): Future[Done] = {
    val caseMap    = cases.map(c ⇒ c.id → c).toMap
    val caseFilter = and(parent("case", withId(cases.map(_.id): _*)), "status" ~= "Ok")
    // Find artifacts hold by cases
    val (artifacts, futureArtifactCount) = artifactSrv.find(caseFilter, Some("all"), Nil)
    futureArtifactCount.foreach { count ⇒
      logger.info(s"Found $count artifact(s) in merging cases")
    }
    artifacts
      .mapAsyncUnordered(5) { artifact ⇒
        // For each artifact find similar artifacts
        val dataFilter = artifact.data().map("data" ~= _) orElse artifact.attachment().map("attachment.id" ~= _.id)
        val filter     = and(caseFilter, "status" ~= "Ok", "dataType" ~= artifact.dataType(), dataFilter.get)

        val (artifacts, futureArtifactCount) = artifactSrv.find(filter, Some("all"), Nil)
        futureArtifactCount.foreach { count ⇒
          logger.debug(
            s"$count identical artifact(s) found (${artifact.dataType()}):${(artifact.data() orElse artifact.attachment().map(_.name)).get}"
          )
        }
        artifacts.runWith(Sink.seq)
      }
      .mapAsync(5) { sameArtifacts ⇒
        // Same artifacts are merged
        val firstArtifact = sameArtifacts.head
        val fields = firstArtifact
          .attachment()
          .fold(Fields.empty) { a ⇒
            Fields.empty.set("attachment", AttachmentInputValue(a.name, a.hashes, a.size, a.contentType, a.id))
          }
          .set("data", firstArtifact.data().map(JsString))
          .set("dataType", firstArtifact.dataType())
          .set("message", concatOpt[Artifact](sameArtifacts, "\n  \n", a ⇒ caseMap(a.parentId.get).caseId(), _.message()))
          .set("startDate", firstDate(sameArtifacts.map(_.startDate())))
          .set("tlp", JsNumber(sameArtifacts.map(_.tlp()).min))
          .set("tags", JsArray(sameArtifacts.flatMap(_.tags()).distinct.map(JsString)))
          .set("ioc", JsBoolean(sameArtifacts.map(_.ioc()).reduce(_ || _)))
          .set("status", mergeArtifactStatus(sameArtifacts))
          .set("sighted", JsBoolean(sameArtifacts.map(_.sighted()).reduce(_ || _)))
          .set("reports", sameArtifacts.map(a ⇒ Json.parse(a.reports()).as[JsObject]).reduce(_ deepMerge _).toString)
        // Merged artifact is created under new case
        artifactSrv
          .create(newCase, fields)
          .map(a ⇒ List(a → sameArtifacts))
          // Errors are logged and ignored (probably document already exists)
          .recover {
            case e ⇒
              logger.warn("Artifact creation fail", e)
              Nil
          }
      }
      .mapConcat(identity)
      .runForeach {
        case (newArtifact, sameArtifacts) ⇒
          // Then jobs are imported
          eventSrv.publish(MergeArtifact(newArtifact, sameArtifacts, authContext))
      }
  }

  private[services] def mergeCases(cases: Seq[Case])(implicit authContext: AuthContext): Future[Case] = {
    logger.info("Merging cases: " + cases.map(c ⇒ s"#${c.caseId()}:${c.title()}").mkString(" / "))
    val fields = Fields
      .empty
      .set("title", concat[Case](cases, " / ", _.caseId(), _.title()))
      .set("description", concatCaseDescription(cases))
      .set("severity", JsNumber(cases.map(_.severity()).max))
      .set("startDate", firstDate(cases.map(_.startDate())))
      .set("tags", JsArray(cases.flatMap(_.tags()).distinct.map(JsString)))
      .set("flag", JsBoolean(cases.map(_.flag()).reduce(_ || _)))
      .set("tlp", JsNumber(cases.map(_.tlp()).max))
      .set("status", JsString(CaseStatus.Open.toString))
      .set("metrics", mergeMetrics(cases))
      .set("customFields", mergeCustomFields(cases))
      .set("resolutionStatus", mergeResolutionStatus(cases))
      .set("impactStatus", mergeImpactStatus(cases))
      .set("summary", mergeSummary(cases))
      .set("mergeFrom", JsArray(cases.map(c ⇒ JsString(c.id))))
    caseSrv.create(fields)
  }

  def markCaseAsDuplicated(cases: Seq[Case], mergeCase: Case)(implicit authContext: AuthContext): Future[Done] =
    Future
      .traverse(cases) { caze ⇒
        val s       = s"Merge into : ${mergeCase.title()} ([#${mergeCase.caseId()}](#/case/${mergeCase.id}/details))"
        val summary = caze.summary().fold(s)(_ + s"\n\n$s")
        caseSrv.update(
          caze.id,
          Fields
            .empty
            .set("mergeInto", mergeCase.id)
            .set("status", CaseStatus.Resolved.toString)
            .set("resolutionStatus", CaseResolutionStatus.Duplicated.toString)
            .set("summary", summary)
        )
      }
      .map(_ ⇒ Done)
      .recover {
        case error ⇒
          logger.error("Case update fail", error)
          Done
      }

  def merge(caseIds: String*)(implicit authContext: AuthContext): Future[Case] =
    for {
      cases   ← Future.sequence(caseIds.map(caseSrv.get))
      newCase ← mergeCases(cases)
      _       ← mergeTasksAndLogs(newCase, cases)
      _       ← mergeArtifactsAndJobs(newCase, cases)
      _       ← markCaseAsDuplicated(cases, newCase)
      _       ← Future.traverse(cases)(caze ⇒ taskSrv.closeTasksOfCase(caze.id))
    } yield newCase
}
