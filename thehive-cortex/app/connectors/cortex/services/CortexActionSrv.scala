package connectors.cortex.services

import java.util.Date

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{ Failure, Success }

import play.api.Logger
import play.api.libs.json._

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import connectors.cortex.models._
import connectors.cortex.models.JsonFormat._
import javax.inject.{ Inject, Singleton }

import org.elastic4play.{ BadRequestError, MissingAttributeError, NotFoundError }
import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.models.BaseEntity
import org.elastic4play.services._

@Singleton
class CortexActionSrv @Inject() (
    cortexConfig: CortexConfig,
    actionModel: ActionModel,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    findSrv: FindSrv,
    updateSrv: UpdateSrv,
    auxSrv: AuxSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  lazy val logger = Logger(getClass)
  lazy val workerIdRegex: Regex = "(.*)-(.*)".r

  def getWorkerById(id: String): Future[Worker] = {
    id match {
      case workerIdRegex(instanceId, workerId) ⇒ cortexConfig.instances.find(_.name == instanceId).map(_.getWorkerById(workerId)).getOrElse(Future.failed(NotFoundError(s"Worker $id not found")))
      case _                                   ⇒ Future.firstCompletedOf(cortexConfig.instances.map(_.getWorkerById(id)))
    }
  }

  def askWorkersOnAllCortex(f: CortexClient ⇒ Future[Seq[Worker]]): Future[Seq[Worker]] = {
    Future
      .traverse(cortexConfig.instances) { cortex ⇒
        f(cortex).recover { case NonFatal(t) ⇒ logger.error("Request to Cortex fails", t); Nil }
      }
      .map(_.flatten)
  }

  def findWorkers(query: JsObject): Future[Seq[Worker]] = {
    askWorkersOnAllCortex(_.findWorkers(query))
      .map { workers ⇒
        workers
          .groupBy(_.name)
          .values
          .map(_.reduce(_ join _))
          .toSeq
      }
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Action, NotUsed], Future[Long]) = {
    findSrv[ActionModel, Action](actionModel, queryDef, range, sortBy)
  }

  def getAction(actionId: String): Future[Action] = {
    getSrv[ActionModel, Action](actionModel, actionId)
  }

  private def update(actionId: String, fields: Fields)(implicit authContext: AuthContext): Future[Action] =
    update(actionId, fields, ModifyConfig.default)

  private def update(actionId: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Action] =
    getAction(actionId).flatMap(action ⇒ update(action, fields, modifyConfig))

  private def update(action: Action, fields: Fields)(implicit authContext: AuthContext): Future[Action] =
    update(action, fields, ModifyConfig.default)

  private def update(action: Action, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Action] =
    updateSrv[Action](action, fields, modifyConfig)

  def updateActionWithCortex(actionId: String, cortexJobId: String, cortex: CortexClient)(implicit authContext: AuthContext): Unit = {
    logger.debug(s"Requesting status of job $cortexJobId in cortex ${cortex.name} in order to update action $actionId")
    cortex.waitReport(cortexJobId, 1.minute) andThen {
      case Success(j) ⇒
        val status = (j \ "status").asOpt[JobStatus.Type].getOrElse(JobStatus.Failure)
        if (status == JobStatus.InProgress || status == JobStatus.Waiting)
          updateActionWithCortex(actionId, cortexJobId, cortex)
        else {
          val report = (j \ "report").asOpt[JsObject].getOrElse(JsObject.empty).toString
          logger.debug(s"Job $cortexJobId in cortex ${cortex.name} has finished with status $status, updating action $actionId")
          getSrv[ActionModel, Action](actionModel, actionId)
            .flatMap { action ⇒
              val actionFields = Fields.empty
                .set("status", status.toString)
                .set("report", report)
                .set("endDate", Json.toJson(new Date))
              update(action, actionFields)
            }
            .onComplete {
              case Failure(e) ⇒ logger.error(s"Update action fails", e)
              case _          ⇒
            }
        }
      case Failure(CortexError(404, _, _)) ⇒
        logger.debug(s"The job $cortexJobId not found")
        val actionFields = Fields.empty
          .set("status", JobStatus.Failure.toString)
          .set("endDate", Json.toJson(new Date))
        update(actionId, actionFields)
      case _ ⇒
        logger.debug(s"Request of status of job $cortexJobId in cortex ${cortex.name} fails, restarting ...")
        updateActionWithCortex(actionId, cortexJobId, cortex)
    }
    ()
  }

  def executeAction(fields: Fields)(implicit authContext: AuthContext): Future[Action] = {
    def getWorker(cortexClient: CortexClient): Future[Worker] = {
      fields.getString("workerId").map(cortexClient.getWorkerById) orElse
        fields.getString("workerName").map(cortexClient.getWorkerByName) getOrElse
        Future.failed(BadRequestError("worker is missing"))
    }

    def getCortexClient: Future[(CortexClient, Worker)] = {
      fields.getString("cortexId")
        .map { cortexId ⇒
          cortexConfig
            .instances
            .find(_.name == cortexId)
            .fold[Future[(CortexClient, Worker)]](Future.failed(NotFoundError(s"cortex $cortexId not found"))) { c ⇒
              getWorker(c).map(c -> _)
            }
        }
        .getOrElse {
          Future.firstCompletedOf {
            cortexConfig.instances.map(c ⇒ getWorker(c).map(c -> _))
          }
        }
    }

    def getEntity(objectType: String, objectId: String): Future[BaseEntity] = {
      import org.elastic4play.services.QueryDSL._

      findSrv.apply(Some(objectType), withId(objectId), Some("0-1"), Nil)._1
        .runWith(Sink.headOption)
        .flatMap {
          case Some(entity) ⇒ Future.successful(entity)
          case None         ⇒ Future.failed(NotFoundError(s"$objectType $objectId not found"))
        }
    }

    for {
      objectType ← fields.getString("objectType").fold[Future[String]](Future.failed(MissingAttributeError("action.objectType")))(Future.successful)
      objectId ← fields.getString("objectId").fold[Future[String]](Future.failed(MissingAttributeError("action.objectId")))(Future.successful)
      (cortexClient, worker) ← getCortexClient
      tlp = fields.getLong("tlp").getOrElse(2L)
      message = fields.getString("message").getOrElse("")
      parameters = fields.getValue("parameters") match {
        case Some(o: JsObject) ⇒ o
        case _                 ⇒ JsObject.empty
      }
      entity ← getEntity(objectType, objectId)
      entityJson ← auxSrv(entity, 10, withStats = false, removeUnaudited = true)
      jobJson ← cortexClient.execute(
        worker.id,
        s"thehive:$objectType",
        entityJson,
        tlp,
        message,
        parameters)
      job = jobJson.as[CortexJob] //(cortexActionJobReads(cortexClient.name))
      action ← createSrv[ActionModel, Action](actionModel, Fields.empty
        .set("objectType", entity.model.modelName)
        .set("objectId", entity.id)
        .set("workerId", job.analyzerId)
        .set("workerName", job.analyzerName)
        .set("workerDefinition", job.analyzerDefinition)
        //.set("status", JobStatus.InProgress)
        .set("objectType", objectType)
        .set("objectId", objectId)
        //        .set("startDate", Json.toJson(new Date()))
        .set("cortexId", cortexClient.name)
        .set("cortexJobId", job.id))
      _ = updateActionWithCortex(action.id, job.id, cortexClient)
    } yield action
  }
}
