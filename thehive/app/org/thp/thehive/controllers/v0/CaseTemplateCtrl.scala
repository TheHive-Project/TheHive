package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.scalactic.Accumulation._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{AttributeCheckingError, BadRequestError, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputCaseTemplate, InputTask}
import org.thp.thehive.models.{CaseTemplate, Permissions, RichCaseTemplate}
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Failure
@Singleton
class CaseTemplateCtrl @Inject() (
    override val entrypoint: Entrypoint,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    auditSrv: AuditSrv,
    override val publicData: PublicCaseTemplate,
    @Named("with-thehive-schema") implicit override val db: Database,
    @Named("v0") override val queryExecutor: QueryExecutor
) extends QueryCtrl {
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
      .extract("caseTemplate", FieldsParser.update("caseTemplate", publicData.publicProperties))
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

@Singleton
class PublicCaseTemplate @Inject() (
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    customFieldSrv: CustomFieldSrv,
    userSrv: UserSrv,
    taskSrv: TaskSrv
) extends PublicData {
  lazy val logger: Logger         = Logger(getClass)
  override val entityName: String = "caseTemplate"
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
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[CaseTemplate]
    .property("name", UMapping.string)(_.field.updatable)
    .property("displayName", UMapping.string)(_.field.updatable)
    .property("titlePrefix", UMapping.string.optional)(_.field.updatable)
    .property("description", UMapping.string.optional)(_.field.updatable)
    .property("severity", UMapping.int.optional)(_.field.updatable)
    .property("tags", UMapping.string.set)(
      _.select(_.tags.displayName)
        .custom { (_, value, vertex, _, graph, authContext) =>
          caseTemplateSrv
            .get(vertex)(graph)
            .getOrFail("CaseTemplate")
            .flatMap(caseTemplate => caseTemplateSrv.updateTagNames(caseTemplate, value)(graph, authContext))
            .map(_ => Json.obj("tags" -> value))
        }
    )
    .property("flag", UMapping.boolean)(_.field.updatable)
    .property("tlp", UMapping.int.optional)(_.field.updatable)
    .property("pap", UMapping.int.optional)(_.field.updatable)
    .property("summary", UMapping.string.optional)(_.field.updatable)
    .property("user", UMapping.string)(_.field.updatable)
    .property("customFields", UMapping.jsonNative)(_.subSelect {
      case (FPathElem(_, FPathElem(name, _)), caseTemplateSteps) => caseTemplateSteps.customFields(name).jsonValue
      case (_, caseTemplateSteps)                                => caseTemplateSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
    }.custom {
      case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
        for {
          c <- caseTemplateSrv.getByIds(vertex.id.toString)(graph).getOrFail("CaseTemplate")
          _ <- caseTemplateSrv.setOrCreateCustomField(c, name, Some(value), None)(graph, authContext)
        } yield Json.obj(s"customFields.$name" -> value)
      case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
        for {
          c   <- caseTemplateSrv.get(vertex)(graph).getOrFail("CaseTemplate")
          cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(n)(graph).map(_ -> v) }
          _   <- caseTemplateSrv.updateCustomField(c, cfv)(graph, authContext)
        } yield Json.obj("customFields" -> values)
      case _ => Failure(BadRequestError("Invalid custom fields format"))
    })
    .property("tasks", UMapping.jsonNative.sequence)(_.select(_.tasks.richTask.domainMap(_.toJson)).custom { //  FIXME select the correct mapping
      (_, value, vertex, _, graph, authContext) =>
        val fp = FieldsParser[InputTask]

        caseTemplateSrv.get(vertex)(graph).tasks.remove()
        for {
          caseTemplate <- caseTemplateSrv.getByIds(vertex.id.toString)(graph).getOrFail("CaseTemplate")
          tasks        <- value.validatedBy(t => fp(Field(t))).badMap(AttributeCheckingError(_)).toTry
          createdTasks <-
            tasks
              .toTry(t => t.owner.map(userSrv.getOrFail(_)(graph)).flip.flatMap(owner => taskSrv.create(t.toTask, owner)(graph, authContext)))
          _ <- createdTasks.toTry(t => caseTemplateSrv.addTask(caseTemplate, t.task)(graph, authContext))
        } yield Json.obj("tasks" -> createdTasks.map(_.toJson))
    })
    .build

}
