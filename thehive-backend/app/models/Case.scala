package models

import java.util.Date
import javax.inject.{ Inject, Provider, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.math.BigDecimal.{ int2bigDecimal, long2bigDecimal }

import play.api.Logger
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import models.JsonFormat.{ caseImpactStatusFormat, caseResolutionStatusFormat, caseStatusFormat }
import services.{ AuditedModel, CaseSrv }

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.models.{ AttributeDef, BaseEntity, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F, AttributeOption ⇒ O }
import org.elastic4play.services.{ FindSrv, SequenceSrv }

object CaseStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Open, Resolved, Deleted = Value
}

object CaseResolutionStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Indeterminate, FalsePositive, TruePositive, Other, Duplicated = Value
}

object CaseImpactStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val NoImpact, WithImpact, NotApplicable = Value
}

trait CaseAttributes { _: AttributeDef ⇒
  val caseId: A[Long] = attribute("caseId", F.numberFmt, "Id of the case (auto-generated)", O.model)
  val title: A[String] = attribute("title", F.textFmt, "Title of the case")
  val description: A[String] = attribute("description", F.textFmt, "Description of the case")
  val severity: A[Long] = attribute("severity", SeverityAttributeFormat, "Severity if the case is an incident (0-3)", 2L)
  val owner: A[String] = attribute("owner", F.userFmt, "Owner of the case")
  val startDate: A[Date] = attribute("startDate", F.dateFmt, "Creation date", new Date)
  val endDate: A[Option[Date]] = optionalAttribute("endDate", F.dateFmt, "Resolution date")
  val tags: A[Seq[String]] = multiAttribute("tags", F.stringFmt, "Case tags")
  val flag: A[Boolean] = attribute("flag", F.booleanFmt, "Flag of the case", false)
  val tlp: A[Long] = attribute("tlp", TlpAttributeFormat, "TLP level", 2L)
  val status: A[CaseStatus.Value] = attribute("status", F.enumFmt(CaseStatus), "Status of the case", CaseStatus.Open)
  val metrics: A[JsValue] = attribute("metrics", F.metricsFmt, "List of metrics", JsObject(Nil))
  val resolutionStatus: A[Option[CaseResolutionStatus.Value]] = optionalAttribute("resolutionStatus", F.enumFmt(CaseResolutionStatus), "Resolution status of the case")
  val impactStatus: A[Option[CaseImpactStatus.Value]] = optionalAttribute("impactStatus", F.enumFmt(CaseImpactStatus), "Impact status of the case")
  val summary: A[Option[String]] = optionalAttribute("summary", F.textFmt, "Summary of the case, to be provided when closing a case")
  val mergeInto: A[Option[String]] = optionalAttribute("mergeInto", F.stringFmt, "Id of the case created by the merge")
  val mergeFrom: A[Seq[String]] = multiAttribute("mergeFrom", F.stringFmt, "Id of the cases merged")
  val customFields: A[JsValue] = attribute("customFields", F.customFields, "Custom fields", JsObject(Nil))
}

@Singleton
class CaseModel @Inject() (
    artifactModel: Provider[ArtifactModel],
    taskModel: Provider[TaskModel],
    caseSrv: Provider[CaseSrv],
    alertModel: Provider[AlertModel],
    sequenceSrv: SequenceSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext) extends ModelDef[CaseModel, Case]("case", "Case", "/case") with CaseAttributes with AuditedModel { caseModel ⇒

  private[CaseModel] lazy val logger = Logger(getClass)
  override val defaultSortBy = Seq("-startDate")
  override val removeAttribute: JsObject = Json.obj("status" → CaseStatus.Deleted)

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = {
    sequenceSrv("case").map { caseId ⇒
      attrs + ("caseId" → JsNumber(caseId))
    }
  }

  override def updateHook(entity: BaseEntity, updateAttrs: JsObject): Future[JsObject] = Future.successful {
    (updateAttrs \ "status").asOpt[CaseStatus.Type] match {
      case Some(CaseStatus.Resolved) if !updateAttrs.keys.contains("endDate") ⇒
        updateAttrs +
          ("endDate" → Json.toJson(new Date)) +
          ("flag" → JsBoolean(false))
      case Some(CaseStatus.Open) ⇒
        updateAttrs + ("endDate" → JsArray(Nil))
      case _ ⇒
        updateAttrs
    }
  }

  private[models] def buildArtifactStats(caze: Case): Future[JsObject] = {
    import org.elastic4play.services.QueryDSL._
    findSrv(
      artifactModel.get,
      and(
        parent("case", withId(caze.id)),
        "status" ~= "Ok"),
      selectCount)
      .map { artifactStats ⇒
        Json.obj("artifacts" → artifactStats)
      }
  }

  private[models] def buildTaskStats(caze: Case): Future[JsObject] = {
    import org.elastic4play.services.QueryDSL._
    findSrv(
      taskModel.get,
      and(
        parent("case", withId(caze.id)),
        "status" in ("Waiting", "InProgress", "Completed")),
      groupByField("status", selectCount))
      .map { taskStatsJson ⇒
        val (taskCount, taskStats) = taskStatsJson.value.foldLeft((0L, JsObject(Nil))) {
          case ((total, s), (key, value)) ⇒
            val count = (value \ "count").as[Long]
            (total + count, s + (key → JsNumber(count)))
        }
        Json.obj("tasks" → (taskStats + ("total" → JsNumber(taskCount))))
      }
  }

  private[models] def buildMergeIntoStats(caze: Case): Future[JsObject] = {
    caze.mergeInto()
      .fold(Future.successful(Json.obj())) { mergeCaseId ⇒
        caseSrv.get.get(mergeCaseId).map { c ⇒
          Json.obj("mergeInto" → Json.obj(
            "caseId" → c.caseId(),
            "title" → c.title()))
        }
      }
  }

  private[models] def buildMergeFromStats(caze: Case): Future[JsObject] = {
    Future
      .traverse(caze.mergeFrom()) { id ⇒
        caseSrv.get.get(id).map { c ⇒
          Json.obj(
            "caseId" → c.caseId(),
            "title" → c.title())
        }
      }
      .map {
        case mf if mf.nonEmpty ⇒ Json.obj("mergeFrom" → mf)
        case _                 ⇒ Json.obj()
      }
  }

  private[models] def buildAlertStats(caze: Case): Future[JsObject] = {
    import org.elastic4play.services.QueryDSL._
    findSrv(
      alertModel.get,
      "case" ~= caze.id,
      groupByField("type", groupByField("source", selectCount)))
      .map { alertStatsJson ⇒
        val alertStats = for {
          (tpe, JsObject(srcStats)) ← alertStatsJson.value
          src ← srcStats.keys
        } yield Json.obj("type" → tpe, "source" → src)
        Json.obj("alerts" → alertStats)
      }
  }

  override def getStats(entity: BaseEntity): Future[JsObject] = {

    entity match {
      case caze: Case ⇒
        for {
          taskStats ← buildTaskStats(caze)
          artifactStats ← buildArtifactStats(caze)
          alertStats ← buildAlertStats(caze)
          mergeIntoStats ← buildMergeIntoStats(caze)
          mergeFromStats ← buildMergeFromStats(caze)
        } yield taskStats ++ artifactStats ++ alertStats ++ mergeIntoStats ++ mergeFromStats
      case other ⇒
        logger.warn(s"Request caseStats from a non-case entity ?! ${other.getClass}:$other")
        Future.successful(Json.obj())
    }
  }

  override val computedMetrics = Map(
    "handlingDurationInSeconds" → "(doc['endDate'].value - doc['startDate'].value) / 1000",
    "handlingDurationInDays" → "(doc['endDate'].value - doc['startDate'].value) / 3600000")
}

class Case(model: CaseModel, attributes: JsObject) extends EntityDef[CaseModel, Case](model, attributes) with CaseAttributes
