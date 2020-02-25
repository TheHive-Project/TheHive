package org.thp.thehive.migration

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{classTag, ClassTag}
import scala.util.{Failure, Success, Try}

import play.api.Logger

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.thp.scalligraph.{NotFoundError, RichOptionTry}
import org.thp.thehive.migration.dto.{InputAlert, InputAudit, InputCase, InputCaseTemplate}

trait MigrationOps {
  lazy val logger: Logger = Logger(getClass)

  implicit class IdMappingOps(idMappings: Seq[IdMapping]) {

    def fromInput(id: String): Try[String] =
      idMappings
        .find(_.inputId == id)
        .fold[Try[String]](Failure(NotFoundError(s"Id $id not found")))(m => Success(m.outputId))
  }

  def migrate[A: ClassTag](source: Source[A, NotUsed], create: A => Try[IdMapping])(implicit mat: Materializer): Future[Seq[IdMapping]] =
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
  )(implicit mat: Materializer): Future[Seq[I]] =
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

  @deprecated("0.1", "use migrate()")
  def process(input: Input, output: Output, filter: Filter, removeData: Boolean)(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[Unit] = {
    if (removeData) output.removeData()
    for {
      profileIds          <- migrate(input.listProfiles(filter).filterNot(output.profileExists), output.createProfile)
      organisationIds     <- migrate(input.listOrganisations(filter).filterNot(output.organisationExists), output.createOrganisation)
      userIds             <- migrate(input.listUsers(filter).filterNot(output.userExists), output.createUser)
      impactStatusIds     <- migrate(input.listImpactStatus(filter).filterNot(output.impactStatusExists), output.createImpactStatus)
      resolutionStatusIds <- migrate(input.listResolutionStatus(filter).filterNot(output.resolutionStatusExists), output.createResolutionStatus)
      customFieldsIds     <- migrate(input.listCustomFields(filter).filterNot(output.customFieldExists), output.createCustomField)
      observableTypeIds   <- migrate(input.listObservableTypes(filter).filterNot(output.observableTypeExists), output.createObservableTypes)
      caseTemplateIds     <- migrate(input.listCaseTemplate(filter).filterNot(output.caseTemplateExists), output.createCaseTemplate)
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
      _ <- migrateAudit(
        profileIds ++ organisationIds ++ userIds ++ impactStatusIds ++ resolutionStatusIds ++ customFieldsIds ++ observableTypeIds ++ caseTemplateIds ++ caseTemplateTaskIds ++
          caseIds ++ caseObservableIds ++ caseTaskIds ++ caseTaskLogIds ++ alertIds ++ alertObservableIds ++ jobIds ++ jobObservableIds ++ actionIds,
        input.listAudit(filter),
        output.createAudit
      )
    } yield ()
  }

  def migrateAudit(ids: Seq[IdMapping], source: Source[(String, InputAudit), NotUsed], create: (String, InputAudit) => Try[Unit])(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[Unit] =
    source
      .map {
        case (contextId, inputAudit) =>
          for {
            cid <- ids.fromInput(contextId)
            objId = inputAudit.audit.objectId.map(ids.fromInput).flip.getOrElse {
              logger.error(s"object Id not found in audit ${inputAudit.audit}")
              None
            }
            _ <- create(cid, inputAudit.updateObjectId(objId))
          } yield ()
      }
      .recover {
        case error =>
          logger.error("Audit creation failure", error)
          Nil
      }
      .runWith(Sink.ignore)
      .map(_ => ())

  def migrateAWholeCaseTemplate(input: Input, output: Output)(
      inputCaseTemplate: InputCaseTemplate
  )(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
    output.createCaseTemplate(inputCaseTemplate) match {
      case Success(caseTemplateId @ IdMapping(inputCaseTemplateId, _)) =>
        migrateWithParent(Seq(caseTemplateId), input.listCaseTemplateTask(inputCaseTemplateId), output.createCaseTemplateTask).map(_ => ())
      case Failure(error) =>
        logger.error(s"Migration of case template ${inputCaseTemplate.caseTemplate.name} failed", error)
        Future.successful(())
    }

  def migrateWholeCaseTemplates(input: Input, output: Output, filter: Filter)(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
    input
      .listCaseTemplate(filter)
      .filterNot(output.caseTemplateExists)
      .mapAsync(1)(migrateAWholeCaseTemplate(input, output))
      .runWith(Sink.ignore)
      .map(_ => ())

  def migrateAWholeCase(input: Input, output: Output, filter: Filter)(
      inputCase: InputCase
  )(implicit ec: ExecutionContext, mat: Materializer): Future[IdMapping] =
    output
      .createCase(inputCase)
      .fold(
        Future.failed, {
          case caseId @ IdMapping(inputCaseId, _) =>
            for {
              caseTaskIds       <- migrateWithParent(Seq(caseId), input.listCaseTasks(inputCaseId), output.createCaseTask)
              caseTaskLogIds    <- migrateWithParent(caseTaskIds, input.listCaseTaskLogs(inputCaseId), output.createCaseTaskLog)
              caseObservableIds <- migrateWithParent(Seq(caseId), input.listCaseObservables(inputCaseId), output.createCaseObservable)
              jobIds            <- migrateWithParent(caseObservableIds, input.listJobs(inputCaseId), output.createJob)
              jobObservableIds  <- migrateWithParent(jobIds, input.listJobObservables(inputCaseId), output.createJobObservable)
              caseEntitiesIds = caseTaskIds ++ caseTaskLogIds ++ caseObservableIds ++ jobIds ++ jobObservableIds :+ caseId
              actionSource    = Source(caseEntitiesIds.to[immutable.Iterable]).flatMapConcat(id => input.listAction(id.inputId))
              actionIds <- migrateWithParent(caseEntitiesIds, actionSource, output.createAction)
              caseEntitiesAuditIds = caseEntitiesIds ++ actionIds
              auditSource          = Source(caseEntitiesAuditIds.to[immutable.Iterable]).flatMapConcat(id => input.listAudit(id.inputId, filter))
              _ <- migrateAudit(caseEntitiesAuditIds, auditSource, output.createAudit)
            } yield caseId
        }
      )

  def migrateWholeCases(input: Input, output: Output, filter: Filter)(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
    input
      .listCases(filter)
      .filterNot(output.caseExists)
      .mapAsync(1)(migrateAWholeCase(input, output, filter)) // TODO recover failed future
      .runWith(Sink.ignore)
      .map(_ => ())

  def migrateAWholeAlert(input: Input, output: Output, filter: Filter)(
      inputAlert: InputAlert
  )(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
    output.createAlert(inputAlert) match {
      case Success(alertId @ IdMapping(inputAlertId, _)) =>
        for {
          alertObservableIds <- migrateWithParent(Seq(alertId), input.listAlertObservables(inputAlertId), output.createAlertObservable)
          alertEntitiesIds = alertId +: alertObservableIds
          actionSource     = Source(alertEntitiesIds.to[immutable.Iterable]).flatMapConcat(id => input.listAction(id.inputId))
          actionIds <- migrateWithParent(alertEntitiesIds, actionSource, output.createAction)
          alertEntitiesAuditIds = alertEntitiesIds ++ actionIds
          auditSource           = Source(alertEntitiesAuditIds.to[immutable.Iterable]).flatMapConcat(id => input.listAudit(id.inputId, filter))
          _ <- migrateAudit(alertEntitiesAuditIds, auditSource, output.createAudit)
        } yield ()
      case Failure(error) =>
        logger.error(s"Migration of alert ${inputAlert.alert.`type`}:${inputAlert.alert.source}:${inputAlert.alert.sourceRef} failed", error)
        Future.successful(())
    }

  def migrateWholeAlerts(input: Input, output: Output, filter: Filter)(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
    input
      .listAlerts(filter)
      .filterNot(output.alertExists)
      .mapAsync(1)(migrateAWholeAlert(input, output, filter))
      .runWith(Sink.ignore)
      .map(_ => ())

  def migrate(input: Input, output: Output, filter: Filter, removeData: Boolean)(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[Unit] = {
    def migrateCasesAndAlerts(): Future[Unit] = {
      val ordering: Ordering[Either[InputAlert, InputCase]] = new Ordering[Either[InputAlert, InputCase]] {
        def createdAt(x: Either[InputAlert, InputCase]): Long = x.fold(_.metaData.createdAt.getTime, _.metaData.createdAt.getTime)
        override def compare(x: Either[InputAlert, InputCase], y: Either[InputAlert, InputCase]): Int =
          java.lang.Long.compare(createdAt(x), createdAt(y)) * -1
      }

      val caseSource  = input.listCases(filter).filterNot(output.caseExists).map(Right.apply)
      val alertSource = input.listAlerts(filter).filterNot(output.alertExists).map(Left.apply)
      caseSource
        .mergeSorted(alertSource)(ordering)
        .foldAsync(Seq.empty[IdMapping]) {
          case (caseIds, Right(case0)) =>
            migrateAWholeCase(input, output, filter)(case0).transform(_.fold(_ => Success(caseIds), cid => Success(caseIds :+ cid)))
          case (caseIds, Left(alert)) =>
            val caseId = alert.caseId.map(caseIds.fromInput).flip.getOrElse {
              logger.error(s"case Id not found in alert $alert")
              None
            }
            migrateAWholeAlert(input, output, filter)(alert.updateCaseId(caseId)).map(_ => caseIds)
        }
        .runWith(Sink.ignore)
        .map(_ => ())
    }

    for {
      _ <- migrate(input.listProfiles(filter).filterNot(output.profileExists), output.createProfile)
      _ <- migrate(input.listOrganisations(filter).filterNot(output.organisationExists), output.createOrganisation)
      _ <- migrate(input.listUsers(filter).filterNot(output.userExists), output.createUser)
      _ <- migrate(input.listImpactStatus(filter).filterNot(output.impactStatusExists), output.createImpactStatus)
      _ <- migrate(input.listResolutionStatus(filter).filterNot(output.resolutionStatusExists), output.createResolutionStatus)
      _ <- migrate(input.listCustomFields(filter).filterNot(output.customFieldExists), output.createCustomField)
      _ <- migrate(input.listObservableTypes(filter).filterNot(output.observableTypeExists), output.createObservableTypes)
      _ <- migrateWholeCaseTemplates(input, output, filter)
      _ <- migrateCasesAndAlerts()
    } yield ()
  }
}
