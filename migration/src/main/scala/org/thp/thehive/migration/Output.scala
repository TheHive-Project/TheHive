package org.thp.thehive.migration

import scala.util.Try
import org.thp.thehive.migration.dto.{
  InputAction,
  InputAlert,
  InputAudit,
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
  def profileExists(inputProfile: InputProfile): Boolean
  def createProfile(inputProfile: InputProfile): Try[IdMapping]
  def organisationExists(inputOrganisation: InputOrganisation): Boolean
  def createOrganisation(inputOrganisation: InputOrganisation): Try[IdMapping]
  def userExists(inputUser: InputUser): Boolean
  def createUser(inputUser: InputUser): Try[IdMapping]
  def customFieldExists(inputCustomField: InputCustomField): Boolean
  def createCustomField(inputCustomField: InputCustomField): Try[IdMapping]
  def observableTypeExists(inputObservableType: InputObservableType): Boolean
  def createObservableTypes(inputObservableType: InputObservableType): Try[IdMapping]
  def impactStatusExists(inputImpactStatus: InputImpactStatus): Boolean
  def createImpactStatus(inputImpactStatus: InputImpactStatus): Try[IdMapping]
  def resolutionStatusExists(inputResolutionStatus: InputResolutionStatus): Boolean
  def createResolutionStatus(inputResolutionStatus: InputResolutionStatus): Try[IdMapping]
  def caseTemplateExists(inputCaseTemplate: InputCaseTemplate): Boolean
  def createCaseTemplate(inputCaseTemplate: InputCaseTemplate): Try[IdMapping]
  def createCaseTemplateTask(caseTemplateId: String, inputTask: InputTask): Try[IdMapping]
  def caseExists(inputCase: InputCase): Boolean
  def createCase(inputCase: InputCase): Try[IdMapping]
  def createCaseObservable(caseId: String, inputObservable: InputObservable): Try[IdMapping]
  def createJob(observableId: String, inputJob: InputJob): Try[IdMapping]
  def createJobObservable(jobId: String, inputObservable: InputObservable): Try[IdMapping]
  def createCaseTask(caseId: String, inputTask: InputTask): Try[IdMapping]
  def createCaseTaskLog(taskId: String, inputLog: InputLog): Try[IdMapping]
  def alertExists(inputAlert: InputAlert): Boolean
  def createAlert(inputAlert: InputAlert): Try[IdMapping]
  def createAlertObservable(alertId: String, inputObservable: InputObservable): Try[IdMapping]
  def createAction(objectId: String, inputAction: InputAction): Try[IdMapping]
  def createAudit(contextId: String, inputAudit: InputAudit): Try[Unit]
}
