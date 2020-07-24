package org.thp.thehive.controllers.v0

import java.lang.{Long => JLong}

import gremlin.scala.{By, Graph, Key}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSteps, ShareSteps}
import play.api.libs.json._

import scala.collection.JavaConverters._

trait CaseRenderer {

  def observableStats(
      shareSteps: ShareSteps
  )(implicit db: Database, graph: Graph): Traversal[JsObject, JsObject] =
    shareSteps
      .observables
      .count
      .map(count => Json.obj("count" -> count))

  def taskStats(shareSteps: ShareSteps)(implicit db: Database, graph: Graph): Traversal[JsObject, JsObject] =
    shareSteps
      .tasks
      .active
      .groupCount(By(Key[String]("status")))
      .map { statusAgg =>
        val (total, result) = statusAgg.asScala.foldLeft(0L -> JsObject.empty) {
          case ((t, r), (k, v)) => (t + v) -> (r + (k -> JsNumber(v.toInt)))
        }
        result + ("total" -> JsNumber(total))
      }

  def alertStats(caseSteps: CaseSteps): Traversal[Seq[JsObject], Seq[JsObject]] =
    caseSteps
      .inTo[AlertCase]
      .group(By(Key[String]("type")), By(Key[String]("source")))
      .map { alertAgg =>
        alertAgg
          .asScala
          .flatMap {
            case (tpe, listOfSource) =>
              listOfSource.asScala.map(s => Json.obj("type" -> tpe, "source" -> s))
          }
          .toSeq
      }
  // seq({caseId, title})

  def mergeFromStats(caseSteps: CaseSteps): Traversal[JsValue, JsValue] = caseSteps.constant(JsNull)

  def mergeIntoStats(caseSteps: CaseSteps): Traversal[JsValue, JsValue] = caseSteps.constant(JsNull)

  def sharedWithStats(
      caseSteps: CaseSteps
  )(implicit db: Database, graph: Graph): Traversal[Seq[String], Seq[String]] =
    caseSteps.organisations.name.fold.map(_.asScala.toSeq)

  def originStats(caseSteps: CaseSteps)(implicit db: Database, graph: Graph): Traversal[String, String] =
    caseSteps.origin.name

  def shareCountStats(caseSteps: CaseSteps)(implicit db: Database, graph: Graph): Traversal[Long, JLong] =
    caseSteps.organisations.count

  def isOwnerStats(
      caseSteps: CaseSteps
  )(implicit db: Database, graph: Graph, authContext: AuthContext): Traversal[Boolean, Boolean] =
    caseSteps.origin.name.map(_ == authContext.organisation)

  def caseStatsRenderer(
      implicit authContext: AuthContext,
      db: Database,
      graph: Graph
  ): CaseSteps => Traversal[JsObject, JsObject] =
    _.project(
      _.by(
        (_: CaseSteps).coalesce(
          _.share.project(
            _.by(taskStats(_))
              .by(observableStats(_))
          ),
          _.constant((JsObject.empty, JsObject.empty))
        )
      ).by(alertStats(_))
        .by(mergeFromStats(_))
        .by(mergeIntoStats(_))
        //        .by(sharedWithStats(_))
        //        .by(originStats(_))
        .by(isOwnerStats(_))
        .by(shareCountStats(_))
    ).map {
      case ((tasks, observables), alerts, mergeFrom, mergeInto, isOwner, shareCount) =>
        Json.obj(
          "tasks"      -> tasks,
          "artifacts"  -> observables,
          "alerts"     -> alerts,
          "mergeFrom"  -> mergeFrom,
          "mergeInto"  -> mergeInto,
          "isOwner"    -> isOwner,
          "shareCount" -> (shareCount - 1)
        )
    }

}
