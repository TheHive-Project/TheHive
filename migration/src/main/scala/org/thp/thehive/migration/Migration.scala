package org.thp.thehive.migration

import java.io.File

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.{classTag, ClassTag}
import scala.util.{Failure, Success, Try}

import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Configuration, Environment, Logger}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import org.thp.scalligraph.NotFoundError

class Migration(input: Input, output: Output, implicit val ec: ExecutionContext, implicit val mat: Materializer) {
  lazy val logger: Logger = Logger(getClass)

  implicit class IdMappingOps(idMappings: Seq[IdMapping]) {

    def fromInput(id: String): Try[String] =
      idMappings
        .find(_.inputId == id)
        .fold[Try[String]](Failure(NotFoundError(s"Id $id not found")))(m => Success(m.outputId))
  }

  def migrate[A: ClassTag](source: Source[A, NotUsed], create: A => Try[IdMapping]): Future[Seq[IdMapping]] =
    source
      .map(create)
      .mapConcat {
        case Success(idMapping) => List(idMapping)
        case Failure(error) =>
          logger.error(s"${classTag[A].runtimeClass.getSimpleName} creation failure", error)
          Nil
      }
      .runWith(Sink.seq)

  def migrateWithParent[A: ClassTag, I](
      parentIds: Seq[IdMapping],
      source: Source[(String, A), NotUsed],
      create: (String, A) => Try[I]
  ): Future[Seq[I]] =
    source
      .map {
        case (parentId, a) =>
          parentIds.fromInput(parentId).flatMap(create(_, a))
      }
      .mapConcat {
        case Success(idMapping) => List(idMapping)
        case Failure(error) =>
          logger.error(s"${classTag[A].runtimeClass.getSimpleName} creation failure", error)
          Nil
      }
      .runWith(Sink.seq)

  def process(filter: Filter, removeData: Boolean): Future[Unit] = {
    if (removeData) output.removeData()
    for {
      profileIds          <- migrate(input.listProfiles(filter), output.createProfile)
      organisationIds     <- migrate(input.listOrganisations(filter), output.createOrganisation)
      userIds             <- migrate(input.listUsers(filter), output.createUser)
      impactStatusIds     <- migrate(input.listImpactStatus(filter), output.createImpactStatus)
      resolutionStatusIds <- migrate(input.listResolutionStatus(filter), output.createResolutionStatus)
      customFieldsIds     <- migrate(input.listCustomFields(filter), output.createCustomField)
      observableTypeIds   <- migrate(input.listObservableTypes(filter), output.createObservableTypes)
      caseTemplateIds     <- migrate(input.listCaseTemplate(filter), output.createCaseTemplate)
      caseTemplateTaskIds <- migrateWithParent(caseTemplateIds, input.listCaseTemplateTask(filter), output.createCaseTemplateTask)
      caseIds             <- migrate(input.listCases(filter), output.createCase)
      caseObservableIds   <- migrateWithParent(caseIds, input.listCaseObservables(filter), output.createCaseObservable)
      caseTaskIds         <- migrateWithParent(caseIds, input.listCaseTasks(filter), output.createCaseTask)
      caseTaskLogIds      <- migrateWithParent(caseTaskIds, input.listCaseTaskLogs(filter), output.createCaseTaskLog)
      alertIds            <- migrate(input.listAlerts(filter), output.createAlert)
      alertObservableIds  <- migrateWithParent(alertIds, input.listAlertObservables(filter), output.createAlertObservable)
      jobIds              <- migrateWithParent(caseObservableIds, input.listJobs(filter), output.createJob)
      jobObservableIds    <- migrateWithParent(jobIds, input.listJobObservables(filter), output.createJobObservable)
      actionIds <- migrateWithParent(
        caseIds ++ caseObservableIds ++ caseTaskIds ++ caseTaskLogIds ++ alertIds,
        input.listAction(filter),
        output.createAction
      )
      _ <- migrateWithParent(
        profileIds ++ organisationIds ++ userIds ++ impactStatusIds ++ resolutionStatusIds ++ customFieldsIds ++ observableTypeIds ++ caseTemplateIds ++ caseTemplateTaskIds ++
          caseIds ++ caseObservableIds ++ caseTaskIds ++ caseTaskLogIds ++ alertIds ++ alertObservableIds ++ jobIds ++ jobObservableIds ++ actionIds,
        input.listAudit(filter),
        output.createAudit
      )
    } yield ()
  }
}

object Start extends App {

  val config =
    ConfigFactory
      .parseFileAnySyntax(new File("conf/migration.conf"))
      .withFallback(ConfigFactory.parseResources("play/reference-overrides.conf"))
      .withFallback(ConfigFactory.defaultReference())
      .resolve() // TODO read filename from argument
  implicit val actorSystem: ActorSystem = ActorSystem("TheHiveMigration", config)

  (new LogbackLoggerConfigurator).configure(Environment.simple(), Configuration.empty, Map.empty)

  val input = config.getString("input.name") match {
    case _ => th3.Input(Configuration(config.getConfig("input").withFallback(config)))
  }

  val output = config.getString("output.name") match {
    case _ => th4.Output(Configuration(config.getConfig("output").withFallback(config)))
  }

  val filter     = Filter.fromConfig(config.getConfig("input.filter"))
  val removeData = config.getBoolean("output.removeData")

  val process = new Migration(input, output, actorSystem.dispatcher, Materializer(actorSystem)).process(filter, removeData)
  Await.ready(process, Duration.Inf)
  System.exit(0)
}
