package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.FieldsParser
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query._
import org.thp.thehive.dto.v1.{OutputCase, OutputOrganisation, OutputTask, OutputUser}
import org.thp.thehive.models.{Organisation, RichCase, RichUser, Task}
import org.thp.thehive.services._

case class GetCaseParams(id: String)

@Singleton
class TheHiveQueryExecutor @Inject()(
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    implicit val db: Database
) extends QueryExecutor
    with CaseConversion
    with TaskConversion
    with UserConversion
    with OrganisationConversion {

  override val version: (Int, Int)                          = 1 -> 1
  override val publicProperties: List[PublicProperty[_, _]] = caseProperties
  override val queries: Seq[ParamQuery[_]] = Seq(
    Query.initWithParam[GetCaseParams, CaseSteps](
      "getCase",
      FieldsParser[GetCaseParams],
      (p, graph, authContext) => caseSrv.get(p.id)(graph).visible(authContext)
    ),
    Query.init[CaseSteps]("listCase", (graph, authContext) => caseSrv.initSteps(graph).visible(authContext)),
    Query.init[TaskSteps]("listTask", (graph, _) => taskSrv.initSteps(graph)), // FIXME check permission,
    Query.init[UserSteps]("listUser", (graph, _) => userSrv.initSteps(graph)),
    Query.init[OrganisationSteps]("listOrganisation", (graph, _) => organisationSrv.initSteps(graph)),
    Query[CaseSteps, List[RichCase]]("toList", (caseSteps, _) => caseSteps.richCase.toList),
    Query[CaseSteps, TaskSteps]("listTask", (caseSteps, authContext) => caseSteps.tasks(authContext)),
    Query[UserSteps, List[RichUser]]("toList", (userSteps, authContext) => userSteps.richUser(authContext.organisation).toList),
    Query.output[RichCase, OutputCase],
    Query.output[Task with Entity, OutputTask],
    Query.output[RichUser, OutputUser],
    Query.output[Organisation with Entity, OutputOrganisation]
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
