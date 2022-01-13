package org.thp.thehive.migration

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.thp.scalligraph.{EntityId, NotFoundError, RichOptionTry}
import org.thp.thehive.migration.dto.{InputAlert, InputAudit, InputCase, InputCaseTemplate}
import play.api.Logger

import scala.collection.concurrent.TrieMap
import scala.collection.{mutable, GenTraversableOnce}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MigrationStats() {
  class AVG(var count: Long = 0, var sum: Long = 0) {
    def +=(value: Long): Unit = {
      count += 1
      sum += value
    }
    def ++=(avg: AVG): Unit = {
      count += avg.count
      sum += avg.sum
    }
    def reset(): Unit = {
      count = 0
      sum = 0
    }
    def isEmpty: Boolean          = count == 0L
    override def toString: String = if (isEmpty) "0" else (sum / count).toString
  }

  class StatEntry(
      var total: Long = -1,
      var nSuccess: Int = 0,
      var nFailure: Int = 0,
      var nExist: Int = 0,
      global: AVG = new AVG,
      current: AVG = new AVG
  ) {
    def update(isSuccess: Boolean, time: Long): Unit = {
      if (isSuccess) nSuccess += 1
      else nFailure += 1
      current += time
    }

    def failure(): Unit = nFailure += 1

    def exist(): Unit = nExist += 1

    def flush(): Unit = {
      global ++= current
      current.reset()
    }

    def isEmpty: Boolean = nSuccess == 0 && nFailure == 0

    def currentStats: String = {
      val totalTxt = if (total < 0) "" else s"/$total"
      val avg      = if (current.isEmpty) "" else s"(${current}ms)"
      s"${nSuccess + nFailure}$totalTxt$avg"
    }

    def setTotal(v: Long): Unit = total = v

    override def toString: String = {
      val totalTxt = if (total < 0) s"/${nSuccess + nFailure}" else s"/$total"
      val avg      = if (global.isEmpty) "" else s" avg:${global}ms"
      val failureAndExistTxt = if (nFailure > 0 || nExist > 0) {
        val failureTxt = if (nFailure > 0) s"$nFailure failures" else ""
        val existTxt   = if (nExist > 0) s"$nExist exists" else ""
        if (nFailure > 0 && nExist > 0) s" ($failureTxt, $existTxt)" else s" ($failureTxt$existTxt)"
      } else ""
      s"$nSuccess$totalTxt$failureAndExistTxt$avg"
    }
  }

  val logger: Logger                    = Logger("org.thp.thehive.migration.Migration")
  val stats: TrieMap[String, StatEntry] = TrieMap.empty
  val startDate: Long                   = System.currentTimeMillis()
  var stage: String                     = "initialisation"

  def apply[A](name: String)(body: => Try[A]): Try[A] = {
    val start = System.currentTimeMillis()
    val ret   = body
    val time  = System.currentTimeMillis() - start
    stats.getOrElseUpdate(name, new StatEntry).update(ret.isSuccess, time)
    if (ret.isFailure)
      logger.error(s"$name creation failure: ${ret.failed.get}")
    ret
  }

  def failure(name: String, error: Throwable): Unit = {
    logger.error(s"$name creation failure: $error")
    stats.getOrElseUpdate(name, new StatEntry).failure()
  }

  def exist(name: String): Unit = stats.getOrElseUpdate(name, new StatEntry).exist()

  def flush(): Unit = stats.foreach(_._2.flush())

  def showStats(): String =
    stats
      .collect {
        case (name, entry) if !entry.isEmpty => s"$name:${entry.currentStats}"
      }
      .mkString(s"[$stage] ", " ", "")

  override def toString: String =
    stats
      .map {
        case (name, entry) => s"$name: $entry"
      }
      .toSeq
      .sorted
      .mkString(s"Stage: $stage\n", "\n", "")

  def setTotal(name: String, count: Long): Unit =
    stats.getOrElseUpdate(name, new StatEntry).setTotal(count)
}

trait MigrationOps {
  lazy val logger: Logger            = Logger(getClass)
  val migrationStats: MigrationStats = new MigrationStats
  def transactionPageSize: Int
  def threadCount: Int

  implicit class IdMappingOpsDefs(idMappings: Seq[IdMapping]) {

    def fromInput(id: String): Try[EntityId] =
      idMappings
        .find(_.inputId == id)
        .fold[Try[EntityId]](Failure(NotFoundError(s"Id $id not found")))(m => Success(m.outputId))
  }

  def groupedIterator[F, T](source: Source[F, NotUsed])(body: Iterator[F] => GenTraversableOnce[T])(implicit map: Materializer): Iterator[T] = {
    val iterator = QueueIterator(source.runWith(Sink.queue[F]))
    Iterator
      .continually(iterator)
      .takeWhile(_ => iterator.hasNext)
      .flatMap(_ => body(iterator.take(transactionPageSize)))
  }

  def migrate[TX, A](
      output: Output[TX]
  )(name: String, source: Source[Try[A], NotUsed], create: (TX, A) => Try[IdMapping], exists: (TX, A) => Boolean = (_: TX, _: A) => true)(implicit
      mat: Materializer
  ): Seq[IdMapping] =
    groupedIterator(source) { iterator =>
      output
        .withTx { tx =>
          Try {
            iterator.flatMap {
              case Success(a) if !exists(tx, a) => migrationStats(name)(create(tx, a)).toOption
              case Failure(error) =>
                migrationStats.failure(name, error)
                Nil
              case _ =>
                migrationStats.exist(name)
                Nil
            }.toBuffer
          }
        }
        .getOrElse(Nil)
    }.toSeq

  def migrateWithParent[TX, A](output: Output[TX])(
      name: String,
      parentIds: Seq[IdMapping],
      source: Source[Try[(String, A)], NotUsed],
      create: (TX, EntityId, A) => Try[IdMapping]
  )(implicit mat: Materializer): Seq[IdMapping] =
    groupedIterator(source) { iterator =>
      output
        .withTx { tx =>
          Try {
            iterator.flatMap {
              case Success((parentId, a)) =>
                parentIds
                  .fromInput(parentId)
                  .flatMap(parent => migrationStats(name)(create(tx, parent, a)))
                  .toOption
              case Failure(error) =>
                migrationStats.failure(name, error)
                Nil
              case _ =>
                migrationStats.exist(name)
                Nil
            }.toBuffer
          }
        }
        .getOrElse(Nil)
    }.toSeq

  def migrateAudit[TX](
      output: Output[TX]
  )(ids: Seq[IdMapping], source: Source[Try[(String, InputAudit)], NotUsed])(implicit mat: Materializer): Unit =
    groupedIterator(source) { audits =>
      output.withTx { tx =>
        audits.foreach {
          case Success((contextId, inputAudit)) =>
            migrationStats("Audit") {
              for {
                cid <- ids.fromInput(contextId)
                objId = inputAudit.audit.objectId.map(ids.fromInput).flip.getOrElse {
                  logger.warn(s"object Id not found in audit ${inputAudit.audit}")
                  None
                }
                _ <- output.createAudit(tx, cid, inputAudit.updateObjectId(objId))
              } yield ()
            }
            ()
          case Failure(error) =>
            migrationStats.failure("Audit", error)
        }
        Success(())
      }
      Nil
    }.foreach(_ => ())

  def migrateAWholeCaseTemplate[TX](input: Input, output: Output[TX])(
      inputCaseTemplate: InputCaseTemplate
  )(implicit mat: Materializer): Unit =
    migrationStats("CaseTemplate")(output.withTx(output.createCaseTemplate(_, inputCaseTemplate)))
      .foreach {
        case caseTemplateId @ IdMapping(inputCaseTemplateId, _) =>
          migrateWithParent(output)(
            "CaseTemplate/Task",
            Seq(caseTemplateId),
            input.listCaseTemplateTask(inputCaseTemplateId),
            output.createCaseTemplateTask
          )
          ()
      }

  def migrateWholeCaseTemplates[TX](input: Input, output: Output[TX], filter: Filter)(implicit mat: Materializer): Unit =
    groupedIterator(input.listCaseTemplate(filter)) { cts =>
      output
        .withTx { tx =>
          Try {
            cts.flatMap {
              case Success(ct) if !output.caseTemplateExists(tx, ct) => List(ct)
              case Failure(error) =>
                migrationStats.failure("CaseTemplate", error)
                Nil
              case _ =>
                migrationStats.exist("CaseTemplate")
                Nil
            }.toBuffer
          }
        }
        .getOrElse(Nil)
    }
      .foreach(migrateAWholeCaseTemplate(input, output))

  def migrateAWholeCase[TX](input: Input, output: Output[TX], filter: Filter)(
      inputCase: InputCase
  )(implicit mat: Materializer): Option[IdMapping] =
    migrationStats("Case")(output.withTx(output.createCase(_, inputCase))).map {
      case caseId @ IdMapping(inputCaseId, _) =>
        val caseTaskIds    = migrateWithParent(output)("Case/Task", Seq(caseId), input.listCaseTasks(inputCaseId), output.createCaseTask)
        val caseTaskLogIds = migrateWithParent(output)("Case/Task/Log", caseTaskIds, input.listCaseTaskLogs(inputCaseId), output.createCaseTaskLog)
        val caseObservableIds =
          migrateWithParent(output)("Case/Observable", Seq(caseId), input.listCaseObservables(inputCaseId), output.createCaseObservable)
        val jobIds = migrateWithParent(output)("Job", caseObservableIds, input.listJobs(inputCaseId), output.createJob)
        val jobObservableIds =
          migrateWithParent(output)("Case/Observable/Job/Observable", jobIds, input.listJobObservables(inputCaseId), output.createJobObservable)
        val caseEntitiesIds      = caseTaskIds ++ caseTaskLogIds ++ caseObservableIds ++ jobIds ++ jobObservableIds :+ caseId
        val actionSource         = input.listActions(caseEntitiesIds.map(_.inputId).distinct)
        val actionIds            = migrateWithParent(output)("Action", caseEntitiesIds, actionSource, output.createAction)
        val caseEntitiesAuditIds = caseEntitiesIds ++ actionIds
        val auditSource          = input.listAudits(caseEntitiesAuditIds.map(_.inputId).distinct, filter)
        migrateAudit(output)(caseEntitiesAuditIds, auditSource)
        caseId
    }.toOption

  def migrateAWholeAlert[TX](input: Input, output: Output[TX], filter: Filter)(inputAlert: InputAlert)(implicit mat: Materializer): Try[EntityId] =
    migrationStats("Alert")(output.withTx(output.createAlert(_, inputAlert))).map {
      case alertId @ IdMapping(inputAlertId, outputEntityId) =>
        val alertObservableIds =
          migrateWithParent(output)("Alert/Observable", Seq(alertId), input.listAlertObservables(inputAlertId), output.createAlertObservable)
        val alertEntitiesIds      = alertId +: alertObservableIds
        val actionSource          = input.listActions(alertEntitiesIds.map(_.inputId).distinct)
        val actionIds             = migrateWithParent(output)("Action", alertEntitiesIds, actionSource, output.createAction)
        val alertEntitiesAuditIds = alertEntitiesIds ++ actionIds
        val auditSource           = input.listAudits(alertEntitiesAuditIds.map(_.inputId).distinct, filter)
        migrateAudit(output)(alertEntitiesAuditIds, auditSource)
        outputEntityId
    }

  def migrateCasesAndAlerts[TX](input: Input, output: Output[TX], filter: Filter)(implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Future[Unit] = {
    val pendingAlertCase: mutable.Buffer[(String, EntityId)] = mutable.Buffer.empty

    val ordering: Ordering[Either[InputAlert, InputCase]] = new Ordering[Either[InputAlert, InputCase]] {
      def createdAt(x: Either[InputAlert, InputCase]): Long = x.fold(_.metaData.createdAt.getTime, _.metaData.createdAt.getTime)

      override def compare(x: Either[InputAlert, InputCase], y: Either[InputAlert, InputCase]): Int =
        java.lang.Long.compare(createdAt(x), createdAt(y)) * -1
    }

    val caseSource = input
      .listCases(filter)
      .mapConcat {
        case Success(c) if !output.withTx(tx => Try(output.caseExists(tx, c))).fold(_ => false, identity) => List(Right(c))
        case Failure(error) =>
          migrationStats.failure("Case", error)
          Nil
        case _ =>
          migrationStats.exist("Case")
          Nil
      }
    val alertSource = input
      .listAlerts(filter)
      .mapConcat {
        case Success(a) if !output.withTx(tx => Try(output.alertExists(tx, a))).fold(_ => false, identity) => List(Left(a))
        case Failure(error) =>
          migrationStats.failure("Alert", error)
          Nil
        case _ =>
          migrationStats.exist("Alert")
          Nil
      }
    caseSource
      .mergeSorted(alertSource)(ordering)
      .grouped(threadCount)
      .runFold(Seq.empty[IdMapping]) {
        case (caseIds, alertsCases) =>
          caseIds ++ alertsCases.par.flatMap {
            case Right(case0) => migrateAWholeCase(input, output, filter)(case0)
            case Left(alert) =>
              val caseId = alert.caseId.flatMap(cid => caseIds.find(_.inputId == cid)).map(_.outputId)
              migrateAWholeAlert(input, output, filter)(alert.updateCaseId(caseId.map(_.toString))).foreach { alertId =>
                if (caseId.isEmpty && alert.caseId.isDefined)
                  pendingAlertCase.synchronized(pendingAlertCase += (alert.caseId.get -> alertId))
              }
              None
            case _ => None
          }
      }
      .map { caseIds =>
        pendingAlertCase.foreach {
          case (cid, alertId) =>
            caseIds.fromInput(cid).toOption match {
              case None         => logger.warn(s"Case ID $cid not found. Link with alert $alertId is ignored")
              case Some(caseId) => output.withTx(output.linkAlertToCase(_, alertId, caseId))
            }
        }
      }
  }

  def migrate[TX](input: Input, output: Output[TX], filter: Filter)(implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Future[Unit] = {

    migrationStats.stage = "Get element count"
    input.countOrganisations(filter).foreach(count => migrationStats.setTotal("Organisation", count))
    input.countCases(filter).foreach(count => migrationStats.setTotal("Case", count))
    input.countCaseObservables(filter).foreach(count => migrationStats.setTotal("Case/Observable", count))
    input.countCaseTasks(filter).foreach(count => migrationStats.setTotal("Case/Task", count))
    input.countCaseTaskLogs(filter).foreach(count => migrationStats.setTotal("Case/Task/Log", count))
    input.countAlerts(filter).foreach(count => migrationStats.setTotal("Alert", count))
    input.countAlertObservables(filter).foreach(count => migrationStats.setTotal("Alert/Observable", count))
    input.countUsers(filter).foreach(count => migrationStats.setTotal("User", count))
    input.countCustomFields(filter).foreach(count => migrationStats.setTotal("CustomField", count))
    input.countObservableTypes(filter).foreach(count => migrationStats.setTotal("ObservableType", count))
    input.countProfiles(filter).foreach(count => migrationStats.setTotal("Profile", count))
    input.countImpactStatus(filter).foreach(count => migrationStats.setTotal("ImpactStatus", count))
    input.countResolutionStatus(filter).foreach(count => migrationStats.setTotal("ResolutionStatus", count))
    input.countCaseTemplate(filter).foreach(count => migrationStats.setTotal("CaseTemplate", count))
    input.countCaseTemplateTask(filter).foreach(count => migrationStats.setTotal("CaseTemplate/Task", count))
    input.countJobs(filter).foreach(count => migrationStats.setTotal("Job", count))
    input.countJobObservables(filter).foreach(count => migrationStats.setTotal("Job/Observable", count))
    input.countAction(filter).foreach(count => migrationStats.setTotal("Action", count))
    input.countAudit(filter).foreach(count => migrationStats.setTotal("Audit", count))

    migrationStats.stage = "Prepare database"
    Future.fromTry(output.startMigration()).flatMap { _ =>
      migrationStats.stage = "Migrate profiles"
      migrate(output)("Profile", input.listProfiles(filter), output.createProfile, output.profileExists)
      migrationStats.stage = "Migrate organisations"
      migrate(output)("Organisation", input.listOrganisations(filter), output.createOrganisation, output.organisationExists)
      migrationStats.stage = "Migrate users"
      migrate(output)("User", input.listUsers(filter), output.createUser, output.userExists)
      migrationStats.stage = "Migrate impact statuses"
      migrate(output)("ImpactStatus", input.listImpactStatus(filter), output.createImpactStatus, output.impactStatusExists)
      migrationStats.stage = "Migrate resolution statuses"
      migrate(output)("ResolutionStatus", input.listResolutionStatus(filter), output.createResolutionStatus, output.resolutionStatusExists)
      migrationStats.stage = "Migrate custom fields"
      migrate(output)("CustomField", input.listCustomFields(filter), output.createCustomField, output.customFieldExists)
      migrationStats.stage = "Migrate observable types"
      migrate(output)("ObservableType", input.listObservableTypes(filter), output.createObservableTypes, output.observableTypeExists)
      migrationStats.stage = "Migrate case templates"
      migrateWholeCaseTemplates(input, output, filter)
      migrationStats.stage = "Migrate cases and alerts"
      migrateCasesAndAlerts(input, output, filter).flatMap { _ =>
        migrationStats.stage = "Finalisation"
        Future.fromTry(output.endMigration())
      }
    }
  }
}
