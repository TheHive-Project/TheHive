package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FObject, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.utils.RichType
import org.thp.thehive.models.Audit
import org.thp.thehive.services.AuditOps._

import scala.reflect.runtime.{universe => ru}

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

class TheHiveQueryExecutor(
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
      taxonomyCtrl,
      userCtrl
    )

  val limitedCountThresholdConfig: ConfigItem[Long, Long] = appConfig.item[Long]("query.limitedCountThreshold", "Maximum number returned by a count")
  override val limitedCountThreshold: Long                = limitedCountThresholdConfig.get

  override val version: (Int, Int) = 1 -> 1

  override lazy val publicProperties: PublicProperties = controllers.foldLeft(properties.metaProperties)(_ ++ _.publicProperties)

  override lazy val queries: Seq[ParamQuery[_]] =
    controllers.map(_.initialQuery) ++
      controllers.map(_.getQuery) ++
      controllers.map(_.pageQuery(limitedCountThreshold)) ++ // FIXME the value of limitedCountThreshold is read only once. The value is not updated.
      controllers.map(_.outputQuery) ++
      controllers.flatMap(_.extraQueries) :+
      new Query {
        override val name: String = "audits"
        override def checkFrom(t: ru.Type): Boolean =
          RichType.getTypeArgs(t, ru.typeOf[Traversal[_, _, _]]).drop(1).headOption.exists(_ =:= ru.typeOf[Vertex])
        override def toType(t: ru.Type): ru.Type                                                     = ru.typeOf[Traversal.V[Audit]]
        override def apply(param: Unit, fromType: ru.Type, from: Any, authContext: AuthContext): Any = from.asInstanceOf[Traversal.V[Any]].audits
      } :+
      new Query {
        override val name: String = "auditsFromContext"
        override def checkFrom(t: ru.Type): Boolean =
          RichType.getTypeArgs(t, ru.typeOf[Traversal[_, _, _]]).drop(1).headOption.exists(_ =:= ru.typeOf[Vertex])
        override def toType(t: ru.Type): ru.Type = ru.typeOf[Traversal.V[Audit]]
        override def apply(param: Unit, fromType: ru.Type, from: Any, authContext: AuthContext): Any =
          from.asInstanceOf[Traversal.V[Any]].auditsFromContext
      }
}
