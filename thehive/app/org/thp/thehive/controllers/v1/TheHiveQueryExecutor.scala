package org.thp.thehive.controllers.v1

import gremlin.scala.Element
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.FieldsParser
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query._
import org.thp.thehive.dto.v1.{OutputCase, OutputTask}
import org.thp.thehive.models.{RichCase, Task}
import org.thp.thehive.services._

case class GetCaseParams(id: String)
object GetCaseParams {
  implicit val parser: FieldsParser[GetCaseParams] = FieldsParser[GetCaseParams]
}

@Singleton
class TheHiveQueryExecutor @Inject()(caseSrv: CaseSrv, taskSrv: TaskSrv, implicit val db: Database) extends QueryExecutor {

  override val publicProperties: List[PublicProperty[_ <: Element, _]] = outputCaseProperties
  override val queries: Seq[ParamQuery[_]] = Seq(
    Query.initWithParam[GetCaseParams, CaseSteps]("getCase", (p, graph, authContext) ⇒ caseSrv.get(p.id)(graph).availableFor(authContext)),
    Query.init[CaseSteps]("listCase", (graph, authContext) ⇒ caseSrv.initSteps(graph).availableFor(authContext)),
    Query.init[TaskSteps]("listTask", (graph, _) ⇒ taskSrv.initSteps(graph)), // FIXME check permission,
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
