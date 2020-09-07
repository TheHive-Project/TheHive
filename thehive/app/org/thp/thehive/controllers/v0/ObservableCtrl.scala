package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputObservable
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class ObservableCtrl @Inject() (
    override val entrypoint: Entrypoint,
    @Named("with-thehive-schema") override val db: Database,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseSrv: CaseSrv,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicObservable
) extends ObservableRenderer
    with QueryCtrl {
  def create(caseId: String): Action[AnyContent] =
    entrypoint("create artifact")
      .extract("artifact", FieldsParser[InputObservable])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputObservable: InputObservable = request.body("artifact")
        for {
          case0 <-
            caseSrv
              .get(caseId)
              .can(Permissions.manageObservable)
              .orFail(AuthorizationError("Operation not permitted"))
          observableType <- observableTypeSrv.getOrFail(inputObservable.dataType)
          observablesWithData <-
            inputObservable
              .data
              .toTry(d => observableSrv.create(inputObservable.toObservable, observableType, d, inputObservable.tags, Nil))
          observableWithAttachment <-
            inputObservable
              .attachment
              .map(a => observableSrv.create(inputObservable.toObservable, observableType, a, inputObservable.tags, Nil))
              .flip
          createdObservables <- (observablesWithData ++ observableWithAttachment).toTry { richObservables =>
            caseSrv
              .addObservable(case0, richObservables)
              .map(_ => richObservables)
          }
        } yield Results.Created(createdObservables.toJson)
      }

  def get(observableId: String): Action[AnyContent] =
    entrypoint("get observable")
      .authRoTransaction(db) { implicit request => implicit graph =>
        observableSrv
          .getByIds(observableId)
          .visible
          .richObservable
          .getOrFail("Observable")
          .map { observable =>
            Results.Ok(observable.toJson)
          }
      }

  def update(observableId: String): Action[AnyContent] =
    entrypoint("update observable")
      .extract("observable", FieldsParser.update("observable", publicData.publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("observable")
        observableSrv
          .update(
            _.getByIds(observableId).can(Permissions.manageObservable),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def findSimilar(obsId: String): Action[AnyContent] =
    entrypoint("find similar")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val observables = observableSrv
          .getByIds(obsId)
          .visible
          .similar
          .visible
          .richObservableWithCustomRenderer(observableLinkRenderer)
          .toSeq

        Success(Results.Ok(observables.toJson))
      }

  def bulkUpdate: Action[AnyContent] =
    entrypoint("bulk update")
      .extract("input", FieldsParser.update("observable", publicData.publicProperties))
      .extract("ids", FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val properties: Seq[PropertyUpdater] = request.body("input")
        val ids: Seq[String]                 = request.body("ids")
        ids
          .toTry { id =>
            observableSrv
              .update(_.getByIds(id).can(Permissions.manageObservable), properties)
          }
          .map(_ => Results.NoContent)
      }

  def delete(obsId: String): Action[AnyContent] =
    entrypoint("delete")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          observable <-
            observableSrv
              .getByIds(obsId)
              .can(Permissions.manageObservable)
              .getOrFail("Observable")
          _ <- observableSrv.remove(observable)
        } yield Results.NoContent
      }
}

@Singleton
class PublicObservable @Inject() (
    observableSrv: ObservableSrv,
    organisationSrv: OrganisationSrv
) extends PublicData
    with ObservableRenderer {
  override val entityName: String = "observable"
  override val initialQuery: Query =
    Query.init[Traversal.V[Observable]](
      "listObservable",
      (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.observables
    )
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[Observable]](
    "getObservable",
    FieldsParser[IdOrName],
    (param, graph, authContext) => observableSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Observable], IteratorOutput](
      "page",
      FieldsParser[OutputParam],
      {
        case (OutputParam(from, to, withStats, 0), observableSteps, authContext) =>
          observableSteps
            .richPage(from, to, withTotal = true) {
              case o if withStats =>
                o.richObservableWithCustomRenderer(observableStatsRenderer(authContext))(authContext)
                  .domainMap(ros => (ros._1, ros._2, None: Option[RichCase]))
              case o =>
                o.richObservable.domainMap(ro => (ro, JsObject.empty, None))
            }
        case (OutputParam(from, to, _, _), observableSteps, authContext) =>
          observableSteps.richPage(from, to, withTotal = true)(
            _.richObservableWithCustomRenderer(o => o.`case`.richCase(authContext))(authContext).domainMap(roc =>
              (roc._1, JsObject.empty, Some(roc._2): Option[RichCase])
            )
          )
      }
    )
  override val outputQuery: Query = Query.output[RichObservable, Traversal.V[Observable]](_.richObservable)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    //    Query.output[(RichObservable, JsObject, Option[RichCase])]
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Observable]
    .property("status", UMapping.string)(_.select(_.constant("Ok")).readonly)
    .property("startDate", UMapping.date)(_.select(_._createdAt).readonly)
    .property("ioc", UMapping.boolean)(_.field.updatable)
    .property("sighted", UMapping.boolean)(_.field.updatable)
    .property("tags", UMapping.string.set)(
      _.select(_.tags.displayName)
        .custom { (_, value, vertex, _, graph, authContext) =>
          observableSrv
            .getByIds(vertex.id.toString)(graph)
            .getOrFail("Observable")
            .flatMap(observable => observableSrv.updateTagNames(observable, value)(graph, authContext))
            .map(_ => Json.obj("tags" -> value))
        }
    )
    .property("message", UMapping.string)(_.field.updatable)
    .property("tlp", UMapping.int)(_.field.updatable)
    .property("dataType", UMapping.string)(_.select(_.observableType.value(_.name)).readonly)
    .property("data", UMapping.string.optional)(_.select(_.data.value(_.data)).readonly)
    .property("attachment.name", UMapping.string.optional)(_.select(_.attachments.value(_.name)).readonly)
    .property("attachment.size", UMapping.long.optional)(_.select(_.attachments.value(_.size)).readonly)
    .property("attachment.contentType", UMapping.string.optional)(_.select(_.attachments.value(_.contentType)).readonly)
    .property("attachment.hashes", UMapping.hash)(_.select(_.attachments.value(_.hashes)).readonly)
    .build
}
