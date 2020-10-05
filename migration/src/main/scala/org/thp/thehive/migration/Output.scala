package org.thp.thehive.migration

import org.thp.scalligraph.EntityId
import org.thp.thehive.migration.dto._

import scala.util.Try

trait Output {
  def startMigration(): Try[Unit]
  def endMigration(): Try[Unit]
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
  def createCaseTemplateTask(caseTemplateId: EntityId, inputTask: InputTask): Try[IdMapping]
  def caseExists(inputCase: InputCase): Boolean
  def createCase(inputCase: InputCase): Try[IdMapping]
  def createCaseObservable(caseId: EntityId, inputObservable: InputObservable): Try[IdMapping]
  def createJob(observableId: EntityId, inputJob: InputJob): Try[IdMapping]
  def createJobObservable(jobId: EntityId, inputObservable: InputObservable): Try[IdMapping]
  def createCaseTask(caseId: EntityId, inputTask: InputTask): Try[IdMapping]
  def createCaseTaskLog(taskId: EntityId, inputLog: InputLog): Try[IdMapping]
  def alertExists(inputAlert: InputAlert): Boolean
  def createAlert(inputAlert: InputAlert): Try[IdMapping]
  def createAlertObservable(alertId: EntityId, inputObservable: InputObservable): Try[IdMapping]
  def createAction(objectId: EntityId, inputAction: InputAction): Try[IdMapping]
  def createAudit(contextId: EntityId, inputAudit: InputAudit): Try[Unit]
}
