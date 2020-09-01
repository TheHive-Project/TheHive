package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputCaseTemplate
import org.thp.thehive.models.{CaseTemplate, Permissions, RichCaseTemplate}
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}
@Singleton
class CaseTemplateCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    auditSrv: AuditSrv,
    @Named("with-thehive-schema") implicit val db: Database
) extends QueryableCtrl {

  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "caseTemplate"
  override val publicProperties: List[PublicProperty[_, _]] = properties.caseTemplate
  override val initialQuery: Query =
    Query
      .init[Traversal.V[CaseTemplate]]("listCaseTemplate", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).caseTemplates)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[CaseTemplate]](
    "getCaseTemplate",
    FieldsParser[IdOrName],
    (param, graph, authContext) => caseTemplateSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[CaseTemplate], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, caseTemplateSteps, _) => caseTemplateSteps.richPage(range.from, range.to, withTotal = true)(_.richCaseTemplate)
  )
  override val outputQuery: Query = Query.output[RichCaseTemplate, Traversal.V[CaseTemplate]](_.richCaseTemplate)

  def create: Action[AnyContent] =
    entrypoint("create case template")
      .extract("caseTemplate", FieldsParser[InputCaseTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputCaseTemplate: InputCaseTemplate = request.body("caseTemplate")
        val customFields                         = inputCaseTemplate.customFields.sortBy(_.order.getOrElse(0)).map(c => c.name -> c.value)
        for {
          tasks            <- inputCaseTemplate.tasks.toTry(t => t.owner.map(userSrv.getOrFail).flip.map(t.toTask -> _))
          organisation     <- userSrv.current.organisations(Permissions.manageCaseTemplate).get(request.organisation).getOrFail("CaseTemplate")
          richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate.toCaseTemplate, organisation, inputCaseTemplate.tags, tasks, customFields)
        } yield Results.Created(richCaseTemplate.toJson)
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("get case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        caseTemplateSrv
          .get(caseTemplateNameOrId)
          .visible
          .richCaseTemplate
          .getOrFail("CaseTemplate")
          .map(richCaseTemplate => Results.Ok(richCaseTemplate.toJson))
      }

  def update(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("update case template")
      .extract("caseTemplate", FieldsParser.update("caseTemplate", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("caseTemplate")
        caseTemplateSrv
          .update(
            _.get(caseTemplateNameOrId)
              .can(Permissions.manageCaseTemplate),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def delete(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("delete case template")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          organisation <- organisationSrv.getOrFail(request.organisation)
          template     <- caseTemplateSrv.get(caseTemplateNameOrId).can(Permissions.manageCaseTemplate).getOrFail("CaseTemplate")
          _ = caseTemplateSrv.get(template).remove()
          _ <- auditSrv.caseTemplate.delete(template, organisation)
        } yield Results.Ok
      }
}
