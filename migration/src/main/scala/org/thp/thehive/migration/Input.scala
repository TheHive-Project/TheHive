package org.thp.thehive.migration

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import org.thp.thehive.migration.dto._

import scala.concurrent.Future
import scala.util.Try

case class Filter(caseFromDate: Long, alertFromDate: Long, auditFromDate: Long)

object Filter {
  def fromConfig(config: Config): Filter = {
    val now           = System.currentTimeMillis()
    val maxCaseAge    = config.getDuration("maxCaseAge")
    val caseFromDate  = if (maxCaseAge.isZero) 0L else now - maxCaseAge.getSeconds * 1000
    val maxAlertAge   = config.getDuration("maxAlertAge")
    val alertFromDate = if (maxAlertAge.isZero) 0L else now - maxAlertAge.getSeconds * 1000
    val maxAuditAge   = config.getDuration("maxAuditAge")
    val auditFromDate = if (maxAuditAge.isZero) 0L else now - maxAuditAge.getSeconds * 1000
    Filter(caseFromDate, alertFromDate, auditFromDate)
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
