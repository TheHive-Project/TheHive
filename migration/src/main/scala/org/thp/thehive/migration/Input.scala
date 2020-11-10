package org.thp.thehive.migration

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import org.thp.thehive.migration.dto._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Try}

case class Filter(
    caseDateRange: (Option[Long], Option[Long]),
    caseNumberRange: (Option[Int], Option[Int]),
    alertDateRange: (Option[Long], Option[Long]),
    auditDateRange: (Option[Long], Option[Long]),
    includeAlertTypes: Seq[String],
    excludeAlertTypes: Seq[String],
    includeAlertSources: Seq[String],
    excludeAlertSources: Seq[String],
    includeAuditActions: Seq[String],
    excludeAuditActions: Seq[String],
    includeAuditObjectTypes: Seq[String],
    excludeAuditObjectTypes: Seq[String]
)

object Filter {
  def fromConfig(config: Config): Filter = {
    val now = System.currentTimeMillis()
    lazy val dateFormats = Seq(
      new SimpleDateFormat("yyyyMMddHHmmss"),
      new SimpleDateFormat("yyyyMMddHHmm"),
      new SimpleDateFormat("yyyyMMddHH"),
      new SimpleDateFormat("yyyyMMdd"),
      new SimpleDateFormat("MMdd")
    )
    def parseDate(s: String): Try[Date] =
      dateFormats
        .foldLeft[Try[Date]](Failure(new ParseException(s"Unparseable date: $s", 0))) { (acc, format) =>
          acc.recoverWith { case _ => Try(format.parse(s)) }
        }
        .recoverWith {
          case _ =>
            Failure(
              new ParseException(s"Unparseable date: $s\nExpected format is ${dateFormats.map(_.toPattern).mkString("\"", "\" or \"", "\"")}", 0)
            )
        }
    def readDate(dateConfigName: String, ageConfigName: String) =
      Try(config.getString(dateConfigName))
        .flatMap(parseDate)
        .map(d => d.getTime)
        .toOption
        .orElse {
          Try {
            val age = config.getDuration(ageConfigName)
            if (age.isZero) None else Some(now - age.getSeconds * 1000)
          }.toOption.flatten
        }
    val caseFromDate    = readDate("caseFromDate", "maxCaseAge")
    val caseUntilDate   = readDate("caseUntilDate", "minCaseAge")
    val caseFromNumber  = Try(config.getInt("caseFromNumber")).toOption
    val caseUntilNumber = Try(config.getInt("caseUntilNumber")).toOption
    val alertFromDate   = readDate("alertFromDate", "maxAlertAge")
    val alertUntilDate  = readDate("alertUntilDate", "minAlertAge")
    val auditFromDate   = readDate("auditFromDate", "maxAuditAge")
    val auditUntilDate  = readDate("auditUntilDate", "minAuditAge")

    Filter(
      caseFromDate   -> caseUntilDate,
      caseFromNumber -> caseUntilNumber,
      alertFromDate  -> alertUntilDate,
      auditFromDate  -> auditUntilDate,
      config.getStringList("includeAlertTypes").asScala,
      config.getStringList("excludeAlertTypes").asScala,
      config.getStringList("includeAlertSources").asScala,
      config.getStringList("excludeAlertSources").asScala,
      config.getStringList("includeAuditActions").asScala,
      config.getStringList("excludeAuditActions").asScala,
      config.getStringList("includeAuditObjectTypes").asScala,
      config.getStringList("excludeAuditObjectTypes").asScala
    )
  }
}

trait Input {
  def listOrganisations(filter: Filter): Source[Try[InputOrganisation], NotUsed]
  def countOrganisations(filter: Filter): Future[Long]
  def listCases(filter: Filter): Source[Try[InputCase], NotUsed]
  def countCases(filter: Filter): Future[Long]
  def listCaseObservables(filter: Filter): Source[Try[(String, InputObservable)], NotUsed]
  def countCaseObservables(filter: Filter): Future[Long]
  def listCaseObservables(caseId: String): Source[Try[(String, InputObservable)], NotUsed]
  def countCaseObservables(caseId: String): Future[Long]
  def listCaseTasks(filter: Filter): Source[Try[(String, InputTask)], NotUsed]
  def countCaseTasks(filter: Filter): Future[Long]
  def listCaseTasks(caseId: String): Source[Try[(String, InputTask)], NotUsed]
  def countCaseTasks(caseId: String): Future[Long]
  def listCaseTaskLogs(filter: Filter): Source[Try[(String, InputLog)], NotUsed]
  def countCaseTaskLogs(filter: Filter): Future[Long]
  def listCaseTaskLogs(caseId: String): Source[Try[(String, InputLog)], NotUsed]
  def countCaseTaskLogs(caseId: String): Future[Long]
  def listAlerts(filter: Filter): Source[Try[InputAlert], NotUsed]
  def countAlerts(filter: Filter): Future[Long]
  def listAlertObservables(filter: Filter): Source[Try[(String, InputObservable)], NotUsed]
  def countAlertObservables(filter: Filter): Future[Long]
  def listAlertObservables(alertId: String): Source[Try[(String, InputObservable)], NotUsed]
  def countAlertObservables(alertId: String): Future[Long]
  def listUsers(filter: Filter): Source[Try[InputUser], NotUsed]
  def countUsers(filter: Filter): Future[Long]
  def listCustomFields(filter: Filter): Source[Try[InputCustomField], NotUsed]
  def countCustomFields(filter: Filter): Future[Long]
  def listObservableTypes(filter: Filter): Source[Try[InputObservableType], NotUsed]
  def countObservableTypes(filter: Filter): Future[Long]
  def listProfiles(filter: Filter): Source[Try[InputProfile], NotUsed]
  def countProfiles(filter: Filter): Future[Long]
  def listImpactStatus(filter: Filter): Source[Try[InputImpactStatus], NotUsed]
  def countImpactStatus(filter: Filter): Future[Long]
  def listResolutionStatus(filter: Filter): Source[Try[InputResolutionStatus], NotUsed]
  def countResolutionStatus(filter: Filter): Future[Long]
  def listCaseTemplate(filter: Filter): Source[Try[InputCaseTemplate], NotUsed]
  def countCaseTemplate(filter: Filter): Future[Long]
  def listCaseTemplateTask(caseTemplateId: String): Source[Try[(String, InputTask)], NotUsed]
  def countCaseTemplateTask(caseTemplateId: String): Future[Long]
  def listCaseTemplateTask(filter: Filter): Source[Try[(String, InputTask)], NotUsed]
  def countCaseTemplateTask(filter: Filter): Future[Long]
  def listJobs(caseId: String): Source[Try[(String, InputJob)], NotUsed]
  def countJobs(caseId: String): Future[Long]
  def listJobs(filter: Filter): Source[Try[(String, InputJob)], NotUsed]
  def countJobs(filter: Filter): Future[Long]
  def listJobObservables(filter: Filter): Source[Try[(String, InputObservable)], NotUsed]
  def countJobObservables(filter: Filter): Future[Long]
  def listJobObservables(caseId: String): Source[Try[(String, InputObservable)], NotUsed]
  def countJobObservables(caseId: String): Future[Long]
  def listAction(filter: Filter): Source[Try[(String, InputAction)], NotUsed]
  def countAction(filter: Filter): Future[Long]
  def listAction(entityId: String): Source[Try[(String, InputAction)], NotUsed]
  def countAction(entityId: String): Future[Long]
  def listAudit(filter: Filter): Source[Try[(String, InputAudit)], NotUsed]
  def countAudit(filter: Filter): Future[Long]
  def listAudit(entityId: String, filter: Filter): Source[Try[(String, InputAudit)], NotUsed]
  def countAudit(entityId: String, filter: Filter): Future[Long]
}
