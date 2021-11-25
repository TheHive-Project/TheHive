package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperties, Query}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.scalligraph.{EntityIdOrName, RichOptionTry, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.{InputCase, InputTask}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json.{JsNumber, JsObject}
import play.api.mvc.{Action, AnyContent, Results}

import java.util.{Map => JMap}
import javax.inject.{Inject, Singleton}

@Singleton
class CaseCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv,
    userSrv: UserSrv,
    taskSrv: TaskSrv,
    val organisationSrv: OrganisationSrv,
    alertSrv: AlertSrv,
    db: Database,
    appConfig: ApplicationConfig
) extends QueryableCtrl
    with CaseRenderer {

  val limitedCountThresholdConfig: ConfigItem[Long, Long] = appConfig.item[Long]("query.limitedCountThreshold", "Maximum number returned by a count")
  val limitedCountThreshold: Long                         = limitedCountThresholdConfig.get

  override val entityName: String                 = "case"
  override val publicProperties: PublicProperties = properties.`case`
  override val initialQuery: Query =
    Query.init[Traversal.V[Case]]("listCase", (graph, authContext) => caseSrv.startTraversal(graph).visible(organisationSrv)(authContext))
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Case]](
    "getCase",
    (idOrName, graph, authContext) => caseSrv.get(idOrName)(graph).visible(organisationSrv)(authContext)
  )
  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Case], IteratorOutput](
      "page",
      {
        case (OutputParam(from, to, extraData), caseSteps, authContext) =>
          caseSteps.richPage(from, to, extraData.contains("total"), limitedCountThreshold) {
            _.richCaseWithCustomRenderer(caseStatsRenderer(extraData - "total")(authContext))(authContext)
          }
      }
    )
  override val outputQuery: Query = Query.outputWithContext[RichCase, Traversal.V[Case]]((caseSteps, authContext) => caseSteps.richCase(authContext))
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query.init[Long](
      "countCase",
      (graph, authContext) =>
        graph.indexCountQuery(s"""v."_label":Case AND v.organisationIds:${organisationSrv.currentId(graph, authContext).value}""")
    ),
    Query[Traversal.V[Case], Traversal.V[Observable]](
      "observables",
      (caseSteps, authContext) =>
        // caseSteps.observables(authContext)
        observableSrv.startTraversal(caseSteps.graph).has(_.relatedId, P.within(caseSteps._id.toSeq: _*)).visible(organisationSrv)(authContext)
    ),
    Query[Traversal.V[Case], Traversal.V[Task]](
      "tasks",
      (caseSteps, authContext) =>
        // caseSteps.tasks(authContext)
        taskSrv.startTraversal(caseSteps.graph).has(_.relatedId, P.within(caseSteps._id.toSeq: _*)).visible(organisationSrv)(authContext)
    ),
    Query[Traversal.V[Case], Traversal.V[User]]("assignableUsers", (caseSteps, authContext) => caseSteps.assignableUsers(authContext)),
    Query[Traversal.V[Case], Traversal.V[Organisation]]("organisations", (caseSteps, authContext) => caseSteps.organisations.visible(authContext)),
    Query[Traversal.V[Case], Traversal.V[Alert]](
      "alerts",
      (caseSteps, authContext) =>
//      caseSteps.alert.visible(organisationSrv)(authContext)
        alertSrv.startTraversal(caseSteps.graph).has(_.caseId, P.within(caseSteps._id.toSeq: _*)).visible(organisationSrv)(authContext)
    ),
    Query[Traversal.V[Case], Traversal.V[Share]]("shares", (caseSteps, authContext) => caseSteps.shares.visible(authContext)),
    Query[Traversal.V[Case], Traversal.V[Procedure]]("procedures", (caseSteps, _) => caseSteps.procedure),
    Query[Traversal.V[Case], Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]]](
      "linkedCases",
      (caseSteps, authContext) =>
        caseSteps
          .linkedCases(authContext)
          .domainMap {
            case (c, o) =>
              c.toJson.as[JsObject] +
                ("linkedWith" -> o.toJson) +
                ("linksCount" -> JsNumber(o.size))
          }
    )
  )

  def create: Action[AnyContent] =
    entrypoint("create case")
      .extract("case", FieldsParser[InputCase])
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
      .extract("tasks", FieldsParser[InputTask].sequence.on("tasks"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String] = request.body("caseTemplate")
        val inputCase: InputCase             = request.body("case")
        val inputTasks: Seq[InputTask]       = request.body("tasks")
        for {
          caseTemplate <- caseTemplateName.map(ct => caseTemplateSrv.get(EntityIdOrName(ct)).visible.richCaseTemplate.getOrFail("CaseTemplate")).flip
          organisation <- userSrv.current.organisations(Permissions.manageCase).get(request.organisation).getOrFail("Organisation")
          user         <- inputCase.user.fold(userSrv.current.getOrFail("User"))(userSrv.getByName(_).getOrFail("User"))
          richCase <- caseSrv.create(
            caseTemplate.fold(inputCase)(inputCase.withCaseTemplate).toCase,
            Some(user),
            organisation,
            inputCase.customFieldValues,
            caseTemplate,
            inputTasks.map(_.toTask)
          )
        } yield Results.Created(richCase.toJson)
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("get case")
      .authRoTransaction(db) { implicit request => implicit graph =>
        caseSrv
          .get(EntityIdOrName(caseIdOrNumber))
          .visible(organisationSrv)
          .richCase
          .getOrFail("Case")
          .map(richCase => Results.Ok(richCase.toJson))
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("update case")
      .extract("case", FieldsParser.update("case", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("case")
        caseSrv
          .update(_.get(EntityIdOrName(caseIdOrNumber)).can(Permissions.manageCase), propertyUpdaters)
          .map(_ => Results.NoContent)
      }

  def delete(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("delete case")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          c <-
            caseSrv
              .get(EntityIdOrName(caseIdOrNumber))
              .can(Permissions.manageCase)
              .getOrFail("Case")
          _ <- caseSrv.delete(c)
        } yield Results.NoContent
      }

  def deleteCustomField(cfId: String): Action[AnyContent] =
    entrypoint("delete a custom field")
      .authPermittedTransaction(db, Permissions.manageCase) { implicit request => implicit graph =>
        for {
          _ <-
            caseSrv
              .caseCustomFieldSrv
              .get(EntityIdOrName(cfId))
              .filter(_.outV.v[Case].can(Permissions.manageCase))
              .existsOrFail
          _ <- caseSrv.deleteCustomField(EntityIdOrName(cfId))
        } yield Results.NoContent
      }

  def merge(caseIdsOrNumbers: String): Action[AnyContent] =
    entrypoint("merge cases")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          cases <-
            caseIdsOrNumbers
              .split(',')
              .toSeq
              .toTry(c =>
                caseSrv
                  .get(EntityIdOrName(c))
                  .visible(organisationSrv)
                  .getOrFail("Case")
              )
          mergedCase <- caseSrv.merge(cases)
        } yield Results.Created(mergedCase.toJson)
      }
}
