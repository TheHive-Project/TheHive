package org.thp.thehive.controllers.v0

import gremlin.scala.Element
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.FieldsParser
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query._
import org.thp.thehive.dto.v0.{OutputCase, OutputTask}
import org.thp.thehive.models.{RichCase, Task}
import org.thp.thehive.services._

case class GetCaseParams(id: String)
case class RangeParams(from: Long, to: Long)

@Singleton
class TheHiveQueryExecutor @Inject()(caseSrv: CaseSrv, taskSrv: TaskSrv, implicit val db: Database) extends QueryExecutor {

  override val publicProperties: List[PublicProperty[_ <: Element, _, _]] = outputCaseProperties ++ outputTaskProperties
  override val queries: Seq[ParamQuery[_]] = Seq(
    Query.initWithParam[GetCaseParams, CaseSteps](
      "getCase",
      FieldsParser[GetCaseParams],
      (p, graph, authContext) ⇒ caseSrv.get(p.id)(graph).availableFor(authContext)),
    Query.init[CaseSteps]("listCase", (graph, authContext) ⇒ caseSrv.initSteps(graph).availableFor(authContext)),
    Query.init[TaskSteps]("listTask", (graph, _) ⇒ taskSrv.initSteps(graph)), // FIXME check permission,
    Query.withParam[RangeParams, CaseSteps, CaseSteps](
      "range",
      FieldsParser[RangeParams],
      (range, caseSteps, _) ⇒ caseSteps.range(range.from, range.to)),
    Query[CaseSteps, List[RichCase]]("toList", (caseSteps, _) ⇒ caseSteps.richCase.toList),
    Query[CaseSteps, TaskSteps]("listTask", (caseSteps, _) ⇒ caseSteps.tasks),
    Query.output[RichCase, OutputCase],
    Query.output[Task with Entity, OutputTask]
  )

//  val caseToList: ParamQuery[CaseSteps, Seq[RichCase]] = Query("toList")(_.richCase.toList)
//  val caseListTask: ParamQuery[CaseSteps, TaskSteps]   = Query("listTask")(_.tasks)
//  val taskToList: ParamQuery[TaskSteps, Seq[Task]]     = Query("toList")(_.toList)

//  override def toOutput(output: Any): JsValue = output match {
//    case r: RichCase         ⇒ r.toJson
//    case t: Task with Entity ⇒ t.toJson
//    case o                   ⇒ super.toOutput(o)
//  }
}
