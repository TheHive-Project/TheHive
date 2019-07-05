package models

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import models.JsonFormat.dashboardStatusFormat

import org.elastic4play.models.{AttributeDef, EntityDef, HiveEnumeration, ModelDef, AttributeFormat ⇒ F}

object DashboardStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Private, Shared, Deleted = Value
}

trait DashboardAttributes { _: AttributeDef ⇒
  val title: A[String]                 = attribute("title", F.textFmt, "Title of the dashboard")
  val description: A[String]           = attribute("description", F.textFmt, "Description of the dashboard")
  val status: A[DashboardStatus.Value] = attribute("status", F.enumFmt(DashboardStatus), "Status of the case", DashboardStatus.Private)
  val definition: A[String]            = attribute("definition", F.textFmt, "Dashboard definition")
}

@Singleton
class DashboardModel @Inject()() extends ModelDef[DashboardModel, Dashboard]("dashboard", "Dashboard", "/dashboard") with DashboardAttributes {
  dashboardModel ⇒

  private[DashboardModel] lazy val logger = Logger(getClass)
  override val removeAttribute: JsObject  = Json.obj("status" → DashboardStatus.Deleted)
}

class Dashboard(model: DashboardModel, attributes: JsObject) extends EntityDef[DashboardModel, Dashboard](model, attributes) with DashboardAttributes
