package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.{ JsObject, JsValue }

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.{ Audit, AuditModel }

import org.elastic4play.services.{ AuxSrv, FindSrv, ModelSrv }
import org.elastic4play.utils.RichJson

@Singleton
class FlowSrv @Inject() (
    auditModel: AuditModel,
    modelSrv: ModelSrv,
    auxSrv: AuxSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext) {

  private[FlowSrv] lazy val logger = Logger(getClass)

  def apply(rootId: Option[String], count: Int): (Source[JsObject, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._

    val streamableEntities = modelSrv.list.collect {
      case m: AuditedModel if m.name != "user" ⇒ m.name
    }

    val filter = rootId match {
      case Some(rid) ⇒ and("rootId" ~= rid, "base" ~= true, "objectType" in (streamableEntities: _*))
      case None      ⇒ and("base" ~= true, "objectType" in (streamableEntities: _*))
    }
    val (src, total) = findSrv[AuditModel, Audit](auditModel, filter, Some(s"0-$count"), Seq("-startDate"))
    val entities = src.mapAsync(5) { audit ⇒
      val fSummary = findSrv(auditModel, and("requestId" ~= audit.requestId(), "objectType" in (streamableEntities: _*)), groupByField("objectType", groupByField("operation", selectCount)))
        .map { json ⇒
          json.collectValues {
            case objectType: JsObject ⇒ objectType.collectValues {
              case operation: JsObject ⇒ (operation \ "count").as[JsValue]
            }
          }
        }
      val fObj = auxSrv.apply(audit.objectType(), audit.objectId(), 10, withStats = false, removeUnaudited = true)

      for {
        summary ← fSummary
        obj ← fObj
      } yield JsObject(Seq("base" → (audit.toJson + ("object" → obj)), "summary" → summary))
    }
    (entities, total)
  }
}