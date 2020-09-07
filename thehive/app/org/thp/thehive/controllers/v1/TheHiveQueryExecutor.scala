package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{FObject, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._

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
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
    logCtrl: LogCtrl,
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
    caseCtrl :: taskCtrl :: alertCtrl :: userCtrl :: caseTemplateCtrl :: organisationCtrl :: auditCtrl :: observableCtrl :: logCtrl :: Nil
  override val version: (Int, Int) = 1 -> 1

  def metaProperties: PublicProperties =
    PublicPropertyListBuilder[Product]
      .property("_createdBy", UMapping.string)(_.field.readonly)
      .property("_createdAt", UMapping.date)(_.field.readonly)
      .property("_updatedBy", UMapping.string.optional)(_.field.readonly)
      .property("_updatedAt", UMapping.date.optional)(_.field.readonly)
      .build

  override lazy val publicProperties: PublicProperties = controllers.foldLeft(metaProperties)(_ ++ _.publicProperties)

  override lazy val queries: Seq[ParamQuery[_]] =
    controllers.map(_.initialQuery) :::
      controllers.map(_.getQuery) :::
      controllers.map(_.pageQuery) :::
      controllers.map(_.outputQuery) :::
      controllers.flatMap(_.extraQueries)
}
