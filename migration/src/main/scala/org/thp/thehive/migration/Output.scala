package org.thp.thehive.migration

import org.thp.scalligraph.EntityId
import org.thp.thehive.migration.dto._

import scala.util.Try

trait Output[TX] {
  def startMigration(): Try[Unit]
  def endMigration(): Try[Unit]
  def withTx[R](body: TX => Try[R]): Try[R]
  def profileExists(tx: TX, inputProfile: InputProfile): Boolean
  def createProfile(tx: TX, inputProfile: InputProfile): Try[IdMapping]
  def organisationExists(tx: TX, inputOrganisation: InputOrganisation): Boolean
  def createOrganisation(tx: TX, inputOrganisation: InputOrganisation): Try[IdMapping]
  def userExists(tx: TX, inputUser: InputUser): Boolean
  def createUser(tx: TX, inputUser: InputUser): Try[IdMapping]
  def customFieldExists(tx: TX, inputCustomField: InputCustomField): Boolean
  def createCustomField(tx: TX, inputCustomField: InputCustomField): Try[IdMapping]
  def observableTypeExists(tx: TX, inputObservableType: InputObservableType): Boolean
  def createObservableTypes(tx: TX, inputObservableType: InputObservableType): Try[IdMapping]
  def impactStatusExists(tx: TX, inputImpactStatus: InputImpactStatus): Boolean
  def createImpactStatus(tx: TX, inputImpactStatus: InputImpactStatus): Try[IdMapping]
  def resolutionStatusExists(tx: TX, inputResolutionStatus: InputResolutionStatus): Boolean
  def createResolutionStatus(tx: TX, inputResolutionStatus: InputResolutionStatus): Try[IdMapping]
  def caseTemplateExists(tx: TX, inputCaseTemplate: InputCaseTemplate): Boolean
  def createCaseTemplate(tx: TX, inputCaseTemplate: InputCaseTemplate): Try[IdMapping]
  def createCaseTemplateTask(tx: TX, caseTemplateId: EntityId, inputTask: InputTask): Try[IdMapping]
  def caseExists(tx: TX, inputCase: InputCase): Boolean
  def createCase(tx: TX, inputCase: InputCase): Try[IdMapping]
  def createCaseObservable(tx: TX, caseId: EntityId, inputObservable: InputObservable): Try[IdMapping]
  def createJob(tx: TX, observableId: EntityId, inputJob: InputJob): Try[IdMapping]
  def createJobObservable(tx: TX, jobId: EntityId, inputObservable: InputObservable): Try[IdMapping]
  def createCaseTask(tx: TX, caseId: EntityId, inputTask: InputTask): Try[IdMapping]
  def createCaseTaskLog(tx: TX, taskId: EntityId, inputLog: InputLog): Try[IdMapping]
  def alertExists(tx: TX, inputAlert: InputAlert): Boolean
  def createAlert(tx: TX, inputAlert: InputAlert): Try[IdMapping]
  def linkAlertToCase(tx: TX, alertId: EntityId, caseId: EntityId): Try[Unit]
  def createAlertObservable(tx: TX, alertId: EntityId, inputObservable: InputObservable): Try[IdMapping]
  def createAction(tx: TX, objectId: EntityId, inputAction: InputAction): Try[IdMapping]
  def createAudit(tx: TX, contextId: EntityId, inputAudit: InputAudit): Try[Unit]
}
