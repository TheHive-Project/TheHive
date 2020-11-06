package org.thp.thehive.controllers.v0

import java.lang.{Long => JLong}
import java.util.Date

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FPathElem, FPathEmpty, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.scalligraph.{RichSeq, _}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputCase, InputTask}
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.CustomFieldOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success}

@Singleton
class CaseCtrl @Inject() (
    override val entrypoint: Entrypoint,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    tagSrv: TagSrv,
    userSrv: UserSrv,
    override val publicData: PublicCase,
    @Named("v0") override val queryExecutor: QueryExecutor,
    @Named("with-thehive-schema") implicit override val db: Database
) extends CaseRenderer
    with QueryCtrl {
  def create: Action[AnyContent] =
    entrypoint("create case")
      .extract("case", FieldsParser[InputCase])
      .extract("tasks", FieldsParser[InputTask].sequence.on("tasks"))
      .extract("caseTemplate", FieldsParser[String].optional.on("template"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String] = request.body("caseTemplate")
        val inputCase: InputCase             = request.body("case")
        val inputTasks: Seq[InputTask]       = request.body("tasks")
        val customFields                     = inputCase.customFields.map(c => InputCustomFieldValue(c.name, c.value, c.order))
        for {
          organisation <-
            userSrv
              .current
              .organisations(Permissions.manageCase)
              .get(request.organisation)
              .orFail(AuthorizationError("Operation not permitted"))
          caseTemplate <- caseTemplateName.map(ct => caseTemplateSrv.get(EntityIdOrName(ct)).visible.richCaseTemplate.getOrFail("CaseTemplate")).flip
          user         <- inputCase.user.map(u => userSrv.get(EntityIdOrName(u)).visible.getOrFail("User")).flip
          tags         <- inputCase.tags.toTry(tagSrv.getOrCreate)
          tasks        <- inputTasks.toTry(t => t.owner.map(o => userSrv.getOrFail(EntityIdOrName(o))).flip.map(owner => t.toTask -> owner))
          richCase <- caseSrv.create(
            caseTemplate.fold(inputCase)(inputCase.withCaseTemplate).toCase,
            user,
            organisation,
            tags.toSet,
            customFields,
            caseTemplate,
            tasks
          )
        } yield Results.Created(richCase.toJson)
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("get case")
      .extract("stats", FieldsParser.boolean.optional.on("nstats"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val c = caseSrv
          .get(EntityIdOrName(caseIdOrNumber))
          .visible
        val stats: Option[Boolean] = request.body("stats")
        if (stats.contains(true))
          c.richCaseWithCustomRenderer(caseStatsRenderer(request))
            .getOrFail("Case")
            .map {
              case (richCase, stats) => Results.Ok(richCase.toJson.as[JsObject] + ("stats" -> stats))
            }
        else
          c.richCase
            .getOrFail("Case")
            .map(richCase => Results.Ok(richCase.toJson))
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("update case")
      .extract("case", FieldsParser.update("case", publicData.publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("case")
        caseSrv
          .update(
            _.get(EntityIdOrName(caseIdOrNumber))
              .can(Permissions.manageCase),
            propertyUpdaters
          )
          .flatMap {
            case (caseSteps, _) =>
              caseSteps
                .richCase
                .getOrFail("Case")
                .map(richCase => Results.Ok(richCase.toJson))
          }
      }

  def bulkUpdate: Action[AnyContent] =
    entrypoint("update case")
      .extract("case", FieldsParser.update("case", publicData.publicProperties))
      .extract("idsOrNumbers", FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("case")
        val idsOrNumbers: Seq[String]              = request.body("idsOrNumbers")
        idsOrNumbers
          .toTry { caseIdOrNumber =>
            caseSrv
              .update(
                _.get(EntityIdOrName(caseIdOrNumber)).can(Permissions.manageCase),
                propertyUpdaters
              )
              .flatMap {
                case (caseSteps, _) =>
                  caseSteps
                    .richCase
                    .getOrFail("Case")
              }
          }
          .map(richCases => Results.Ok(richCases.toJson))
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
          _ <- caseSrv.remove(c)
        } yield Results.NoContent
      }

  def merge(caseIdsOrNumbers: String): Action[AnyContent] =
    entrypoint("merge cases")
      .authTransaction(db) { implicit request => implicit graph =>
        caseIdsOrNumbers
          .split(',')
          .toSeq
          .toTry(c =>
            caseSrv
              .get(EntityIdOrName(c))
              .visible
              .getOrFail("Case")
          )
          .map { cases =>
            val mergedCase = caseSrv.merge(cases)
            Results.Ok(mergedCase.toJson)
          }
      }

  def linkedCases(caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("case link")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val relatedCases = caseSrv
          .get(EntityIdOrName(caseIdOrNumber))
          .visible
          .linkedCases
          .map {
            case (c, o) =>
              c.toJson.as[JsObject] +
                ("linkedWith" -> o.toJson) +
                ("linksCount" -> JsNumber(o.size))
          }

        Success(Results.Ok(JsArray(relatedCases)))
      }
}

@Singleton
class PublicCase @Inject() (
    caseSrv: CaseSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    customFieldSrv: CustomFieldSrv,
    @Named("with-thehive-schema") implicit val db: Database
) extends PublicData
    with CaseRenderer {
  override val entityName: String = "case"
  override val initialQuery: Query =
    Query.init[Traversal.V[Case]]("listCase", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).cases)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Case]](
    "getCase",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => caseSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Case], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    {
      case (OutputParam(from, to, withStats, _), caseSteps, authContext) =>
        caseSteps
          .richPage(from, to, withTotal = true) {
            case c if withStats =>
              c.richCaseWithCustomRenderer(caseStatsRenderer(authContext))(authContext)
            case c =>
              c.richCase(authContext).domainMap(_ -> JsObject.empty)
          }
    }
  )
  override val outputQuery: Query = Query.outputWithContext[RichCase, Traversal.V[Case]]((caseSteps, authContext) => caseSteps.richCase(authContext))
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Case], Traversal.V[Observable]]("observables", (caseSteps, authContext) => caseSteps.observables(authContext)),
    Query[Traversal.V[Case], Traversal.V[Task]]("tasks", (caseSteps, authContext) => caseSteps.tasks(authContext))
  )
  override val publicProperties: PublicProperties =
    PublicPropertyListBuilder[Case]
      .property("caseId", UMapping.int)(_.rename("number").readonly)
      .property("title", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("severity", UMapping.int)(_.field.updatable)
      .property("startDate", UMapping.date)(_.field.updatable)
      .property("endDate", UMapping.date.optional)(_.field.updatable)
      .property("tags", UMapping.string.set)(
        _.select(_.tags.displayName)
          .filter((_, cases) =>
            cases
              .tags
              .graphMap[String, String, Converter.Identity[String]](
                { v =>
                  val namespace = UMapping.string.getProperty(v, "namespace")
                  val predicate = UMapping.string.getProperty(v, "predicate")
                  val value     = UMapping.string.optional.getProperty(v, "value")
                  Tag(namespace, predicate, value, None, 0).toString
                },
                Converter.identity[String]
              )
          )
          .converter(_ => Converter.identity[String])
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseSrv
              .get(vertex)(graph)
              .getOrFail("Case")
              .flatMap(`case` => caseSrv.updateTagNames(`case`, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("pap", UMapping.int)(_.field.updatable)
      .property("status", UMapping.enum[CaseStatus.type])(_.field.updatable)
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("owner", UMapping.string.optional)(_.select(_.user.value(_.login)).custom { (_, login, vertex, _, graph, authContext) =>
        for {
          c    <- caseSrv.get(vertex)(graph).getOrFail("Case")
          user <- login.map(u => userSrv.get(EntityIdOrName(u))(graph).getOrFail("User")).flip
          _ <- user match {
            case Some(u) => caseSrv.assign(c, u)(graph, authContext)
            case None    => caseSrv.unassign(c)(graph, authContext)
          }
        } yield Json.obj("owner" -> user.map(_.login))
      })
      .property("resolutionStatus", UMapping.string.optional)(_.select(_.resolutionStatus.value(_.value)).custom {
        (_, resolutionStatus, vertex, _, graph, authContext) =>
          for {
            c <- caseSrv.get(vertex)(graph).getOrFail("Case")
            _ <- resolutionStatus match {
              case Some(s) => caseSrv.setResolutionStatus(c, s)(graph, authContext)
              case None    => caseSrv.unsetResolutionStatus(c)(graph, authContext)
            }
          } yield Json.obj("resolutionStatus" -> resolutionStatus)
      })
      .property("impactStatus", UMapping.string.optional)(_.select(_.impactStatus.value(_.value)).custom {
        (_, impactStatus, vertex, _, graph, authContext) =>
          for {
            c <- caseSrv.get(vertex)(graph).getOrFail("Case")
            _ <- impactStatus match {
              case Some(s) => caseSrv.setImpactStatus(c, s)(graph, authContext)
              case None    => caseSrv.unsetImpactStatus(c)(graph, authContext)
            }
          } yield Json.obj("impactStatus" -> impactStatus)
      })
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), caseSteps) =>
          caseSteps
            .customFields(EntityIdOrName(name))
            .jsonValue
        case (_, caseSteps) => caseSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }
        .filter {
          case (FPathElem(_, FPathElem(name, _)), caseTraversal) =>
            db
              .roTransaction(implicit graph => customFieldSrv.get(EntityIdOrName(name)).value(_.`type`).getOrFail("CustomField"))
              .map {
                case CustomFieldType.boolean => caseTraversal.customFields(EntityIdOrName(name)).value(_.booleanValue)
                case CustomFieldType.date    => caseTraversal.customFields(EntityIdOrName(name)).value(_.dateValue)
                case CustomFieldType.float   => caseTraversal.customFields(EntityIdOrName(name)).value(_.floatValue)
                case CustomFieldType.integer => caseTraversal.customFields(EntityIdOrName(name)).value(_.integerValue)
                case CustomFieldType.string  => caseTraversal.customFields(EntityIdOrName(name)).value(_.stringValue)
              }
              .getOrElse(caseTraversal.constant2(null))
          case (_, caseTraversal) => caseTraversal.constant2(null)
        }
        .converter {
          case FPathElem(_, FPathElem(name, _)) =>
            db
              .roTransaction { implicit graph =>
                customFieldSrv.get(EntityIdOrName(name)).value(_.`type`).getOrFail("CustomField")
              }
              .map {
                case CustomFieldType.boolean => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[Boolean] }
                case CustomFieldType.date    => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[Date] }
                case CustomFieldType.float   => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[Double] }
                case CustomFieldType.integer => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[Long] }
                case CustomFieldType.string  => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[String] }
              }
              .getOrElse(new Converter[Any, JsValue] { def apply(x: JsValue): Any = x })
          case _ => (x: JsValue) => x
        }
        .custom {
          case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
            for {
              c <- caseSrv.get(vertex)(graph).getOrFail("Case")
              _ <- caseSrv.setOrCreateCustomField(c, EntityIdOrName(name), Some(value), None)(graph, authContext)
            } yield Json.obj(s"customField.$name" -> value)
          case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
            for {
              c   <- caseSrv.get(vertex)(graph).getOrFail("Case")
              cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(EntityIdOrName(n))(graph).map(cf => (cf, v, None)) }
              _   <- caseSrv.updateCustomField(c, cfv)(graph, authContext)
            } yield Json.obj("customFields" -> values)
          case _ => Failure(BadRequestError("Invalid custom fields format"))
        })
      .property("computed.handlingDurationInHours", UMapping.long)(
        _.select(
          _.coalesce(
            _.has(_.endDate)
              .sack(
                (_: JLong, endDate: JLong) => endDate,
                _.by(_.value(_.endDate).graphMap[Long, JLong, Converter[Long, JLong]](_.getTime, Converter.long))
              )
              .sack((_: Long) - (_: JLong), _.by(_.value(_.startDate).graphMap[Long, JLong, Converter[Long, JLong]](_.getTime, Converter.long)))
              .sack((_: Long) / (_: Long), _.by(_.constant(3600000L)))
              .sack[Long],
            0L
          )
        ).readonly
      )
      .property("viewingOrganisation", UMapping.string)(
        _.authSelect((cases, authContext) => cases.organisations.visible(authContext).value(_.name)).readonly
      )
      .property("owningOrganisation", UMapping.string)(
        _.authSelect((cases, authContext) => cases.origin.visible(authContext).value(_.name)).readonly
      )
      .build
}
