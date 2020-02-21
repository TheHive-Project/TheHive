package org.thp.thehive.migration

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import org.thp.thehive.migration.dto._

case class Filter(caseFromDate: Long, alertFromDate: Long, auditFromDate: Long)

object Filter {
  def fromConfig(config: Config): Filter = {
    val now           = System.currentTimeMillis()
    val maxCaseAge    = config.getDuration("maxCaseAge")
    val caseFromDate  = now - maxCaseAge.getSeconds * 1000
    val maxAlertAge   = config.getDuration("maxAlertAge")
    val alertFromDate = now - maxAlertAge.getSeconds * 1000
    val maxAuditAge   = config.getDuration("maxAuditAge")
    val auditFromDate = now - maxAuditAge.getSeconds * 1000
    Filter(caseFromDate, alertFromDate, auditFromDate)
  }
}

trait Input {
  def listOrganisations(filter: Filter): Source[InputOrganisation, NotUsed]
  def listCases(filter: Filter): Source[InputCase, NotUsed]
  def listCaseObservables(filter: Filter): Source[(String, InputObservable), NotUsed]
  def listCaseTasks(filter: Filter): Source[(String, InputTask), NotUsed]
  def listCaseTaskLogs(filter: Filter): Source[(String, InputLog), NotUsed]
  def listAlerts(filter: Filter): Source[InputAlert, NotUsed]
  def listAlertObservables(filter: Filter): Source[(String, InputObservable), NotUsed]
  def listUsers(filter: Filter): Source[InputUser, NotUsed]
  def listCustomFields(filter: Filter): Source[InputCustomField, NotUsed]
  def listObservableTypes(filter: Filter): Source[InputObservableType, NotUsed]
  def listProfiles(filter: Filter): Source[InputProfile, NotUsed]
  def listImpactStatus(filter: Filter): Source[InputImpactStatus, NotUsed]
  def listResolutionStatus(filter: Filter): Source[InputResolutionStatus, NotUsed]
  def listCaseTemplate(filter: Filter): Source[InputCaseTemplate, NotUsed]
  def listCaseTemplateTask(filter: Filter): Source[(String, InputTask), NotUsed]
  def listJobs(filter: Filter): Source[(String, InputJob), NotUsed]
  def listJobObservables(filter: Filter): Source[(String, InputObservable), NotUsed]
  def listAction(filter: Filter): Source[(String, InputAction), NotUsed]
  def listAudit(filter: Filter): Source[(String, InputAudit), NotUsed]
}
