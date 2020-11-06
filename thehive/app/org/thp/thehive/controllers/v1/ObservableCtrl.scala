package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputObservable
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services._
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class ObservableCtrl @Inject() (
    entryPoint: Entrypoint,
    @Named("with-thehive-schema") db: Database,
    properties: Properties,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseSrv: CaseSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl
    with ObservableRenderer {

  lazy val logger: Logger                         = Logger(getClass)
  override val entityName: String                 = "observable"
  override val publicProperties: PublicProperties = properties.observable
  override val initialQuery: Query =
    Query.init[Traversal.V[Observable]](
      "listObservable",
      (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.observables
    )
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Observable]](
    "getObservable",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => observableSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Observable], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    {
      case (OutputParam(from, to, extraData), observableSteps, authContext) =>
        observableSteps.richPage(from, to, extraData.contains("total")) {
          _.richObservableWithCustomRenderer(observableStatsRenderer(extraData - "total")(authContext))(authContext)
        }
    }
  )
  override val outputQuery: Query = Query.output[RichObservable, Traversal.V[Observable]](_.richObservable)

  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Observable], Traversal.V[Organisation]](
      "organisations",
      (observableSteps, authContext) => observableSteps.organisations.visible(authContext)
    ),
    Query[Traversal.V[Observable], Traversal.V[Observable]](
      "similar",
      (observableSteps, authContext) => observableSteps.filteredSimilar.visible(authContext)
    ),
    Query[Traversal.V[Observable], Traversal.V[Case]]("case", (observableSteps, _) => observableSteps.`case`)
  )

  def create(caseId: String): Action[AnyContent] =
    entryPoint("create artifact")
      .extract("artifact", FieldsParser[InputObservable])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputObservable: InputObservable = request.body("artifact")
        for {
          case0 <-
            caseSrv
              .get(EntityIdOrName(caseId))
              .can(Permissions.manageObservable)
              .getOrFail("Case")
          observableType <- observableTypeSrv.getOrFail(EntityName(inputObservable.dataType))
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
    entryPoint("get observable")
      .authRoTransaction(db) { _ => implicit graph =>
        observableSrv
          .get(EntityIdOrName(observableId))
          //            .availableFor(request.organisation)
          .richObservable
          .getOrFail("Observable")
          .map { observable =>
            Results.Ok(observable.toJson)
          }
      }

  def update(observableId: String): Action[AnyContent] =
    entryPoint("update observable")
      .extract("observable", FieldsParser.update("observable", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("observable")
        observableSrv
          .update(
            _.get(EntityIdOrName(observableId)).can(Permissions.manageObservable),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def bulkUpdate: Action[AnyContent] =
    entryPoint("bulk update")
      .extract("input", FieldsParser.update("observable", publicProperties))
      .extract("ids", FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val properties: Seq[PropertyUpdater] = request.body("input")
        val ids: Seq[String]                 = request.body("ids")
        ids
          .toTry { id =>
            observableSrv
              .update(_.get(EntityIdOrName(id)).can(Permissions.manageObservable), properties)
          }
          .map(_ => Results.NoContent)
      }

  def delete(obsId: String): Action[AnyContent] =
    entryPoint("delete")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          observable <-
            observableSrv
              .get(EntityIdOrName(obsId))
              .can(Permissions.manageObservable)
              .getOrFail("Observable")
          _ <- observableSrv.remove(observable)
        } yield Results.NoContent
      }
}
