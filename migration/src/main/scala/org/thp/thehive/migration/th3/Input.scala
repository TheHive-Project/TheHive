package org.thp.thehive.migration.th3

import java.util.{Base64, Date}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.Guice
import com.sksamuel.elastic4s.http.ElasticDsl.{bool, hasParentQuery, idsQuery, rangeQuery, search, termQuery}
import javax.inject.{Inject, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.thp.thehive.migration
import org.thp.thehive.migration.Filter
import org.thp.thehive.migration.dto._
import org.thp.thehive.models.{ImpactStatus, Organisation, Profile, ResolutionStatus}
import org.thp.thehive.services.UserSrv
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle}
import play.api.libs.json._
import play.api.{Configuration, Logger}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{classTag, ClassTag}
import scala.util.{Failure, Success, Try}

object Input {

  def apply(configuration: Configuration)(implicit actorSystem: ActorSystem): Input =
    Guice
      .createInjector(new ScalaModule {
        override def configure(): Unit = {
          bind[Configuration].toInstance(configuration)
          bind[ActorSystem].toInstance(actorSystem)
          bind[Materializer].toInstance(Materializer(actorSystem))
          bind[ExecutionContext].toInstance(actorSystem.dispatcher)
          bind[ApplicationLifecycle].to[DefaultApplicationLifecycle]
          bind[Int].annotatedWithName("databaseVersion").toInstance(15)
        }
      })
      .getInstance(classOf[Input])
}

@Singleton
class Input @Inject() (configuration: Configuration, dbFind: DBFind, dbGet: DBGet, implicit val ec: ExecutionContext)
    extends migration.Input
    with Conversion {
  lazy val logger: Logger               = Logger(getClass)
  override val mainOrganisation: String = configuration.get[String]("mainOrganisation")

  implicit class SourceOfJson(source: Source[JsObject, NotUsed]) {

    def read[A: Reads: ClassTag]: Source[Try[A], NotUsed] =
      source.map(json => Try(json.as[A]))

    def readWithParent[A: Reads: ClassTag](parent: JsValue => Try[String]): Source[Try[(String, A)], NotUsed] =
      source.map(json => parent(json).flatMap(p => Try(p -> json.as[A])))
  }

  def readAttachment(id: String): Source[ByteString, NotUsed] =
    Source.unfoldAsync(0) { chunkNumber =>
      dbGet("data", s"${id}_$chunkNumber")
        .map { json =>
          (json \ "binary").asOpt[String].map(s => chunkNumber + 1 -> ByteString(Base64.getDecoder.decode(s)))
        }
        .recover { case _ => None }
    }

  override def listOrganisations(filter: Filter): Source[Try[InputOrganisation], NotUsed] =
    Source(
      List(
        Success(InputOrganisation(MetaData(mainOrganisation, "system", new Date, None, None), Organisation(mainOrganisation, mainOrganisation)))
      )
    )

  override def countOrganisations(filter: Filter): Future[Long] = Future.successful(1)

  override def listCases(filter: Filter): Source[Try[InputCase], NotUsed] = {
    val f =
      if (filter.alertFromDate == 0) Seq(termQuery("relations", "case"))
      else Seq(termQuery("relations", "case"), rangeQuery("createdAt").gte(filter.alertFromDate))
    dbFind(Some("all"), Seq("-createdAt"))(indexName => search(indexName).query(bool(f, Nil, Nil)))
      ._1
      .read[InputCase]
  }

  override def countCases(filter: Filter): Future[Long] = {
    val f =
      if (filter.alertFromDate == 0) Seq(termQuery("relations", "case"))
      else Seq(termQuery("relations", "case"), rangeQuery("createdAt").gte(filter.alertFromDate))
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(bool(f, Nil, Nil))
        .limit(0)
    )._2
  }

  override def listCaseObservables(filter: Filter): Source[Try[(String, InputObservable)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_artifact"),
            hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false)
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputObservable](json => Try((json \ "_parent").as[String]))

  override def countCaseObservables(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "case_artifact"),
              hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false)
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listCaseObservables(caseId: String): Source[Try[(String, InputObservable)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_artifact"),
            hasParentQuery("case", idsQuery(caseId), score = false)
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputObservable](json => Try((json \ "_parent").as[String]))

  override def countCaseObservables(caseId: String): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "case_artifact"),
              hasParentQuery("case", idsQuery(caseId), score = false)
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listCaseTasks(filter: Filter): Source[Try[(String, InputTask)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_task"),
            hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false)
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputTask](json => Try((json \ "_parent").as[String]))

  override def countCaseTasks(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "case_task"),
              hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false)
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listCaseTasks(caseId: String): Source[Try[(String, InputTask)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_task"),
            hasParentQuery("case", idsQuery(caseId), score = false)
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputTask](json => Try((json \ "_parent").as[String]))

  override def countCaseTasks(caseId: String): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "case_task"),
              hasParentQuery("case", idsQuery(caseId), score = false)
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listCaseTaskLogs(filter: Filter): Source[Try[(String, InputLog)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_task_log"),
            hasParentQuery(
              "case_task",
              hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false),
              score = false
            )
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputLog](json => Try((json \ "_parent").as[String]))

  override def countCaseTaskLogs(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "case_task_log"),
              hasParentQuery(
                "case_task",
                hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false),
                score = false
              )
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listCaseTaskLogs(caseId: String): Source[Try[(String, InputLog)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_task_log"),
            hasParentQuery(
              "case_task",
              hasParentQuery("case", idsQuery(caseId), score = false),
              score = false
            )
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputLog](json => Try((json \ "_parent").as[String]))

  override def countCaseTaskLogs(caseId: String): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "case_task_log"),
              hasParentQuery(
                "case_task",
                hasParentQuery("case", idsQuery(caseId), score = false),
                score = false
              )
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listAlerts(filter: Filter): Source[Try[InputAlert], NotUsed] = {
    val f =
      if (filter.alertFromDate == 0) Seq(termQuery("relations", "alert"))
      else Seq(termQuery("relations", "alert"), rangeQuery("createdAt").gte(filter.alertFromDate))
    dbFind(Some("all"), Seq("-createdAt"))(indexName => search(indexName).query(bool(f, Nil, Nil)))
      ._1
      .read[InputAlert]
  }

  override def countAlerts(filter: Filter): Future[Long] = {
    val f =
      if (filter.alertFromDate == 0) Seq(termQuery("relations", "alert"))
      else Seq(termQuery("relations", "alert"), rangeQuery("createdAt").gte(filter.alertFromDate))
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(bool(f, Nil, Nil)).limit(0))._2
  }

  override def listAlertObservables(filter: Filter): Source[Try[(String, InputObservable)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(bool(Seq(termQuery("relations", "alert"), rangeQuery("createdAt").gte(filter.alertFromDate)), Nil, Nil))
    )._1
      .map { json =>
        for {
          metaData        <- json.validate[MetaData]
          observablesJson <- (json \ "artifacts").validate[Seq[JsValue]]
        } yield (metaData, observablesJson)
      }
      .mapConcat {
        case JsSuccess(x, _) => List(x)
        case JsError(errors) =>
          val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
          logger.error(s"Alert observable read failure:$errorStr")
          Nil
      }
      .mapConcat {
        case (metaData, observablesJson) =>
          observablesJson.map(observableJson => Try(metaData.id -> observableJson.as(alertObservableReads(metaData)))).toList
      }

  override def countAlertObservables(filter: Filter): Future[Long] = Future.failed(new NotImplementedError)

  override def listAlertObservables(alertId: String): Source[Try[(String, InputObservable)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(bool(Seq(termQuery("relations", "alert"), idsQuery(alertId)), Nil, Nil)))
      ._1
      .map { json =>
        for {
          metaData        <- json.validate[MetaData]
          observablesJson <- (json \ "artifacts").validate[Seq[JsValue]]
        } yield (metaData, observablesJson)
      }
      .mapConcat {
        case JsSuccess(x, _) => List(x)
        case JsError(errors) =>
          val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
          logger.error(s"Alert observable read failure:$errorStr")
          Nil
      }
      .mapConcat {
        case (metaData, observablesJson) =>
          observablesJson.map(observableJson => Try(metaData.id -> observableJson.as(alertObservableReads(metaData)))).toList
      }

  override def countAlertObservables(alertId: String): Future[Long] = Future.failed(new NotImplementedError)

  override def listUsers(filter: Filter): Source[Try[InputUser], NotUsed] =
    dbFind(Some("all"), Seq("createdAt"))(indexName => search(indexName).query(termQuery("relations", "user")))
      ._1
      .read[InputUser]

  override def countUsers(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "user")).limit(0))._2

  override def listCustomFields(filter: Filter): Source[Try[InputCustomField], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(termQuery("relations", "dblist"), bool(Nil, Seq(termQuery("dblist", "case_metrics"), termQuery("dblist", "custom_fields")), Nil)),
          Nil,
          Nil
        )
      )
    )._1.read[InputCustomField]

  override def countCustomFields(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(termQuery("relations", "dblist"), bool(Nil, Seq(termQuery("dblist", "case_metrics"), termQuery("dblist", "custom_fields")), Nil)),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listObservableTypes(filter: Filter): Source[Try[InputObservableType], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(bool(Seq(termQuery("relations", "dblist"), termQuery("dblist", "list_artifactDataType")), Nil, Nil))
    )._1
      .read[InputObservableType]

  override def countObservableTypes(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(bool(Seq(termQuery("relations", "dblist"), termQuery("dblist", "list_artifactDataType")), Nil, Nil)).limit(0)
    )._2

  override def listProfiles(filter: Filter): Source[Try[InputProfile], NotUsed] =
    Source.empty[Profile].map(profile => Success(InputProfile(MetaData(profile.name, UserSrv.init.login, new Date, None, None), profile)))

  override def countProfiles(filter: Filter): Future[Long] = Future.successful(0)

  override def listImpactStatus(filter: Filter): Source[Try[InputImpactStatus], NotUsed] =
    Source
      .empty[ImpactStatus]
      .map(status => Success(InputImpactStatus(MetaData(status.value, UserSrv.init.login, new Date, None, None), status)))

  override def countImpactStatus(filter: Filter): Future[Long] = Future.successful(0)

  override def listResolutionStatus(filter: Filter): Source[Try[InputResolutionStatus], NotUsed] =
    Source
      .empty[ResolutionStatus]
      .map(status => Success(InputResolutionStatus(MetaData(status.value, UserSrv.init.login, new Date, None, None), status)))

  override def countResolutionStatus(filter: Filter): Future[Long] = Future.successful(0)

  override def listCaseTemplate(filter: Filter): Source[Try[InputCaseTemplate], NotUsed] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "caseTemplate")))
      ._1
      .read[InputCaseTemplate]

  override def countCaseTemplate(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "caseTemplate")).limit(0))._2

  override def listCaseTemplateTask(filter: Filter): Source[Try[(String, InputTask)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "caseTemplate")))
      ._1
      .map { json =>
        for {
          metaData  <- json.validate[MetaData]
          tasksJson <- (json \ "tasks").validate[Seq[JsValue]]
        } yield (metaData, tasksJson)
      }
      .mapConcat {
        case JsSuccess(x, _) => List(x)
        case JsError(errors) =>
          val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
          logger.error(s"Case template task read failure:$errorStr")
          Nil
      }
      .mapConcat {
        case (metaData, tasksJson) =>
          tasksJson.map(taskJson => Try(metaData.id -> taskJson.as(caseTemplateTaskReads(metaData)))).toList
      }

  override def countCaseTemplateTask(filter: Filter): Future[Long] = Future.failed(new NotImplementedError)

  def listCaseTemplateTask(caseTemplateId: String): Source[Try[(String, InputTask)], NotUsed] =
    Source
      .futureSource {
        dbGet("caseTemplate", caseTemplateId)
          .map { json =>
            val metaData = json.as[MetaData]
            val tasks    = (json \ "tasks").as(Reads.seq(caseTemplateTaskReads(metaData)))
            Source(tasks.to[immutable.Iterable].map(t => Success(caseTemplateId -> t)))
          }
          .recover {
            case error =>
              Source.single(Failure(error))
          }
      }
      .mapMaterializedValue(_ => NotUsed)

  override def countCaseTemplateTask(caseTemplateId: String): Future[Long] = Future.failed(new NotImplementedError)

  override def listJobs(filter: Filter): Source[Try[(String, InputJob)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_artifact_job"),
            hasParentQuery(
              "case_artifact",
              hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false),
              score = false
            )
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputJob](json => Try((json \ "_parent").as[String]))(jobReads, classTag[InputJob])

  override def countJobs(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "case_artifact_job"),
              hasParentQuery(
                "case_artifact",
                hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false),
                score = false
              )
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listJobs(caseId: String): Source[Try[(String, InputJob)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_artifact_job"),
            hasParentQuery(
              "case_artifact",
              hasParentQuery("case", idsQuery(caseId), score = false),
              score = false
            )
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputJob](json => Try((json \ "_parent").as[String]))(jobReads, classTag[InputJob])

  override def countJobs(caseId: String): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "case_artifact_job"),
              hasParentQuery(
                "case_artifact",
                hasParentQuery("case", idsQuery(caseId), score = false),
                score = false
              )
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listJobObservables(filter: Filter): Source[Try[(String, InputObservable)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_artifact_job"),
            hasParentQuery(
              "case_artifact",
              hasParentQuery("case", rangeQuery("createdAt").gte(filter.caseFromDate), score = false),
              score = false
            )
          ),
          Nil,
          Nil
        )
      )
    )._1
      .map { json =>
        Try {
          val metaData = json.as[MetaData]
          (json \ "artifacts").asOpt[Seq[JsValue]].getOrElse(Nil).map(o => Try(metaData.id -> o.as(jobObservableReads(metaData))))
        }
      }
      .mapConcat {
        case Success(o)     => o.toList
        case Failure(error) => List(Failure(error))
      }

  override def countJobObservables(filter: Filter): Future[Long] = Future.failed(new NotImplementedError)

  override def listJobObservables(caseId: String): Source[Try[(String, InputObservable)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_artifact_job"),
            hasParentQuery(
              "case_artifact",
              hasParentQuery("case", idsQuery(caseId), score = false),
              score = false
            )
          ),
          Nil,
          Nil
        )
      )
    )._1
      .map { json =>
        Try {
          val metaData = json.as[MetaData]
          (json \ "artifacts").asOpt[Seq[JsValue]].getOrElse(Nil).map(o => Try(metaData.id -> o.as(jobObservableReads(metaData))))
        }
      }
      .mapConcat {
        case Success(o)     => o.toList
        case Failure(error) => List(Failure(error))
      }

  override def countJobObservables(caseId: String): Future[Long] = Future.failed(new NotImplementedError)

  override def listAction(filter: Filter): Source[Try[(String, InputAction)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "action")))
      ._1
      .read[(String, InputAction)]

  override def countAction(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "action")).limit(0))._2

  override def listAction(entityId: String): Source[Try[(String, InputAction)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(bool(Seq(termQuery("relations", "action"), idsQuery(entityId)), Nil, Nil)))
      ._1
      .read[(String, InputAction)]

  override def countAction(entityId: String): Future[Long] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(bool(Seq(termQuery("relations", "action"), idsQuery(entityId)), Nil, Nil)).limit(0))._2

  override def listAudit(filter: Filter): Source[Try[(String, InputAudit)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "audit"),
            rangeQuery("createdAt").gte(filter.auditFromDate)
          ),
          Nil,
          Nil
        )
      )
    )._1.read[(String, InputAudit)]

  override def countAudit(filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "audit"),
              rangeQuery("createdAt").gte(filter.auditFromDate)
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2

  override def listAudit(entityId: String, filter: Filter): Source[Try[(String, InputAudit)], NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "audit"),
            rangeQuery("createdAt").gte(filter.auditFromDate),
            termQuery("objectId", entityId)
          ),
          Nil,
          Nil
        )
      )
    )._1.read[(String, InputAudit)]

  def countAudit(entityId: String, filter: Filter): Future[Long] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName)
        .query(
          bool(
            Seq(
              termQuery("relations", "audit"),
              rangeQuery("createdAt").gte(filter.auditFromDate),
              termQuery("objectId", entityId)
            ),
            Nil,
            Nil
          )
        )
        .limit(0)
    )._2
}
