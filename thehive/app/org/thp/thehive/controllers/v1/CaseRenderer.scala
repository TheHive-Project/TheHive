package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.models.{Alert, AlertCase, Case}
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.OrganisationSrv
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json._

import java.lang.{Long => JLong}
import java.util.{Collection => JCollection, List => JList, Map => JMap}

trait CaseRenderer extends BaseRenderer[Case] {
  val limitedCountThreshold: Long
  val organisationSrv: OrganisationSrv

  def observableStats(implicit authContext: AuthContext): Traversal.V[Case] => Traversal[JsObject, AnyRef, Converter[JsObject, AnyRef]] =
    t =>
      t._id.domainMap { caseId =>
        Json.obj(
          "total" -> t
            .graph
            .indexCountQuery(
              s"""v."_label":Observable AND """ +
                s"v.relatedId:${caseId.value} AND " +
                s"v.organisationIds:${organisationSrv.currentId(t.graph, authContext).value}"
            )
        )
      }
//    _.share
//      .observables
//      .limitedCount(limitedCountThreshold)
//      .domainMap(count => Json.obj("total" -> count))

  def taskStats(implicit
      authContext: AuthContext
  ): Traversal.V[Case] => Traversal[JsValue, JMap[String, JLong], Converter[JsValue, JMap[String, JLong]]] =
    _.share
      .tasks
      .active
      .groupCount(_.byValue(_.status))
      .domainMap { statusAgg =>
        val (total, result) = statusAgg.foldLeft(0L -> JsObject.empty) {
          case ((t, r), (k, v)) => (t + v) -> (r + (k.toString -> JsNumber(v.toInt)))
        }
        result + ("total" -> JsNumber(total))
      }

  def alertStats: Traversal.V[Case] => Traversal[JsValue, JMap[String, JCollection[String]], Converter[JsValue, JMap[String, JCollection[String]]]] =
    _.in[AlertCase]
      .v[Alert]
      .group(_.byValue(_.`type`), _.byValue(_.source))
      .domainMap { alertAgg =>
        JsArray((for {
          (tpe, sources) <- alertAgg
          source         <- sources
        } yield Json.obj("type" -> tpe, "source" -> source)).toSeq)
      }

  def isOwnerStats(implicit authContext: AuthContext): Traversal.V[Case] => Traversal[JsValue, JList[Vertex], Converter[JsValue, JList[Vertex]]] =
    _.origin.get(authContext.organisation).fold.domainMap(l => JsBoolean(l.nonEmpty))

  def shareCountStats: Traversal.V[Case] => Traversal[JsValue, JLong, Converter[JsValue, JLong]] =
    _.organisations.count.domainMap(c => JsNumber(c - 1))

  def permissions(implicit authContext: AuthContext): Traversal.V[Case] => Traversal[JsValue, JList[String], Converter[JsValue, JList[String]]] =
    _.userPermissions.domainMap(permissions => Json.toJson(permissions))

  def actionRequired(implicit authContext: AuthContext): Traversal.V[Case] => Traversal[JsValue, Boolean, Converter[JsValue, Boolean]] =
    _.isActionRequired.domainMap(JsBoolean(_))

  def procedureCount: Traversal.V[Case] => Traversal[JsNumber, JLong, Converter[JsNumber, JLong]] =
    _.procedure.count.domainMap(JsNumber(_))

  def caseStatsRenderer(extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Case] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "observableStats") => addData("observableStats", f)(observableStats)
        case (f, "taskStats")       => addData("taskStats", f)(taskStats)
        case (f, "alerts")          => addData("alerts", f)(alertStats)
        case (f, "isOwner")         => addData("isOwner", f)(isOwnerStats)
        case (f, "shareCount")      => addData("shareCount", f)(shareCountStats)
        case (f, "permissions")     => addData("permissions", f)(permissions)
        case (f, "actionRequired")  => addData("actionRequired", f)(actionRequired)
        case (f, "procedureCount")  => addData("procedureCount", f)(procedureCount)
        case (f, _)                 => f
      }
    )
  }
}
