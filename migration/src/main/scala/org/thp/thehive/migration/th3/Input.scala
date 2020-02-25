package org.thp.thehive.migration.th3

import java.util.{Base64, Date}

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.reflect.{classTag, ClassTag}
import scala.util.{Failure, Success, Try}

import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle}
import play.api.libs.json._
import play.api.{Configuration, Logger}

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
import org.thp.thehive.models.{Organisation, Profile}
import org.thp.thehive.services.{ImpactStatusSrv, ResolutionStatusSrv, UserSrv}

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

    def read[A: Reads: ClassTag]: Source[A, NotUsed] =
      source
        .map(_.validate[A])
        .mapConcat {
          case JsSuccess(value, _) => List(value)
          case JsError(errors) =>
            val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
            logger.error(s"${classTag[A]} read failure:$errorStr")
            Nil
        }

    def readWithParent[A: Reads: ClassTag](parent: JsValue => Try[String]): Source[(String, A), NotUsed] =
      source
        .map(json => parent(json) -> json.validate[A])
        .mapConcat {
          case (Success(parent), JsSuccess(value, _)) => List(parent -> value)
          case (_, JsError(errors)) =>
            val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
            logger.error(s"${classTag[A]} read failure:$errorStr")
            Nil
          case (Failure(error), _) =>
            logger.error(s"${classTag[A]} read failure", error)
            Nil
        }
  }

  def readAttachment(id: String): Source[ByteString, NotUsed] =
    Source.unfoldAsync(0) { chunkNumber =>
      dbGet("data", s"${id}_$chunkNumber")
        .map { json =>
          (json \ "binary").asOpt[String].map(s => chunkNumber + 1 -> ByteString(Base64.getDecoder.decode(s)))
        }
        .recover { case _ => None }
    }

  override def listOrganisations(filter: Filter): Source[InputOrganisation, NotUsed] =
    Source(
      List(
        InputOrganisation(MetaData(mainOrganisation, "system", new Date, None, None), Organisation(mainOrganisation, mainOrganisation))
      )
    )

  override def listCases(filter: Filter): Source[InputCase, NotUsed] =
    dbFind(Some("all"), Seq("-createdAt"))(indexName =>
      search(indexName).query(
        bool(
          Seq(termQuery("relations", "case"), rangeQuery("createdAt").gte(filter.caseFromDate)),
          Nil,
          Nil
        )
      )
    )._1
      .read[InputCase]

  override def listCaseObservables(filter: Filter): Source[(String, InputObservable), NotUsed] =
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

  override def listCaseObservables(caseId: String): Source[(String, InputObservable), NotUsed] =
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

  override def listCaseTasks(filter: Filter): Source[(String, InputTask), NotUsed] =
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

  override def listCaseTasks(caseId: String): Source[(String, InputTask), NotUsed] =
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

  override def listCaseTaskLogs(filter: Filter): Source[(String, InputLog), NotUsed] =
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

  override def listCaseTaskLogs(caseId: String): Source[(String, InputLog), NotUsed] =
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

  override def listAlerts(filter: Filter): Source[InputAlert, NotUsed] =
    dbFind(Some("all"), Seq("-createdAt"))(indexName =>
      search(indexName).query(bool(Seq(termQuery("relations", "alert"), rangeQuery("createdAt").gte(filter.alertFromDate)), Nil, Nil))
    )._1
      .read[InputAlert]

  override def listAlertObservables(filter: Filter): Source[(String, InputObservable), NotUsed] =
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
          observablesJson.flatMap { observableJson =>
            observableJson.validate(alertObservableReads(metaData)) match {
              case JsSuccess(observable, _) => Seq(metaData.id -> observable)
              case JsError(errors) =>
                val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
                logger.error(s"Alert observable read failure:$errorStr")
                Nil
            }
          }.toList
      }

  override def listAlertObservables(alertId: String): Source[(String, InputObservable), NotUsed] =
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
          observablesJson.flatMap { observableJson =>
            observableJson.validate(alertObservableReads(metaData)) match {
              case JsSuccess(observable, _) => Seq(metaData.id -> observable)
              case JsError(errors) =>
                val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
                logger.error(s"Alert observable read failure:$errorStr")
                Nil
            }
          }.toList
      }

  override def listUsers(filter: Filter): Source[InputUser, NotUsed] =
    dbFind(Some("all"), Seq("createdAt"))(indexName => search(indexName).query(termQuery("relations", "user")))
      ._1
      .read[InputUser]

  override def listCustomFields(filter: Filter): Source[InputCustomField, NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(termQuery("relations", "dblist"), bool(Nil, Seq(termQuery("dblist", "case_metrics"), termQuery("dblist", "custom_fields")), Nil)),
          Nil,
          Nil
        )
      )
    )._1.read[InputCustomField]

  override def listObservableTypes(filter: Filter): Source[InputObservableType, NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(bool(Seq(termQuery("relations", "dblist"), termQuery("dblist", "list_artifactDataType")), Nil, Nil))
    )._1
      .read[InputObservableType]

  override def listProfiles(filter: Filter): Source[InputProfile, NotUsed] =
    Source.empty[Profile].map(profile => InputProfile(MetaData(profile.name, UserSrv.init.login, new Date, None, None), profile))

  override def listImpactStatus(filter: Filter): Source[InputImpactStatus, NotUsed] =
    Source(List(ImpactStatusSrv.noImpact, ImpactStatusSrv.withImpact, ImpactStatusSrv.notApplicable))
      .map(status => InputImpactStatus(MetaData(status.value, UserSrv.init.login, new Date, None, None), status))

  override def listResolutionStatus(filter: Filter): Source[InputResolutionStatus, NotUsed] =
    Source(
      List(
        ResolutionStatusSrv.indeterminate,
        ResolutionStatusSrv.falsePositive,
        ResolutionStatusSrv.truePositive,
        ResolutionStatusSrv.other,
        ResolutionStatusSrv.duplicated
      )
    ).map(status => InputResolutionStatus(MetaData(status.value, UserSrv.init.login, new Date, None, None), status))

  override def listCaseTemplate(filter: Filter): Source[InputCaseTemplate, NotUsed] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "caseTemplate")))
      ._1
      .read[InputCaseTemplate]

  override def listCaseTemplateTask(filter: Filter): Source[(String, InputTask), NotUsed] =
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
          tasksJson.flatMap { taskJson =>
            taskJson.validate(caseTemplateTaskReads(metaData)) match {
              case JsSuccess(task, _) => Seq(metaData.id -> task)
              case JsError(errors) =>
                val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
                logger.error(s"Case template task read failure:$errorStr")
                Nil
            }
          }.toList
      }

  def listCaseTemplateTask(caseTemplateId: String): Source[(String, InputTask), NotUsed] =
    Source
      .futureSource {
        dbGet("caseTemplate", caseTemplateId)
          .map { json =>
            val metaData = json.as[MetaData]
            val tasks    = (json \ "tasks").as(Reads.seq(caseTemplateTaskReads(metaData)))
            Source(tasks.to[immutable.Iterable].map(caseTemplateId -> _))
          }
          .recover {
            case error =>
              logger.error(s"Case template task read failure:$error")
              Source.empty
          }
      }
      .mapMaterializedValue(_ => NotUsed)

  dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "caseTemplate")))

  override def listJobs(filter: Filter): Source[(String, InputJob), NotUsed] =
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

  override def listJobs(observableId: String): Source[(String, InputJob), NotUsed] =
    dbFind(Some("all"), Nil)(indexName =>
      search(indexName).query(
        bool(
          Seq(
            termQuery("relations", "case_artifact_job"),
            hasParentQuery(
              "case_artifact",
              hasParentQuery("case", idsQuery(observableId), score = false),
              score = false
            )
          ),
          Nil,
          Nil
        )
      )
    )._1
      .readWithParent[InputJob](json => Try((json \ "_parent").as[String]))(jobReads, classTag[InputJob])

  override def listJobObservables(filter: Filter): Source[(String, InputObservable), NotUsed] =
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
        for {
          metaData <- json.validate[MetaData]
          observablesJson = (json \ "artifacts").asOpt[Seq[JsValue]].getOrElse(Nil)
        } yield (metaData, observablesJson)
      }
      .mapConcat {
        case JsSuccess(x, _) => List(x)
        case JsError(errors) =>
          val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
          logger.error(s"Job observable read failure:$errorStr")
          Nil
      }
      .mapConcat {
        case (metaData, observablesJson) =>
          observablesJson.flatMap { observableJson =>
            observableJson.validate(jobObservableReads(metaData)) match {
              case JsSuccess(observable, _) => Seq(metaData.id -> observable)
              case JsError(errors) =>
                val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
                logger.error(s"Job observable read failure:$errorStr")
                Nil
            }
          }.toList
      }

  override def listJobObservables(caseId: String): Source[(String, InputObservable), NotUsed] =
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
        for {
          metaData <- json.validate[MetaData]
          observablesJson = (json \ "artifacts").asOpt[Seq[JsValue]].getOrElse(Nil)
        } yield (metaData, observablesJson)
      }
      .mapConcat {
        case JsSuccess(x, _) => List(x)
        case JsError(errors) =>
          val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
          logger.error(s"Job observable read failure:$errorStr")
          Nil
      }
      .mapConcat {
        case (metaData, observablesJson) =>
          observablesJson.flatMap { observableJson =>
            observableJson.validate(jobObservableReads(metaData)) match {
              case JsSuccess(observable, _) => Seq(metaData.id -> observable)
              case JsError(errors) =>
                val errorStr = errors.map(e => s"\n - ${e._1}: ${e._2.mkString(",")}")
                logger.error(s"Job observable read failure:$errorStr")
                Nil
            }
          }.toList
      }

  override def listAction(filter: Filter): Source[(String, InputAction), NotUsed] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(termQuery("relations", "action")))
      ._1
      .read[(String, InputAction)]

  override def listAction(entityId: String): Source[(String, InputAction), NotUsed] =
    dbFind(Some("all"), Nil)(indexName => search(indexName).query(bool(Seq(termQuery("relations", "action"), idsQuery(entityId)), Nil, Nil)))
      ._1
      .read[(String, InputAction)]

  override def listAudit(filter: Filter): Source[(String, InputAudit), NotUsed] =
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

  def listAudit(entityId: String, filter: Filter): Source[(String, InputAudit), NotUsed] =
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
}
