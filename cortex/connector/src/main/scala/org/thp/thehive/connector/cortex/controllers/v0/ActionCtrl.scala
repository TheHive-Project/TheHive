package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, Schema}
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query, SubType}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{PagedResult, VertexSteps}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.models.{ActionContext, RichAction}
import org.thp.thehive.connector.cortex.services.{ActionSrv, ActionSteps, EntityHelper}
import org.thp.thehive.controllers.v0.Conversion.{toObjectType, _}
import org.thp.thehive.controllers.v0.{AuditRenderer, IdOrName, OutputParam, QueryableCtrl}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.{universe, universe => ru}

@Singleton
class ActionCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-schema") db: Database,
    properties: Properties,
    actionSrv: ActionSrv,
    entityHelper: EntityHelper,
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    alertSrv: AlertSrv,
    schema: Schema,
    implicit val executionContext: ExecutionContext
) extends QueryableCtrl
    with AuditRenderer {

  implicit val entityWrites: OWrites[Entity] = OWrites[Entity] { entity =>
    db.roTransaction { implicit graph =>
        entity match {
          case c: Case       => caseToJson(caseSrv.get(c)).getOrFail("Case")
          case t: Task       => taskToJson(taskSrv.get(t)).getOrFail("Task")
          case o: Observable => observableToJson(observableSrv.get(o)).getOrFail("Observable")
          case l: Log        => logToJson(logSrv.get(l)).getOrFail("Log")
          case a: Alert      => alertToJson(alertSrv.get(a)).getOrFail("Alert")
        }
      }
      .getOrElse(Json.obj("_type" -> entity._model.label, "_id" -> entity._id))
  }

  override val entityName: String                           = "action"
  override val publicProperties: List[PublicProperty[_, _]] = properties.action ::: metaProperties[ActionSteps]
  override val initialQuery: Query =
    Query.init[ActionSteps]("listAction", (graph, authContext) => actionSrv.initSteps(graph).visible(authContext))
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, ActionSteps](
    "getAction",
    FieldsParser[IdOrName],
    (param, graph, authContext) => actionSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, ActionSteps, PagedResult[RichAction]](
    "page",
    FieldsParser[OutputParam],
    (range, actionSteps, _) => actionSteps.richPage(range.from, range.to, withTotal = true)(_.richAction)
  )
  override val outputQuery: Query = Query.output[RichAction, ActionSteps](_.richAction)

  val actionsQuery: Query = new Query {
    override val name: String = "actions"
    override def checkFrom(t: universe.Type): Boolean =
      SubType(t, ru.typeOf[CaseSteps]) || SubType(t, ru.typeOf[ObservableSteps]) ||
        SubType(t, ru.typeOf[TaskSteps]) ||
        SubType(t, ru.typeOf[LogSteps]) ||
        SubType(t, ru.typeOf[AlertSteps])
    override def toType(t: universe.Type): universe.Type = ru.typeOf[ActionSteps]
    override def apply(param: Unit, from: Any, authContext: AuthContext): Any = {
      val fromSteps = from.asInstanceOf[VertexSteps[_]]
      new ActionSteps(from.asInstanceOf[VertexSteps[_]].inTo[ActionContext].raw)(db, fromSteps.graph, schema)
    }
  }

  override val extraQueries: Seq[ParamQuery[_]] = Seq(actionsQuery)

  def create: Action[AnyContent] =
    entrypoint("create action")
      .extract("action", FieldsParser[InputAction])
      .asyncAuth { implicit request =>
        val action: InputAction = request.body("action")
        val tryEntity = db.roTransaction { implicit graph =>
          entityHelper.get(toObjectType(action.objectType), action.objectId, Permissions.manageAction)
        }
        for {
          entity <- Future.fromTry(tryEntity)
          action <- actionSrv.execute(entity, action.cortexId, action.responderId, action.parameters.getOrElse(JsObject.empty))
        } yield Results.Ok(action.toJson)
      }

  def getByEntity(objectType: String, objectId: String): Action[AnyContent] =
    entrypoint("get by entity")
      .authRoTransaction(db) { implicit request => implicit graph =>
        for {
          entity <- entityHelper.get(toObjectType(objectType), objectId, Permissions.manageAction)
        } yield Results.Ok(actionSrv.listForEntity(entity._id).toJson)
      }
}
