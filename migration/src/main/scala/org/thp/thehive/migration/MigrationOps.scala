package org.thp.thehive.migration

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.thp.scalligraph.{NotFoundError, RichOptionTry}
import org.thp.thehive.migration.dto.{InputAlert, InputAudit, InputCase, InputCaseTemplate}
import play.api.Logger

import scala.collection.{immutable, mutable}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MigrationStats(val stats: Map[String, (Int, Int)]) {
  def +(other: MigrationStats): MigrationStats =
    new MigrationStats((stats.keySet ++ other.stats.keySet).map { key =>
      val (s1, f1) = stats.getOrElse(key, 0       -> 0)
      val (s2, f2) = other.stats.getOrElse(key, 0 -> 0)
      (key, (s1 + s2, f1 + f2))
    }.toMap)

  def success(name: String): MigrationStats =
    new MigrationStats({
      val (s, f) = stats.getOrElse(name, 0 -> 0)
      stats.updated(name, s + 1 -> f)
    })
  def failure(name: String): MigrationStats =
    new MigrationStats({
      val (s, f) = stats.getOrElse(name, 0 -> 0)
      stats.updated(name, s -> (f + 1))
    })

  override def toString: String =
    stats
      .map {
        case (name, (success, failure)) => s"$name: $success/${success + failure}"
      }
      .toSeq
      .sorted
      .mkString("\n")
}

object MigrationStats {
  def empty                                              = new MigrationStats(Map.empty)
  def apply(name: String, successes: Int, failures: Int) = new MigrationStats(Map(name -> (successes -> failures)))
  def success(name: String): MigrationStats              = apply(name, 1, 0)
  def failure(name: String): MigrationStats              = apply(name, 0, 1)
}

trait MigrationOps {
  lazy val logger: Logger = Logger(getClass)

  implicit class IdMappingOps(idMappings: Seq[IdMapping]) {

    def fromInput(id: String): Try[String] =
      idMappings
        .find(_.inputId == id)
        .fold[Try[String]](Failure(NotFoundError(s"Id $id not found")))(m => Success(m.outputId))
  }

  def migrate[A](name: String, source: Source[A, NotUsed], create: A => Try[IdMapping])(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[(Seq[IdMapping], MigrationStats)] =
    source
      .map { a =>
        create(a)
          .recoverWith {
            case error =>
              logger.error(s"$name creation failure: $error")
              Failure(error)
          }
      }
      .runFold[(Seq[IdMapping], Int, Int)]((Seq.empty, 0, 0)) {
        case ((idMappings, successes, failures), Success(idMapping)) => (idMappings :+ idMapping, successes + 1, failures)
        case ((idMappings, successes, failures), _)                  => (idMappings, successes, failures + 1)
      }
      .map {
        case (idMappings, successes, failures) => idMappings -> MigrationStats(name, successes, failures)
      }

  def migrateWithParent[A](
      name: String,
      parentIds: Seq[IdMapping],
      source: Source[(String, A), NotUsed],
      create: (String, A) => Try[IdMapping]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[(Seq[IdMapping], MigrationStats)] =
    source
      .map {
        case (parentId, a) =>
          parentIds.fromInput(parentId).flatMap(create(_, a)).recoverWith {
            case error =>
              logger.error(s"$name creation failure: $error")
              Failure(error)
          }
      }
      .runFold[(Seq[IdMapping], Int, Int)]((Seq.empty, 0, 0)) {
        case ((idMappings, successes, failures), Success(idMapping)) => (idMappings :+ idMapping, successes + 1, failures)
        case ((idMappings, successes, failures), _)                  => (idMappings, successes, failures + 1)
      }
      .map {
        case (idMappings, successes, failures) => idMappings -> MigrationStats(name, successes, failures)
      }

  def migrateAudit(ids: Seq[IdMapping], source: Source[(String, InputAudit), NotUsed], create: (String, InputAudit) => Try[Unit])(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[MigrationStats] =
    source
      .map {
        case (contextId, inputAudit) =>
          (for {
            cid <- ids.fromInput(contextId)
            objId = inputAudit.audit.objectId.map(ids.fromInput).flip.getOrElse {
              logger.warn(s"object Id not found in audit ${inputAudit.audit}")
              None
            }
            _ <- create(cid, inputAudit.updateObjectId(objId))
          } yield ())
            .recoverWith {
              case error =>
                logger.error(s"Audit creation failure: $error")
                Failure(error)
            }
      }
      .runFold[(Int, Int)]((0, 0)) {
        case ((successes, failures), Success(_)) => (successes + 1, failures)
        case ((successes, failures), _)          => (successes, failures + 1)
      }
      .map {
        case (successes, failures) => MigrationStats("Audit", successes, failures)
      }

  def migrateAWholeCaseTemplate(input: Input, output: Output)(
      inputCaseTemplate: InputCaseTemplate
  )(implicit ec: ExecutionContext, mat: Materializer): Future[MigrationStats] =
    output.createCaseTemplate(inputCaseTemplate) match {
      case Success(caseTemplateId @ IdMapping(inputCaseTemplateId, _)) =>
        migrateWithParent("CaseTemplate/Task", Seq(caseTemplateId), input.listCaseTemplateTask(inputCaseTemplateId), output.createCaseTemplateTask)
          .map(_._2.success("CaseTemplate"))
      case Failure(error) =>
        logger.error(s"Migration of case template ${inputCaseTemplate.caseTemplate.name} failed: $error")
        Future.successful(MigrationStats.failure("CaseTemplate"))
    }

  def migrateWholeCaseTemplates(input: Input, output: Output, filter: Filter)(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[MigrationStats] =
    input
      .listCaseTemplate(filter)
      .filterNot(output.caseTemplateExists)
      .mapAsync(1)(migrateAWholeCaseTemplate(input, output))
      .runFold(MigrationStats.empty)(_ + _)

  def migrateAWholeCase(input: Input, output: Output, filter: Filter)(
      inputCase: InputCase
  )(implicit ec: ExecutionContext, mat: Materializer): Future[(Option[IdMapping], MigrationStats)] =
    output
      .createCase(inputCase) match {
      case Success(caseId @ IdMapping(inputCaseId, _)) =>
        for {
          (caseTaskIds, caseTaskStats) <- migrateWithParent("Case/Task", Seq(caseId), input.listCaseTasks(inputCaseId), output.createCaseTask)
          (caseTaskLogIds, caseTaskLogStats) <- migrateWithParent(
            "Case/Task/Log",
            caseTaskIds,
            input.listCaseTaskLogs(inputCaseId),
            output.createCaseTaskLog
          )
          (caseObservableIds, caseObservableStats) <- migrateWithParent(
            "Case/Observable",
            Seq(caseId),
            input.listCaseObservables(inputCaseId),
            output.createCaseObservable
          )
          (jobIds, jobStats) <- migrateWithParent("Case/Observable/Job", caseObservableIds, input.listJobs(inputCaseId), output.createJob)
          (jobObservableIds, jobObservableStats) <- migrateWithParent(
            "Case/Observable/Job/Observable",
            jobIds,
            input.listJobObservables(inputCaseId),
            output.createJobObservable
          )
          caseEntitiesIds = caseTaskIds ++ caseTaskLogIds ++ caseObservableIds ++ jobIds ++ jobObservableIds :+ caseId
          actionSource    = Source(caseEntitiesIds.to[immutable.Iterable]).flatMapConcat(id => input.listAction(id.inputId))
          (actionIds, actionStats) <- migrateWithParent("Action", caseEntitiesIds, actionSource, output.createAction)
          caseEntitiesAuditIds = caseEntitiesIds ++ actionIds
          auditSource          = Source(caseEntitiesAuditIds.to[immutable.Iterable]).flatMapConcat(id => input.listAudit(id.inputId, filter))
          auditStats <- migrateAudit(caseEntitiesAuditIds, auditSource, output.createAudit)
        } yield Some(caseId) -> (caseTaskStats + caseTaskLogStats + caseObservableStats + jobStats + jobObservableStats + actionStats + auditStats)
          .success("Case")
      case Failure(error) =>
        logger.error(s"Case creation failure, $error")
        Future.successful(None -> MigrationStats.failure("Case"))
    }

//  def migrateWholeCases(input: Input, output: Output, filter: Filter)(implicit ec: ExecutionContext, mat: Materializer): Future[MigrationStats] =
//    input
//      .listCases(filter)
//      .filterNot(output.caseExists)
//      .mapAsync(1)(migrateAWholeCase(input, output, filter)) // TODO recover failed future
//    .runFold(MigrationStats.empty)(_ + _)

  def migrateAWholeAlert(input: Input, output: Output, filter: Filter)(
      inputAlert: InputAlert
  )(implicit ec: ExecutionContext, mat: Materializer): Future[MigrationStats] =
    output.createAlert(inputAlert) match {
      case Success(alertId @ IdMapping(inputAlertId, _)) =>
        for {
          (alertObservableIds, alertObservableStats) <- migrateWithParent(
            "Alert/Observable",
            Seq(alertId),
            input.listAlertObservables(inputAlertId),
            output.createAlertObservable
          )
          alertEntitiesIds = alertId +: alertObservableIds
          actionSource     = Source(alertEntitiesIds.to[immutable.Iterable]).flatMapConcat(id => input.listAction(id.inputId))
          (actionIds, actionStats) <- migrateWithParent("Action", alertEntitiesIds, actionSource, output.createAction)
          alertEntitiesAuditIds = alertEntitiesIds ++ actionIds
          auditSource           = Source(alertEntitiesAuditIds.to[immutable.Iterable]).flatMapConcat(id => input.listAudit(id.inputId, filter))
          auditStats <- migrateAudit(alertEntitiesAuditIds, auditSource, output.createAudit)
        } yield (alertObservableStats + actionStats + auditStats).success("Alert")
      case Failure(error) =>
        logger.error(s"Migration of alert ${inputAlert.alert.`type`}:${inputAlert.alert.source}:${inputAlert.alert.sourceRef} failed: $error")
        Future.successful(MigrationStats.failure("Alert"))
    }

//  def migrateWholeAlerts(input: Input, output: Output, filter: Filter)(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
//    input
//      .listAlerts(filter)
//      .filterNot(output.alertExists)
//      .mapAsync(1)(migrateAWholeAlert(input, output, filter))
//      .runWith(Sink.ignore)
//      .map(_ => ())

  def migrate(input: Input, output: Output, filter: Filter)(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[MigrationStats] = {
    val pendingAlertCase: mutable.Map[String, Seq[InputAlert]] = mutable.HashMap.empty[String, Seq[InputAlert]]
    def migrateCasesAndAlerts(): Future[MigrationStats] = {
      val ordering: Ordering[Either[InputAlert, InputCase]] = new Ordering[Either[InputAlert, InputCase]] {
        def createdAt(x: Either[InputAlert, InputCase]): Long = x.fold(_.metaData.createdAt.getTime, _.metaData.createdAt.getTime)
        override def compare(x: Either[InputAlert, InputCase], y: Either[InputAlert, InputCase]): Int =
          java.lang.Long.compare(createdAt(x), createdAt(y)) * -1
      }

      val caseSource  = input.listCases(filter).filterNot(output.caseExists).map(Right.apply)
      val alertSource = input.listAlerts(filter).filterNot(output.alertExists).map(Left.apply)
      caseSource
        .mergeSorted(alertSource)(ordering)
        .runFoldAsync[(Seq[IdMapping], MigrationStats)](Seq.empty -> MigrationStats.empty) {
          case ((caseIds, migrationStats), Right(case0)) =>
            migrateAWholeCase(input, output, filter)(case0)
              .map { case (caseId, stats) => (caseIds ++ caseId, migrationStats + stats) }
          case ((caseIds, migrationStats), Left(alert)) =>
            alert
              .caseId
              .map { caseId =>
                caseIds.fromInput(caseId).recoverWith {
                  case error =>
                    pendingAlertCase += caseId -> (pendingAlertCase.getOrElse(caseId, Nil) :+ alert)
                    Failure(error)
                }
              }
              .flip
              .fold(
                _ => Future.successful(caseIds -> migrationStats),
                caseId =>
                  migrateAWholeAlert(input, output, filter)(alert.updateCaseId(caseId))
                    .map(stats => caseIds -> (migrationStats + stats))
              )
        }
        .map(_._2)
    }

    for {
      (_, profileStats) <- migrate("Profile", input.listProfiles(filter).filterNot(output.profileExists), output.createProfile _)
      (_, organisationStats) <- migrate(
        "Organisation",
        input.listOrganisations(filter).filterNot(output.organisationExists),
        output.createOrganisation _
      )
      (_, userStats) <- migrate("User", input.listUsers(filter).filterNot(output.userExists), output.createUser _)
      (_, impactStatuStats) <- migrate(
        "ImpactStatus",
        input.listImpactStatus(filter).filterNot(output.impactStatusExists),
        output.createImpactStatus _
      )
      (_, resolutionStatuStats) <- migrate(
        "ResolutionStatus",
        input.listResolutionStatus(filter).filterNot(output.resolutionStatusExists),
        output.createResolutionStatus _
      )
      (_, customFieldStats) <- migrate("CustomField", input.listCustomFields(filter).filterNot(output.customFieldExists), output.createCustomField _)
      (_, observableTypeStats) <- migrate(
        "ObservableType",
        input.listObservableTypes(filter).filterNot(output.observableTypeExists),
        output.createObservableTypes _
      )
      caseTemplateStats <- migrateWholeCaseTemplates(input, output, filter)
      caseAndAlertSTats <- migrateCasesAndAlerts()
    } yield profileStats + organisationStats + userStats + impactStatuStats + resolutionStatuStats + customFieldStats + observableTypeStats + caseTemplateStats + caseAndAlertSTats
  }
}
