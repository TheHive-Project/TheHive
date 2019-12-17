package org.thp.thehive.migration

import scala.util.Try
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

trait Output {
  def removeData(): Try[Unit]
  def createCase(inputCase: InputCase): Try[IdMapping]
  def createCaseObservable(caseId: String, inputObservable: InputObservable): Try[IdMapping]
  def createAlertObservable(alertId: String, inputObservable: InputObservable): Try[IdMapping]
  def createCaseTask(caseId: String, inputTask: InputTask): Try[IdMapping]
  def createCaseTaskLog(taskId: String, inputLog: InputLog): Try[IdMapping]
  def createCaseTemplate(inputCaseTemplate: InputCaseTemplate): Try[IdMapping]
  def createCaseTemplateTask(caseTemplateId: String, inputTask: InputTask): Try[IdMapping]
  def createAlert(inputAlert: InputAlert): Try[IdMapping]
  def createUser(inputUser: InputUser): Try[IdMapping]
  def createOrganisation(inputOrganisation: InputOrganisation): Try[IdMapping]
  def createCustomField(inputCustomField: InputCustomField): Try[IdMapping]
  def createObservableTypes(inputObservableType: InputObservableType): Try[IdMapping]
  def createProfile(inputProfile: InputProfile): Try[IdMapping]
  def createImpactStatus(inputImpactStatus: InputImpactStatus): Try[IdMapping]
  def createResolutionStatus(inputResolutionStatus: InputResolutionStatus): Try[IdMapping]
  def createJob(observableId: String, inputJob: InputJob): Try[IdMapping]
  def createJobObservable(jobId: String, inputObservable: InputObservable): Try[IdMapping]
  def createAction(objectId: String, inputAction: InputAction): Try[IdMapping]
}
