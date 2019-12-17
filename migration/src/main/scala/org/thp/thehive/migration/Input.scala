package org.thp.thehive.migration

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.thp.thehive.migration.dto.{
  InputAction,
  InputAlert,
  InputCase,
  InputCaseTemplate,
  InputCustomField,
  InputImpactStatus,
  InputJob,
  InputLog,
  InputObservable,
  InputObservableType,
  InputOrganisation,
  InputProfile,
  InputResolutionStatus,
  InputTask,
  InputUser
}

trait Input {
  def listOrganisations: Source[InputOrganisation, NotUsed]
  def listCases: Source[InputCase, NotUsed]
  def listCaseObservables: Source[(String, InputObservable), NotUsed]
  def listCaseTasks: Source[(String, InputTask), NotUsed]
  def listCaseTaskLogs: Source[(String, InputLog), NotUsed]
  def listAlerts: Source[InputAlert, NotUsed]
  def listAlertObservables: Source[(String, InputObservable), NotUsed]
  def listUsers: Source[InputUser, NotUsed]
  def listCustomFields: Source[InputCustomField, NotUsed]
  def listObservableTypes: Source[InputObservableType, NotUsed]
  def listProfiles: Source[InputProfile, NotUsed]
  def listImpactStatus: Source[InputImpactStatus, NotUsed]
  def listResolutionStatus: Source[InputResolutionStatus, NotUsed]
  def listCaseTemplate: Source[InputCaseTemplate, NotUsed]
  def listCaseTemplateTask: Source[(String, InputTask), NotUsed]
  def listJobs: Source[(String, InputJob), NotUsed]
  def listJobObservables: Source[(String, InputObservable), NotUsed]
  def listAction: Source[(String, InputAction), NotUsed]
}
