package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityId
import org.thp.scalligraph.controllers.{FObject, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}

import javax.inject.{Inject, Singleton}

case class InCase(caseId: EntityId)
case class InAlert(alertId: EntityId)

case class OutputParam(from: Long, to: Long, extraData: Set[String])

object OutputParam {
  implicit val parser: FieldsParser[OutputParam] = FieldsParser[OutputParam]("OutputParam") {
    case (_, field: FObject) =>
      for {
        from      <- FieldsParser.long.on("from")(field)
        to        <- FieldsParser.long.on("to")(field)
        extraData <- FieldsParser.string.set.on("extraData")(field)
      } yield OutputParam(from, to, extraData)
  }
}

@Singleton
class TheHiveQueryExecutor @Inject() (
    appConfig: ApplicationConfig,
    alertCtrl: AlertCtrl,
    auditCtrl: AuditCtrl,
    caseCtrl: CaseCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    customFieldCtrl: CustomFieldCtrl,
    logCtrl: LogCtrl,
    observableCtrl: ObservableCtrl,
    observableTypeCtrl: ObservableTypeCtrl,
    organisationCtrl: OrganisationCtrl,
    patternCtrl: PatternCtrl,
    procedureCtrl: ProcedureCtrl,
    profileCtrl: ProfileCtrl,
    shareCtrl: ShareCtrl,
    tagCtrl: TagCtrl,
    taskCtrl: TaskCtrl,
    userCtrl: UserCtrl,
    taxonomyCtrl: TaxonomyCtrl,
    dashboardCtrl: DashboardCtrl,
    properties: Properties,
    implicit val db: Database
) extends QueryExecutor {

  lazy val controllers: Seq[QueryableCtrl] =
    Seq(
      alertCtrl,
      auditCtrl,
      caseCtrl,
      caseTemplateCtrl,
      customFieldCtrl,
      dashboardCtrl,
      logCtrl,
      observableCtrl,
      observableTypeCtrl,
      organisationCtrl,
//      pageCtrl,
      patternCtrl,
      procedureCtrl,
      profileCtrl,
      shareCtrl,
      tagCtrl,
      taskCtrl,
      userCtrl,
      taxonomyCtrl
    )

  val limitedCountThresholdConfig: ConfigItem[Long, Long] = appConfig.item[Long]("query.limitedCountThreshold", "Maximum number returned by a count")
  override val limitedCountThreshold: Long                = limitedCountThresholdConfig.get

  override val version: (Int, Int) = 1 -> 1

  override lazy val publicProperties: PublicProperties = controllers.foldLeft(properties.metaProperties)(_ ++ _.publicProperties)

  override lazy val queries: Seq[ParamQuery[_]] =
    controllers.map(_.initialQuery) ++
      controllers.map(_.getQuery) ++
      controllers.map(_.pageQuery) ++
      controllers.map(_.outputQuery) ++
      controllers.flatMap(_.extraQueries)
}
