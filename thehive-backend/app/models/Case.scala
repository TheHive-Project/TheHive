package models

import java.util.Date

import javax.inject.{ Inject, Provider, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.math.BigDecimal.{ int2bigDecimal, long2bigDecimal }

import play.api.Logger
import play.api.libs.json.{ JsArray, JsBoolean, JsNumber, JsObject }
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.models.{ AttributeDef, AttributeFormat ⇒ F, AttributeOption ⇒ O, BaseEntity, EntityDef, HiveEnumeration, ModelDef }
import org.elastic4play.services.{ FindSrv, SequenceSrv }

import JsonFormat.{ caseImpactStatusFormat, caseResolutionStatusFormat, caseStatusFormat }
import services.{ AuditedModel, CaseSrv }

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
  val caseId = attribute("caseId", F.numberFmt, "Id of the case (auto-generated)", O.model)
  val title = attribute("title", F.textFmt, "Title of the case")
  val description = attribute("description", F.textFmt, "Description of the case")
  val severity = attribute("severity", F.numberFmt, "Severity if the case is an incident (0-5)", 3L)
  val owner = attribute("owner", F.stringFmt, "Owner of the case")
  val startDate = attribute("startDate", F.dateFmt, "Creation date", new Date)
  val endDate = optionalAttribute("endDate", F.dateFmt, "Resolution date")
  val tags = multiAttribute("tags", F.stringFmt, "Case tags")
  val flag = attribute("flag", F.booleanFmt, "Flag of the case", false)
  val tlp = attribute("tlp", F.numberFmt, "TLP level", 2L)
  val status = attribute("status", F.enumFmt(CaseStatus), "Status of the case", CaseStatus.Open)
  val metrics = optionalAttribute("metrics", F.metricsFmt, "List of metrics")
  val resolutionStatus = optionalAttribute("resolutionStatus", F.enumFmt(CaseResolutionStatus), "Resolution status of the case")
  val impactStatus = optionalAttribute("impactStatus", F.enumFmt(CaseImpactStatus), "Impact status of the case")
  val summary = optionalAttribute("summary", F.textFmt, "Summary of the case, to be provided when closing a case")
  val mergeInto = optionalAttribute("mergeInto", F.stringFmt, "Id of the case created by the merge")
  val mergeFrom = multiAttribute("mergeFrom", F.stringFmt, "Id of the cases merged")
}

@Singleton
class CaseModel @Inject() (
    artifactModel: Provider[ArtifactModel],
    taskModel: Provider[TaskModel],
    caseSrv: Provider[CaseSrv],
    sequenceSrv: SequenceSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext) extends ModelDef[CaseModel, Case]("case") with CaseAttributes with AuditedModel { caseModel ⇒

  lazy val logger = Logger(getClass)
  override val defaultSortBy = Seq("-startDate")
  override val removeAttribute = Json.obj("status" → CaseStatus.Deleted)

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject) = {
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
        case mf if !mf.isEmpty ⇒ Json.obj("mergeFrom" → mf)
        case _                 ⇒ Json.obj()
      }
  }
  override def getStats(entity: BaseEntity): Future[JsObject] = {

    entity match {
      case caze: Case ⇒
        for {
          taskStats ← buildTaskStats(caze)
          artifactStats ← buildArtifactStats(caze)
          mergeIntoStats ← buildMergeIntoStats(caze)
          mergeFromStats ← buildMergeFromStats(caze)
        } yield taskStats ++ artifactStats ++ mergeIntoStats ++ mergeFromStats
      case other ⇒
        logger.warn(s"Request caseStats from a non-case entity ?! ${other.getClass}:$other")
        Future.successful(Json.obj())
    }
  }

  override val computedMetrics = Map(
    "handlingDuration" → "doc['endDate'].value - doc['startDate'].value")
}

class Case(model: CaseModel, attributes: JsObject) extends EntityDef[CaseModel, Case](model, attributes) with CaseAttributes
