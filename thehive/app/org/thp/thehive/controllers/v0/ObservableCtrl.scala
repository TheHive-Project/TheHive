package org.thp.thehive.controllers.v0

import scala.util.{Success, Try}

import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{PropertyUpdater, Query}
import org.thp.thehive.dto.v0.InputObservable
import org.thp.thehive.models._
import org.thp.thehive.services.{AuditSrv, CaseSrv, ObservableSrv}

@Singleton
class ObservableCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    observableSrv: ObservableSrv,
    caseSrv: CaseSrv,
    auditSrv: AuditSrv,
    val queryExecutor: TheHiveQueryExecutor
) extends QueryCtrl
    with ObservableConversion {

  lazy val logger = Logger(getClass)

  def create(caseId: String): Action[AnyContent] =
    entryPoint("create artifact")
      .extract('artifact, FieldsParser[InputObservable])
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val inputObservable: InputObservable = request.body('artifact)
        for {
          case0 ← caseSrv
            .get(caseId)
            .can(Permissions.manageCase)
            .getOrFail()
          observablesWithData      ← inputObservable.data.toTry(d ⇒ observableSrv.create(inputObservable, d, Nil))
          observableWithAttachment ← inputObservable.attachment.map(a ⇒ observableSrv.create(inputObservable, a, Nil)).flip
          createdObservables ← (observablesWithData ++ observableWithAttachment).toTry { richObservables ⇒
            caseSrv.addObservable(case0, richObservables.observable).map(_ ⇒ richObservables)
          }
        } yield Results.Created(Json.toJson(createdObservables.map(_.toJson)))
      }

  def get(observableId: String): Action[AnyContent] =
    entryPoint("get observable")
      .authTransaction(db) { _ ⇒ implicit graph ⇒
        observableSrv
          .get(observableId)
          //            .availableFor(request.organisation)
          .richObservable
          .getOrFail()
          .map { observable ⇒
            Results.Ok(observable.toJson)
          }
      }

  def update(obsId: String): Action[AnyContent] =
    entryPoint("update observable")
      .extract('observable, FieldsParser.update("observable", observableProperties))
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val propertyUpdaters: Seq[PropertyUpdater] = request.body('observable)
        observableSrv
          .update(
            _.get(obsId).can(Permissions.manageCase),
            propertyUpdaters
          )
          .map(_ ⇒ Results.NoContent)
      }

  def stats(): Action[AnyContent] = {
    val parser: FieldsParser[Seq[Query]] = statsParser("listObservable")
    entryPoint("stats observable")
      .extract('query, parser)
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val queries: Seq[Query] = request.body('query)
        val results = queries
          .map(query ⇒ queryExecutor.execute(query, graph, request.authContext).toJson)
          .foldLeft(JsObject.empty) {
            case (acc, o: JsObject) ⇒ acc ++ o
            case (acc, r) ⇒
              logger.warn(s"Invalid stats result: $r")
              acc
          }
        Success(Results.Ok(results))
      }
  }

  def search: Action[AnyContent] =
    entryPoint("search case")
      .extract('query, searchParser("listObservable", paged = false))
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val query: Query = request.body('query)
        val result       = queryExecutor.execute(query, graph, request.authContext)
        val resp         = (result.toJson \ "result").as[JsArray]
        result.toOutput match {
          case PagedResult(_, Some(size)) ⇒ Success(Results.Ok(resp).withHeaders("X-Total" → size.toString))
          case _                          ⇒ Success(Results.Ok(resp).withHeaders("X-Total" → resp.value.size.toString))
        }
      }

  def findSimilar(obsId: String): Action[AnyContent] =
    entryPoint("find similar")
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val observables = observableSrv
          .get(obsId)(graph)
          .similar(obsId)
          .richObservable
          .toList()

        Success(Results.Ok(Json.toJson(observables.map(_.toJson))))
      }

  def bulkUpdate: Action[AnyContent] =
    entryPoint("bulk update")
      .extract('input, FieldsParser.update("observable", observableProperties))
      .extract('ids, FieldsParser.seq[String].on("ids"))
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val properties: Seq[PropertyUpdater] = request.body('input)
        val ids: Seq[String]                 = request.body('ids)
        val res = Try(
          ids.map(
            obsId ⇒
              observableSrv
                .update(_.get(obsId).can(Permissions.manageCase), properties)(graph, request.authContext)
                .get
          )
        )

        res.map(_ ⇒ Results.NoContent)
      }

  def delete(obsId: String): Action[AnyContent] =
    entryPoint("delete")
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val obsStep = observableSrv
          .get(obsId)(graph)
          .can(Permissions.manageCase)
        for {
          obs ← obsStep
            .getOrFail()
          _ ← Try(observableSrv.initSteps(graph).remove(obs._id))
//          _ ← auditSrv.deleteObservable(obs, Json.obj("id" → obs._id, "type" → obs.`type`, "message" → obs.message))(graph, request.authContext)
        } yield Results.NoContent
      }
}
