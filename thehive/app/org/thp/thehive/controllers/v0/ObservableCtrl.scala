package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{PropertyUpdater, Query}
import org.thp.thehive.dto.v0.InputObservable
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, ObservableSrv}
import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class ObservableCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    observableSrv: ObservableSrv,
    caseSrv: CaseSrv,
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
          observablesWithData      ← inputObservable.data.toTry(i ⇒ observableSrv.create(inputObservable, Left(Data(i)), Nil, case0))
          observableWithAttachment ← inputObservable.attachment.map(a ⇒ observableSrv.create(inputObservable, Right(a), Nil, case0)).flip
          createdObservables = observablesWithData ++ observableWithAttachment
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

//  def list: Action[AnyContent] =
//    entryPoint("list task")
//      .authenticated { implicit request ⇒
//        db.transaction { implicit graph ⇒
//          val observables = observableSrv.initSteps
////            .availableFor(request.organisation)
//              .richObservable
//            .toList()
//            .map(_.toJson)
//          Results.Ok(Json.toJson(observables))
//        }
//      }

  def update(obsId: String): Action[AnyContent] =
    entryPoint("update observable")
      .extract('observable, FieldsParser.update("observable", observableProperties(db)))
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
}
