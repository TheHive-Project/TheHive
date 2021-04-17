package org.thp.thehive.controllers.v0

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.Converter.CList
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IdentityConverter, Traversal}
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json._

import java.lang.{Long => JLong}
import java.util.{Collection => JCollection, List => JList, Map => JMap}

trait CaseRenderer {

  def observableStats: Traversal.V[Share] => Traversal[JsObject, JLong, Converter[JsObject, JLong]] =
    _.observables
      .count
      .domainMap(count => Json.obj("count" -> count))

  def taskStats: Traversal.V[Share] => Traversal[JsObject, JMap[String, JLong], Converter[JsObject, JMap[String, JLong]]] =
    _.tasks
      .active
      .groupCount(_.byValue(_.status))
      .domainMap { statusAgg =>
        val (total, result) = statusAgg.foldLeft(0L -> JsObject.empty) {
          case ((t, r), (k, v)) => (t + v) -> (r + (k.toString -> JsNumber(v.toInt)))
        }
        result + ("total" -> JsNumber(total))
      }

  def alertStats: Traversal.V[Case] => Traversal[Seq[JsObject], JMap[String, JCollection[String]], Converter[Seq[JsObject], JMap[String, JCollection[
    String
  ]]]] =
    _.in[AlertCase]
      .v[Alert]
      .group(_.byValue(_.`type`), _.byValue(_.source))
      .domainMap { alertAgg =>
        (for {
          (tpe, sources) <- alertAgg
          source         <- sources
        } yield Json.obj("type" -> tpe, "source" -> source)).toSeq
      }

  def mergeFromStats: Traversal.V[Case] => Traversal[JsNull.type, JsNull.type, IdentityConverter[JsNull.type]] = _.constant(JsNull)

  def mergeIntoStats: Traversal.V[Case] => Traversal[JsNull.type, JsNull.type, IdentityConverter[JsNull.type]] = _.constant(JsNull)

  def sharedWithStats: Traversal.V[Case] => Traversal[Seq[String], JList[String], CList[String, String, Converter[String, String]]] =
    _.organisations.value(_.name).fold

  def originStats: Traversal.V[Case] => Traversal[String, String, Converter[String, String]] = _.origin.value(_.name)

  def shareCountStats: Traversal.V[Case] => Traversal[Long, JLong, Converter[Long, JLong]] = _.organisations.count

  def isOwnerStats(implicit authContext: AuthContext): Traversal.V[Case] => Traversal[Boolean, Boolean, Converter.Identity[Boolean]] =
    _.origin
      .current
      .fold
      .count
      .choose(_.is(P.gt(0)), onTrue = true, onFalse = false)

  def caseStatsRenderer(implicit
      authContext: AuthContext
  ): Traversal.V[Case] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.project(
      _.by(
        (_: Traversal.V[Case]).coalesce(
          _.share.project(
            _.by(taskStats)
              .by(observableStats)
          ),
          JsObject.empty -> JsObject.empty
        )
      ).by(alertStats)
        .by(mergeFromStats)
        .by(mergeIntoStats)
        //        .by(sharedWithStats(_))
        //        .by(originStats(_))
        .by(isOwnerStats)
        .by(shareCountStats)
    ).domainMap {
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
