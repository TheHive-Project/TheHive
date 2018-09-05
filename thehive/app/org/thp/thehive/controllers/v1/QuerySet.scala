package org.thp.thehive.controllers.v1

import scala.reflect.runtime.{universe ⇒ ru}

import play.api.libs.json.{JsValue, Json}

import gremlin.scala.GremlinScala
import javax.inject.{Inject, Singleton}
import org.scalactic.Good
import org.thp.scalligraph.controllers.{FieldsParser, JsonQueryExecutor}
import org.thp.scalligraph.query.{FObjOne, InitQuery, Query}
import org.thp.thehive.models.{RichCase, TheHiveSchema}
import org.thp.thehive.services.{CaseSteps, LogSteps, TaskSteps}

@Singleton
class QuerySet @Inject()(theHiveSchema: TheHiveSchema) extends JsonQueryExecutor {
  override def outputs: PartialFunction[ru.Type, Any ⇒ JsValue] =
    super.outputs.orElse {
      case t if t <:< ru.typeOf[RichCase] ⇒
        value ⇒
          Json.toJson(OutputCase.fromRichCase(value.asInstanceOf[RichCase]))
    }

  def caseById(id: String): InitQuery[CaseSteps] = InitQuery[CaseSteps]("caseById")(ag ⇒ theHiveSchema.caseSrv.steps(ag.graph).getCaseById(id))

  def richCase: Query[CaseSteps, GremlinScala[RichCase]] = Query("richCase")(_.richCase)

  override val initQueryParser: FieldsParser[InitQuery[_]] = FieldsParser[InitQuery[_]]("initQuery") {
    case (_, FObjOne("_caseGetById", o)) ⇒ FieldsParser[String].map("caseById")(caseById)(o)
    case (_, FObjOne("_cases", _))       ⇒ Good(InitQuery("cases")(ag ⇒ theHiveSchema.caseSrv.steps(ag.graph)))
    case (_, FObjOne("_tasks", _))       ⇒ Good(InitQuery("cases")(ag ⇒ theHiveSchema.taskSrv.steps(ag.graph)))
  }

  override val queryParser: FieldsParser[Query[_, _]] = FieldsParser[Query[_, _]]("query") {
    case (_, FObjOne("_richCase", _))     ⇒ Good(richCase)
    case (_, FObjOne("_caseGetTasks", _)) ⇒ Good(Query[CaseSteps, TaskSteps]("CaseGetTasks")(_.tasks))
    case (_, FObjOne("_taskGetLogs", _))  ⇒ Good(Query[TaskSteps, LogSteps]("TaskGetLogs")(_.logs))
  }
}
