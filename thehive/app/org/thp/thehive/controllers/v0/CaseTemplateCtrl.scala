package org.thp.thehive.controllers.v0

import org.scalactic.Accumulation._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, IndexType, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{AttributeCheckingError, BadRequestError, EntityId, EntityIdOrName, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputCaseTemplate, InputTask}
import org.thp.thehive.models.{CaseTemplate, Permissions, RichCaseTemplate, Task}
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Failure

class CaseTemplateCtrl(
    override val entrypoint: Entrypoint,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    auditSrv: AuditSrv,
    override val publicData: PublicCaseTemplate,
    implicit override val db: Database,
    override val queryExecutor: QueryExecutor
) extends QueryCtrl
    with TheHiveOpsNoDeps {
  def create: Action[AnyContent] =
    entrypoint("create case template")
      .extract("caseTemplate", FieldsParser[InputCaseTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputCaseTemplate: InputCaseTemplate = request.body("caseTemplate")
        val tasks                                = inputCaseTemplate.tasks.map(_.toTask)
        for {
          organisation <- userSrv.current.organisations(Permissions.manageCaseTemplate).get(request.organisation).getOrFail("CaseTemplate")
          richCaseTemplate <-
            caseTemplateSrv.create(inputCaseTemplate.toCaseTemplate, organisation, tasks, inputCaseTemplate.customFields.map(_.toV1))
        } yield Results.Created(richCaseTemplate.toJson)
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("get case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        caseTemplateSrv
          .get(EntityIdOrName(caseTemplateNameOrId))
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
            _.get(EntityIdOrName(caseTemplateNameOrId))
              .can(Permissions.manageCaseTemplate),
            propertyUpdaters
          )
          .flatMap {
            case (caseTemplates, _) =>
              caseTemplates
                .richCaseTemplate
                .getOrFail("CaseTemplate")
                .map(richCaseTemplate => Results.Ok(richCaseTemplate.toJson))
          }
      }

  def delete(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("delete case template")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          organisation <- organisationSrv.getOrFail(request.organisation)
          template     <- caseTemplateSrv.get(EntityIdOrName(caseTemplateNameOrId)).can(Permissions.manageCaseTemplate).getOrFail("CaseTemplate")
          _ = caseTemplateSrv.get(template).remove()
          _ <- auditSrv.caseTemplate.delete(template, organisation)
        } yield Results.Ok
      }
}

class PublicCaseTemplate(
    caseTemplateSrv: CaseTemplateSrv,
    searchSrv: SearchSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv
) extends PublicData
    with TheHiveOps {
  lazy val logger: Logger         = Logger(getClass)
  override val entityName: String = "caseTemplate"
  override val initialQuery: Query =
    Query
      .init[Traversal.V[CaseTemplate]]("listCaseTemplate", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).caseTemplates)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[CaseTemplate]](
    "getCaseTemplate",
    (idOrName, graph, authContext) => caseTemplateSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[CaseTemplate], IteratorOutput](
    "page",
    (range, caseTemplateSteps, _) => caseTemplateSteps.richPage(range.from, range.to, withTotal = true)(_.richCaseTemplate)
  )
  override val outputQuery: Query = Query.output[RichCaseTemplate, Traversal.V[CaseTemplate]](_.richCaseTemplate)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[CaseTemplate], Traversal.V[Task]]("tasks", (caseTemplateSteps, _) => caseTemplateSteps.tasks)
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[CaseTemplate]
    .property("keyword", UMapping.string)(
      _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
        .filter[String](IndexType.fulltext) {
          case (_, t, _, Right(p))   => searchSrv("CaseTemplate", p.getValue)(t)
          case (_, t, _, Left(true)) => t
          case (_, t, _, _)          => t.empty
        }
        .readonly
    )
    .property("name", UMapping.string)(_.field.updatable)
    .property("displayName", UMapping.string)(_.field.updatable)
    .property("titlePrefix", UMapping.string.optional)(_.field.updatable)
    .property("description", UMapping.string.optional)(_.field.updatable)
    .property("severity", UMapping.int.optional)(_.field.updatable)
    .property("tags", UMapping.string.sequence)(
      _.field
        .custom { (_, value, vertex, graph, authContext) =>
          caseTemplateSrv
            .get(vertex)(graph)
            .getOrFail("CaseTemplate")
            .flatMap(caseTemplate => caseTemplateSrv.updateTags(caseTemplate, value.toSet)(graph, authContext))
            .map(_ => Json.obj("tags" -> value))
        }
    )
    .property("flag", UMapping.boolean)(_.field.updatable)
    .property("tlp", UMapping.int.optional)(_.field.updatable)
    .property("pap", UMapping.int.optional)(_.field.updatable)
    .property("summary", UMapping.string.optional)(_.field.updatable)
    .property("customFields", UMapping.jsonNative)(_.subSelect {
      case (FPathElem(_, FPathElem(isOrName, _)), caseTemplateSteps) =>
        caseTemplateSteps.customFieldJsonValue(EntityIdOrName(isOrName))
      case (_, caseTemplateSteps) =>
        caseTemplateSteps.richCustomFields.fold.domainMap(_.toJson)
    }
      .filter[JsValue](IndexType.standard) {
        case (FPathElem(_, FPathElem(name, _)), caseTemplateTraversal, _, predicate) =>
          predicate match {
            case Right(p) =>
              val elementIds = customFieldSrv
                .getByName(name)(caseTemplateTraversal.graph)
                .value(_.`type`)
                .headOption
                .fold[Seq[EntityId]](Nil)(_.filter(customFieldValueSrv.startTraversal(caseTemplateTraversal.graph), p).value(_.elementId).toSeq)
              caseTemplateTraversal.hasId(elementIds: _*)
            case Left(true)  => caseTemplateTraversal.hasCustomField(EntityIdOrName(name))
            case Left(false) => caseTemplateTraversal.hasNotCustomField(EntityIdOrName(name))
          }
        case (_, caseTraversal, _, _) => caseTraversal.empty
      }
      .custom {
        case (FPathElem(_, FPathElem(idOrName, _)), jsonValue, vertex, graph, authContext) =>
          EntityIdOrName(idOrName).fold(
            valueId => // update the value
              caseTemplateSrv
                .updateCustomField(EntityId(vertex.id()), valueId, jsonValue)(graph)
                .map(cfv => Json.obj(s"customField.${cfv.name}" -> jsonValue)),
            name => // update or add new custom field
              for {
                ct             <- caseTemplateSrv.getOrFail(vertex)(graph)
                cf             <- customFieldSrv.getByName(name)(graph).getOrFail("CustomField")
                (value, order) <- cf.`type`.parseValue(jsonValue)
                _              <- caseTemplateSrv.updateOrCreateCustomField(ct, cf, value, order)(graph, authContext)
              } yield Json.obj(s"customField.$name" -> jsonValue)
          )
        case (FPathElem(_, FPathEmpty), jsonValue: JsObject, vertex, graph, authContext) =>
          for {
            ct <- caseTemplateSrv.getOrFail(vertex)(graph)
            cfv <- jsonValue.fields.toTry {
              case (n, v) => customFieldSrv.getOrFail(EntityIdOrName(n))(graph).flatMap(cf => cf.`type`.parseValue(v).map(cf -> _))
            }
            _ <- cfv.toTry {
              case (cf, (value, order)) => caseTemplateSrv.updateOrCreateCustomField(ct, cf, value, order)(graph, authContext)
            }
          } yield Json.obj("customFields" -> jsonValue)
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })
    .property("tasks", UMapping.jsonNative.sequence)(
      _.select(_.tasks.richTaskWithoutActionRequired.domainMap(_.toJson)).custom { //  FIXME select the correct mapping
        (_, value, vertex, graph, authContext) =>
          val fp = FieldsParser[InputTask]

          caseTemplateSrv.get(vertex)(graph).tasks.remove()
          for {
            caseTemplate <- caseTemplateSrv.get(vertex)(graph).getOrFail("CaseTemplate")
            tasks        <- value.validatedBy(t => fp(Field(t))).badMap(AttributeCheckingError(_)).toTry
            createdTasks <- tasks.toTry(task => caseTemplateSrv.createTask(caseTemplate, task.toTask)(graph, authContext))
          } yield Json.obj("tasks" -> createdTasks.map(_.toJson))
      }
    )
    .build

}
