package org.thp.thehive.controllers.v1

import java.lang.{Boolean => JBoolean, Long => JLong}
import java.util.{List => JList, Map => JMap}
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.Observable
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.OrganisationSrv
import play.api.libs.json._

trait ObservableRenderer extends BaseRenderer[Observable] {

  def seenStats(organisationSrv: OrganisationSrv)(implicit
      authContext: AuthContext
  ): Traversal.V[Observable] => Traversal[JsValue, JMap[JBoolean, JLong], Converter[JsValue, JMap[JBoolean, JLong]]] =
    _.filteredSimilar
      .visible(organisationSrv)
      .groupCount(_.byValue(_.ioc))
      .domainMap { stats =>
        val nTrue  = stats.getOrElse(true, 0L)
        val nFalse = stats.getOrElse(false, 0L)
        Json.obj(
          "seen" -> (nTrue + nFalse),
          "ioc"  -> (nTrue > 0)
        )
      }

  def sharesStats: Traversal.V[Observable] => Traversal[JsValue, JList[String], Converter[JsValue, JList[String]]] =
    _.organisations.value(_.name).fold.domainMap(Json.toJson(_))

  def shareCount: Traversal.V[Observable] => Traversal[JsValue, JLong, Converter[JsValue, JLong]] =
    _.organisations.count.domainMap(count => JsNumber(count.longValue() - 1))

  def isOwner(implicit
      authContext: AuthContext
  ): Traversal.V[Observable] => Traversal[JsValue, JList[Vertex], Converter[JsValue, JList[Vertex]]] =
    _.origin.get(authContext.organisation).fold.domainMap(l => JsBoolean(l.nonEmpty))

  def observableLinks: Traversal.V[Observable] => Traversal[JsValue, JMap[String, Any], Converter[JsValue, JMap[String, Any]]] =
    _.coalesceMulti(
      _.alert.richAlert.domainMap(a => Json.obj("alert" -> a.toJson)),
      _.`case`.richCaseWithoutPerms.domainMap(c => Json.obj("case" -> c.toJson))
    )

  def permissions(implicit authContext: AuthContext): Traversal.V[Observable] => Traversal[JsValue, Vertex, Converter[JsValue, Vertex]] =
    _.userPermissions.domainMap(permissions => Json.toJson(permissions))

  def observableStatsRenderer(organisationSrv: OrganisationSrv, extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Observable] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "seen")        => addData("seen", f)(seenStats(organisationSrv))
        case (f, "shares")      => addData("shares", f)(sharesStats)
        case (f, "links")       => addData("links", f)(observableLinks)
        case (f, "permissions") => addData("permissions", f)(permissions)
        case (f, "isOwner")     => addData("isOwner", f)(isOwner)
        case (f, "shareCount")  => addData("shareCount", f)(shareCount)
        case (f, _)             => f
      }
    )
  }
}
