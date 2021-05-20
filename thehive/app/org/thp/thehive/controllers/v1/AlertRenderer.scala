package org.thp.thehive.controllers.v1

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.{Alert, RichCase, SimilarStats}
import org.thp.thehive.services.{OrganisationSrv, TheHiveOps}
import play.api.libs.json._

import java.util.{Date, List => JList, Map => JMap}

trait AlertRenderer extends BaseRenderer[Alert] with TheHiveOps {
  implicit val similarCaseWrites: Writes[(RichCase, SimilarStats)] = Writes[(RichCase, SimilarStats)] {
    case (richCase, similarStats) =>
      Json.obj(
        "case"                   -> richCase.toJson,
        "similarObservableCount" -> similarStats.observable._1,
        "observableCount"        -> similarStats.observable._2,
        "similarIocCount"        -> similarStats.ioc._1,
        "iocCount"               -> similarStats.ioc._2,
        "observableTypes"        -> similarStats.types
      )
  }
  def similarCasesStats(organisationSrv: OrganisationSrv)(implicit
      authContext: AuthContext
  ): Traversal.V[Alert] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] = {
    implicit val similarCaseOrdering: Ordering[(RichCase, SimilarStats)] = (x: (RichCase, SimilarStats), y: (RichCase, SimilarStats)) =>
      //negative if x < y
      if (x._1._createdAt after y._1._createdAt) -1
      else if (x._1._createdAt before y._1._createdAt) 1
      else if (x._2.observable._1 > y._2.observable._1) -1
      else if (x._2.observable._1 < y._2.observable._1) 1
      else if (x._2.ioc._1 > y._2.ioc._1) -1
      else if (x._2.ioc._1 < y._2.ioc._1) 1
      else if (x._2.ioc._2 > y._2.ioc._2) -1
      else if (x._2.ioc._2 < y._2.ioc._2) 1
      else 0
    _.similarCases(caseFilter = None).fold.domainMap(sc => JsArray(sc.sorted.map(Json.toJson(_))))
  }

  def importDate: Traversal.V[Alert] => Traversal[JsValue, JList[Date], Converter[JsValue, JList[Date]]] =
    _.importDate.fold.domainMap(_.headOption.fold[JsValue](JsNull)(d => JsNumber(d.getTime)))

  def alertStatsRenderer(organisationSrv: OrganisationSrv, extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Alert] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "similarCases") => addData("similarCases", f)(similarCasesStats(organisationSrv))
        case (f, "importDate")   => addData("importDate", f)(importDate)
        case (f, _)              => f
      }
    )
  }
}
