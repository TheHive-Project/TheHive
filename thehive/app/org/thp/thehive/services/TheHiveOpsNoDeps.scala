package org.thp.thehive.services

import org.thp.scalligraph.query.PredicateOps
import org.thp.scalligraph.traversal.TraversalOps

object TheHiveOps {
  def apply[A](orgSrv: OrganisationSrv, cfSrv: CustomFieldSrv)(body: TheHiveOps => A): A =
    body(new TheHiveOps {
      override protected val organisationSrv: OrganisationSrv = orgSrv
      override protected val customFieldSrv: CustomFieldSrv   = cfSrv
    })
}
trait TheHiveOps
    extends TheHiveOpsNoDeps
    with AlertOps
    with AttachmentOps
    with AuditOps
    with CaseOps
    with CaseTemplateOps
    with CustomFieldOps
    with DashboardOps
    with DataOps
    with ConfigOps
    with ImpactStatusOps
    with LogOps
    with ObservableOps
    with ObservableTypeOps
    with OrganisationOps
    with PageOps
    with PatternOps
    with ProcedureOps
    with ProfileOps
    with ReportTagOps
    with ResolutionStatusOps
    with RoleOps
    with ShareOps
    with TagOps
    with TaskOps
    with TaxonomyOps
    with UserOps

trait TheHiveOpsNoDeps
    extends TraversalOps
    with PredicateOps
    with AttachmentOps
    with AlertOpsNoDeps // TODO CustomFields
    with AuditOpsNoDeps
    with CaseOpsNoDeps // TODO CustomFields
    with CaseTemplateOpsNoDeps
    with ConfigOps
    with CustomFieldOps
    with DashboardOps
    with DataOps
    with ImpactStatusOps
    with LogOpsNoDeps
    with ObservableOpsNoDeps
    with ObservableTypeOps
    with OrganisationOps
    with PageOps
    with PatternOps
    with ProfileOps
    with ProcedureOps
    with ResolutionStatusOps
    with ReportTagOps
    with RoleOps
    with ShareOps
    with TagOpsNoDeps
    with TaskOpsNoDeps
    with TaxonomyOps
    with UserOps
