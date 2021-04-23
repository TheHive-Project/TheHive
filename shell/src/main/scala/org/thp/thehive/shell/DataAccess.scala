package org.thp.thehive.shell

import org.thp.scalligraph.traversal.Traversal.V
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.thehive.models._
import org.thp.thehive.services._

import javax.inject.{Inject, Singleton}

trait DataAccess {
  def alert: Traversal.V[Alert]
  def audit: Traversal.V[Audit]
  def `case`: Traversal.V[Case]
  def caseTemplate: Traversal.V[CaseTemplate]
  def customField: Traversal.V[CustomField]
  def dashboard: Traversal.V[Dashboard]
  def log: Traversal.V[Log]
  def observable: Traversal.V[Observable]
  def observableType: Traversal.V[ObservableType]
  def organisation: Traversal.V[Organisation]
  //    def page: Traversal.V[Page]
  def pattern: Traversal.V[Pattern]
  def procedure: Traversal.V[Procedure]
  def profile: Traversal.V[Profile]
  def share: Traversal.V[Share]
  def tag: Traversal.V[Tag]
  def task: Traversal.V[Task]
  def taxonomy: Traversal.V[Taxonomy]
  def user: Traversal.V[User]
}

class DataAccessProvider (
    alertSrv: AlertSrv,
    auditSrv: AuditSrv,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    customFieldSrv: CustomFieldSrv,
    dashboardSrv: DashboardSrv,
    logSrv: LogSrv,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    organisationSrv: OrganisationSrv,
    //    pageSrv: PageSrv,
    patternSrv: PatternSrv,
    procedureSrv: ProcedureSrv,
    profileSrv: ProfileSrv,
    tagSrv: TagSrv,
    taskSrv: TaskSrv,
    taxonomySrv: TaxonomySrv,
    shareSrv: ShareSrv,
    userSrv: UserSrv
) {
  def get(implicit graph: Graph): DataAccess =
    new DataAccess {
      override def alert: Traversal.V[Alert]                   = alertSrv.startTraversal
      override def audit: Traversal.V[Audit]                   = auditSrv.startTraversal
      override def `case`: Traversal.V[Case]                   = caseSrv.startTraversal
      override def caseTemplate: Traversal.V[CaseTemplate]     = caseTemplateSrv.startTraversal
      override def customField: Traversal.V[CustomField]       = customFieldSrv.startTraversal
      override def dashboard: Traversal.V[Dashboard]           = dashboardSrv.startTraversal
      override def log: Traversal.V[Log]                       = logSrv.startTraversal
      override def observable: Traversal.V[Observable]         = observableSrv.startTraversal
      override def observableType: Traversal.V[ObservableType] = observableTypeSrv.startTraversal
      override def organisation: Traversal.V[Organisation]     = organisationSrv.startTraversal
      override def pattern: Traversal.V[Pattern]               = patternSrv.startTraversal
      override def procedure: Traversal.V[Procedure]           = procedureSrv.startTraversal
      override def profile: Traversal.V[Profile]               = profileSrv.startTraversal
      override def tag: Traversal.V[Tag]                       = tagSrv.startTraversal
      override def task: Traversal.V[Task]                     = taskSrv.startTraversal
      override def taxonomy: Traversal.V[Taxonomy]             = taxonomySrv.startTraversal
      override def share: Traversal.V[Share]                   = shareSrv.startTraversal
      override def user: Traversal.V[User]                     = userSrv.startTraversal
    }
}
