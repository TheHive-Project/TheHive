package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{FObject, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._

case class OutputParam(from: Long, to: Long, withStats: Boolean)

object OutputParam {
  implicit val parser: FieldsParser[OutputParam] = FieldsParser[OutputParam]("OutputParam") {
    case (_, field: FObject) =>
      for {
        from      <- FieldsParser.long.on("from")(field)
        to        <- FieldsParser.long.on("to")(field)
        withStats <- FieldsParser.boolean.optional.on("withStats")(field)
      } yield OutputParam(from, to, withStats.getOrElse(false))
  }
}

@Singleton
class TheHiveQueryExecutor @Inject() (
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
//    logCtrl: LogCtrl,
    observableCtrl: ObservableCtrl,
    alertCtrl: AlertCtrl,
    userCtrl: UserCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
//    dashboardCtrl: DashboardCtrl,
    organisationCtrl: OrganisationCtrl,
    auditCtrl: AuditCtrl,
    @Named("with-thehive-schema") implicit val db: Database
) extends QueryExecutor {

  lazy val controllers: List[QueryableCtrl] =
    caseCtrl :: taskCtrl :: alertCtrl :: userCtrl :: caseTemplateCtrl :: organisationCtrl :: auditCtrl :: observableCtrl :: Nil
  override val version: (Int, Int) = 1 -> 1

  override lazy val publicProperties: List[PublicProperty[_, _]] = controllers.flatMap(_.publicProperties)

  override lazy val queries: Seq[ParamQuery[_]] =
    controllers.map(_.initialQuery) :::
      controllers.map(_.getQuery) :::
      controllers.map(_.pageQuery) :::
      controllers.map(_.outputQuery) :::
      controllers.flatMap(_.extraQueries)
}
