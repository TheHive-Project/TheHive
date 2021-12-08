package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.{EntityIdOrName, InternalError}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, IndexType, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.models.{Action, ActionContext, RichAction}
import org.thp.thehive.connector.cortex.services.{ActionOps, ActionSrv, CortexOps, EntityHelper}
import org.thp.thehive.controllers.v0.Conversion.{toObjectType, _}
import org.thp.thehive.controllers.v0._
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.mvc.{AnyContent, Results, Action => PlayAction}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.{universe => ru}

class ActionCtrl(
    override val entrypoint: Entrypoint,
    override val db: Database,
    actionSrv: ActionSrv,
    entityHelper: EntityHelper,
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    alertSrv: AlertSrv,
    implicit val executionContext: ExecutionContext,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicAction
) extends AuditRenderer
    with QueryCtrl {

  implicit val entityWrites: OWrites[Entity] = OWrites[Entity] { entity =>
    db.roTransaction { implicit graph =>
      entity match {
        case c: Case       => caseToJson(caseSrv.get(c)).getOrFail("Case")
        case t: Task       => taskToJson(taskSrv.get(t)).getOrFail("Task")
        case o: Observable => observableToJson(observableSrv.get(o)).getOrFail("Observable")
        case l: Log        => logToJson(logSrv.get(l)).getOrFail("Log")
        case a: Alert      => alertToJson(alertSrv.get(a)).getOrFail("Alert")
        case other         => throw InternalError(s"Invalid entity (${other.getClass})")
      }
    }.getOrElse(Json.obj("_type" -> entity._label, "_id" -> entity._id))
  }

  def create: PlayAction[AnyContent] =
    entrypoint("create action")
      .extract("action", FieldsParser[InputAction])
      .asyncAuth { implicit request =>
        val action: InputAction = request.body("action")
        val tryEntity = db.roTransaction { implicit graph =>
          entityHelper.get(toObjectType(action.objectType), EntityIdOrName(action.objectId), Permissions.manageAction)
        }
        for {
          entity <- Future.fromTry(tryEntity)
          action <- actionSrv.execute(entity, action.cortexId, action.responderId, action.parameters.getOrElse(JsObject.empty))
        } yield Results.Ok(action.toJson)
      }

  def getByEntity(objectType: String, objectId: String): PlayAction[AnyContent] =
    entrypoint("get by entity")
      .authRoTransaction(db) { implicit request => implicit graph =>
        for {
          entity <- entityHelper.get(toObjectType(objectType), EntityIdOrName(objectId), Permissions.manageAction)
        } yield Results.Ok(actionSrv.listForEntity(entity._id).toJson)
      }
}

class PublicAction(
    actionSrv: ActionSrv,
    searchSrv: SearchSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    db: Database
) extends PublicData
    with CortexOps
    with ActionOps {

  override val entityName: String = "action"
  override val initialQuery: Query =
    Query.init[Traversal.V[Action]]("listAction", (graph, authContext) => actionSrv.startTraversal(graph).visible(authContext))
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Action]](
    "getAction",
    (idOrName, graph, authContext) => actionSrv.get(idOrName)(graph).visible(authContext)
  )
  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Action], IteratorOutput](
      "page",
      (range, actionSteps, _) => actionSteps.richPage(range.from, range.to, withTotal = true, limitedCountThreshold)(_.richAction)
    )
  override val outputQuery: Query = Query.output[RichAction, Traversal.V[Action]](_.richAction)
  val actionsQuery: Query = new Query {
    override val name: String = "actions"
    override def checkFrom(t: ru.Type): Boolean =
      SubType(t, ru.typeOf[Traversal.V[Case]]) ||
        SubType(t, ru.typeOf[Traversal.V[Observable]]) ||
        SubType(t, ru.typeOf[Traversal.V[Task]]) ||
        SubType(t, ru.typeOf[Traversal.V[Log]]) ||
        SubType(t, ru.typeOf[Traversal.V[Alert]])
    override def toType(t: ru.Type): ru.Type = ru.typeOf[Traversal.V[Action]]

    override def apply(param: Unit, fromType: ru.Type, from: Any, authContext: AuthContext): Any =
      from.asInstanceOf[Traversal.V[_]].in[ActionContext].v[Action]
  }

  override val extraQueries: Seq[ParamQuery[_]] = Seq(actionsQuery)
  override val publicProperties: PublicProperties =
    PublicPropertyListBuilder[Action]
      .property("keyword", UMapping.string)(
        _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
          .filter[String](IndexType.fulltext) {
            case (_, t, _, Right(p))   => searchSrv("Action", p.getValue)(t)
            case (_, t, _, Left(true)) => t
            case (_, t, _, _)          => t.empty
          }
          .readonly
      )
      .property("responderId", UMapping.string)(_.rename("workerId").readonly)
      .property("objectType", UMapping.string)(_.select(_.context.domainMap(o => fromObjectType(o._label))).readonly)
      .property("status", UMapping.string)(_.field.readonly)
      .property("startDate", UMapping.date)(_.field.readonly)
      .property("objectId", db.idMapping)(_.select(_.out[ActionContext]._id).readonly)
      .property("responderName", UMapping.string.optional)(_.rename("workerName").readonly)
      .property("cortexId", UMapping.string.optional)(_.field.readonly)
      .build
}
