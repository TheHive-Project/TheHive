package org.thp.thehive.migration

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.thp.scalligraph.{EntityId, NotFoundError, RichOptionTry}
import org.thp.thehive.migration.dto.{InputAlert, InputAudit, InputCase, InputCaseTemplate}
import play.api.Logger

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}
import java.text.NumberFormat
import java.util.concurrent.LinkedBlockingQueue
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.parallel.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
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
    override def toString: String = if (isEmpty) "-" else format.format(sum / count / 1000)
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
      val avg      = if (current.isEmpty) "" else s"(${current}µs)"
      s"${nSuccess + nFailure}$totalTxt$avg"
    }

    def setTotal(v: Long): Unit = total = v

    override def toString: String = {
      val totalTxt = if (total < 0) s"/${nSuccess + nFailure}" else s"/$total"
      val avg      = if (global.isEmpty) "" else s" avg:${global}µs"
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
  var stage: String                     = "initialisation"

  def apply[A](name: String)(body: => Try[A]): Try[A] = {
    val start = System.nanoTime()
    val ret   = body
    val time  = System.nanoTime() - start
    stats.getOrElseUpdate(name, new StatEntry).update(ret.isSuccess, time)
    if (ret.isFailure)
      logger.error(s"$name creation failure: ${ret.failed.get}")
    ret
  }

  def failure(name: String, error: Throwable): Unit = {
    logger.error(s"$name creation failure: $error")
    stats.getOrElseUpdate(name, new StatEntry).failure()
  }

  def exist(name: String): Unit = {
    logger.debug(s"$name already exists")
    stats.getOrElseUpdate(name, new StatEntry).exist()
  }

  def flush(): Unit = stats.foreach(_._2.flush())

  private val runtime: Runtime                 = Runtime.getRuntime
  private val gcs: Seq[GarbageCollectorMXBean] = ManagementFactory.getGarbageCollectorMXBeans.asScala.toSeq
  private var startPeriod: Long                = System.nanoTime()
  private var previousTotalGCTime: Long        = gcs.map(_.getCollectionTime).sum
  private var previousTotalGCCount: Long       = gcs.map(_.getCollectionCount).sum
  private val format: NumberFormat             = NumberFormat.getInstance()
  def memoryUsage(): String = {
    val now          = System.nanoTime()
    val totalGCTime  = gcs.map(_.getCollectionTime).sum
    val totalGCCount = gcs.map(_.getCollectionCount).sum
    val gcTime       = totalGCTime - previousTotalGCTime
    val gcCount      = totalGCCount - previousTotalGCCount
    val gcPercent    = gcTime * 100 * 1000 * 1000 / (now - startPeriod)
    previousTotalGCTime = totalGCTime
    previousTotalGCCount = totalGCCount
    startPeriod = now
    val freeMem = runtime.freeMemory
    val maxMem  = runtime.maxMemory
    val percent = 100 - (freeMem * 100 / maxMem)
    s"${format.format((maxMem - freeMem) / 1024)}/${format.format(maxMem / 1024)}KiB($percent%) GC:$gcCount (cpu:$gcPercent% ${gcTime}ms)"
  }
  def showStats(): String =
    memoryUsage() + "\n" +
      stats
        .toSeq
        .sortBy(_._1)
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

  implicit class RichSource[A](source: Source[A, NotUsed]) {
    def toIterator(capacity: Int = 3)(implicit mat: Materializer, ec: ExecutionContext): Iterator[A] = {
      val queue = new LinkedBlockingQueue[Option[A]](capacity)
      source
        .runForeach(a => queue.put(Some(a)))
        .onComplete(_ => queue.put(None))
      new Iterator[A] {
        var e: Option[A]              = queue.take()
        override def hasNext: Boolean = e.isDefined
        override def next(): A = { val r = e.get; e = queue.take(); r }
      }
    }
  }

  def mergeSortedIterator[A](it1: Iterator[A], it2: Iterator[A])(implicit ordering: Ordering[A]): Iterator[A] =
    new Iterator[A] {
      var e1: Option[A]                   = get(it1)
      var e2: Option[A]                   = get(it2)
      def get(it: Iterator[A]): Option[A] = if (it.hasNext) Some(it.next()) else None
      def emit1: A = { val r = e1.get; e1 = get(it1); r }
      def emit2: A = { val r = e2.get; e2 = get(it2); r }
      override def hasNext: Boolean = e1.isDefined || e2.isDefined
      override def next(): A =
        if (e1.isDefined)
          if (e2.isDefined)
            if (ordering.lt(e1.get, e2.get)) emit1
            else emit2
          else emit1
        else if (e2.isDefined) emit2
        else throw new NoSuchElementException()
    }

  def transactionPageSize: Int

  def threadCount: Int

  implicit class IdMappingOpsDefs(idMappings: Seq[IdMapping]) {

    def fromInput(id: String): Try[EntityId] =
      idMappings
        .find(_.inputId == id)
        .fold[Try[EntityId]](Failure(NotFoundError(s"Id $id not found")))(m => Success(m.outputId))
  }

  def migrate[TX, A](
      output: Output[TX]
  )(name: String, source: Source[Try[A], NotUsed], create: (TX, A) => Try[IdMapping], exists: (TX, A) => Boolean = (_: TX, _: A) => true)(implicit
      mat: Materializer,
      ec: ExecutionContext
  ): Seq[IdMapping] =
    source
      .toIterator()
      .grouped(transactionPageSize)
      .flatMap { elements =>
        output
          .withTx { tx =>
            Try {
              elements.flatMap {
                case Success(a) if !exists(tx, a) => migrationStats(name)(create(tx, a)).toOption
                case Failure(error) =>
                  migrationStats.failure(name, error)
                  Nil
                case _ =>
                  migrationStats.exist(name)
                  Nil
              }
            }
          }
          .getOrElse(Nil)
      }
      .toList

  def migrateWithParent[TX, A](output: Output[TX])(
      name: String,
      parentIds: Seq[IdMapping],
      source: Source[Try[(String, A)], NotUsed],
      create: (TX, EntityId, A) => Try[IdMapping]
  )(implicit mat: Materializer, ec: ExecutionContext): Seq[IdMapping] =
    source
      .toIterator()
      .grouped(transactionPageSize)
      .flatMap { elements =>
        output
          .withTx { tx =>
            Try {
              elements.flatMap {
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
              }
            }
          }
          .getOrElse(Nil)
      }
      .toList

  def migrateAudit[TX](
      output: Output[TX]
  )(ids: Seq[IdMapping], source: Source[Try[(String, InputAudit)], NotUsed])(implicit mat: Materializer, ec: ExecutionContext): Unit =
    source
      .toIterator()
      .grouped(transactionPageSize)
      .foreach { audits =>
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
        ()
      }

  def migrateAWholeCaseTemplate[TX](input: Input, output: Output[TX])(
      inputCaseTemplate: InputCaseTemplate
  )(implicit mat: Materializer, ec: ExecutionContext): Unit =
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

  def migrateWholeCaseTemplates[TX](input: Input, output: Output[TX], filter: Filter)(implicit
      mat: Materializer,
      ec: ExecutionContext
  ): Unit =
    input
      .listCaseTemplate(filter)
      .toIterator()
      .grouped(transactionPageSize)
      .foreach { cts =>
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
              }
            }
          }
          .foreach(_.foreach(migrateAWholeCaseTemplate(input, output)))
      }

  def migrateAWholeCase[TX](input: Input, output: Output[TX], filter: Filter)(
      inputCase: InputCase
  )(implicit mat: Materializer, ec: ExecutionContext): Option[IdMapping] =
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

  def migrateAWholeAlert[TX](input: Input, output: Output[TX], filter: Filter)(
      inputAlert: InputAlert
  )(implicit mat: Materializer, ec: ExecutionContext): Option[EntityId] =
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
    }.toOption

  def migrateCasesAndAlerts[TX](input: Input, output: Output[TX], filter: Filter)(implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Unit = {
    val pendingAlertCase: mutable.Buffer[(String, EntityId)] = mutable.Buffer.empty

    val ordering: Ordering[Either[InputAlert, InputCase]] = new Ordering[Either[InputAlert, InputCase]] {
      def createdAt(x: Either[InputAlert, InputCase]): Long = x.fold(_.metaData.createdAt.getTime, _.metaData.createdAt.getTime)

      override def compare(x: Either[InputAlert, InputCase], y: Either[InputAlert, InputCase]): Int =
        java.lang.Long.compare(createdAt(x), createdAt(y)) * -1
    }

    val caseIterator = input
      .listCases(filter)
      .toIterator()
      .flatMap {
        case Success(c) if !output.withTx(tx => Try(output.caseExists(tx, c))).fold(_ => false, identity) => List(Right(c))
        case Failure(error) =>
          migrationStats.failure("Case", error)
          Nil
        case _ =>
          migrationStats.exist("Case")
          Nil
      }
    val alertIterator = input
      .listAlerts(filter)
      .toIterator()
      .flatMap {
        case Success(a) if !output.withTx(tx => Try(output.alertExists(tx, a))).fold(_ => false, identity) => List(Left(a))
        case Failure(error) =>
          migrationStats.failure("Alert", error)
          Nil
        case _ =>
          migrationStats.exist("Alert")
          Nil
      }
    val caseIds = mergeSortedIterator(caseIterator, alertIterator)(ordering)
      .grouped(threadCount)
      .foldLeft[Seq[IdMapping]](Nil) {
        case (caseIds, alertsCases) =>
          caseIds ++ alertsCases
            .par
            .flatMap {
              case Right(case0) =>
                migrateAWholeCase(input, output, filter)(case0)
              case Left(alert) =>
                val caseId = alert.caseId.flatMap(cid => caseIds.find(_.inputId == cid)).map(_.outputId)
                migrateAWholeAlert(input, output, filter)(alert.updateCaseId(caseId.map(_.toString)))
                  .map { alertId =>
                    if (caseId.isEmpty && alert.caseId.isDefined)
                      pendingAlertCase.synchronized(pendingAlertCase += (alert.caseId.get -> alertId))
                    None
                  }
                None
            }
      }
    pendingAlertCase.foreach {
      case (cid, alertId) =>
        caseIds.fromInput(cid).toOption match {
          case None         => logger.warn(s"Case ID $cid not found. Link with alert $alertId is ignored")
          case Some(caseId) => output.withTx(output.linkAlertToCase(_, alertId, caseId))
        }
    }
  }

  def migrate[TX](input: Input, output: Output[TX], filter: Filter)(implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Try[Unit] = {

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
    input.countAudits(filter).foreach(count => migrationStats.setTotal("Audit", count))

    migrationStats.stage = "Prepare database"
    output.startMigration().flatMap { _ =>
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
      migrationStats.stage = "Migrate dashboards"
      migrate(output)("Dashboard", input.listDashboards(filter), output.createDashboard, output.dashboardExists)
      migrationStats.stage = "Migrate cases and alerts"
      migrateCasesAndAlerts(input, output, filter)
      migrationStats.stage = "Finalisation"
      output.endMigration()
    }
  }
}
