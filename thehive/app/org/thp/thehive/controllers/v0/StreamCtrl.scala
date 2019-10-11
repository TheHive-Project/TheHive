package org.thp.thehive.controllers.v0

import java.lang.{Long => JLong}
import java.util.{Map => JMap}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.Success

import play.api.libs.json.{JsArray, JsNumber, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import akka.actor.ActorSystem
import gremlin.scala.{__, By, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services._

@Singleton
class StreamCtrl @Inject()(
    entryPoint: EntryPoint,
    streamSrv: StreamSrv,
    auditSrv: AuditSrv,
    val caseSrv: CaseSrv,
    val taskSrv: TaskSrv,
    val userSrv: UserSrv,
    implicit val db: Database,
    implicit val ec: ExecutionContext,
    system: ActorSystem
) extends AuditRenderer {

  def create: Action[AnyContent] =
    entryPoint("create stream")
      .auth { implicit request =>
        val streamId = streamSrv.create
        Success(Results.Ok(streamId))
      }

  def get(streamId: String): Action[AnyContent] =
    entryPoint("get stream").async { _ =>
      streamSrv
        .get(streamId)
        .map {
          case auditIds if auditIds.nonEmpty =>
            db.roTransaction { implicit graph =>
              val audits = auditSrv
                .getMainByIds(Order.desc, auditIds: _*)
                .richAuditWithCustomRenderer(auditRenderer)
                .toIterator
                .map {
                  case (audit, obj) =>
                    val summary = auditSrv
                      .initSteps
                      .has(Key("requestId"), P.eq(audit.requestId))
                      .has(Key("mainAction"), P.eq(false))
                      .groupBy(By(Key[String]("objectType")), By(__[Vertex].groupCount(By(Key[String]("action")))))
                      .headOption()
                      .fold(JsObject.empty) { m =>
                        JsObject(
                          m.asInstanceOf[JMap[String, JMap[String, JLong]]]
                            .asScala
                            .map {
                              case (o, ac) =>
                                objectTypeMapper(o) -> JsObject(ac.asScala.map { case (a, c) => actionToOperation(a) -> JsNumber(c.toLong) }.toSeq)
                            }
                        )
                      }
                    audit
                      .toJson
                      .as[JsObject]
                      .deepMerge(Json.obj("base" -> Json.obj("object" -> obj, "rootId" -> audit.context._id), "summary" -> summary))
                }
              Results.Ok(JsArray(audits.toSeq))
            }
          case _ => Results.Ok(JsArray.empty)
        }
    }

  def status: Action[AnyContent] = // TODO
    entryPoint("get stream") { _ =>
      Success(
        Results.Ok(
          Json.obj(
            "remaining" -> 3600,
            "warning"   -> false
          )
        )
      )
    }
}
