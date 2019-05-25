package org.thp.thehive.controllers.v0

import gremlin.scala.{Graph, GremlinScala, StepLabel, Vertex}
import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FSeq, Field, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.InputFilter.{and, not, or}
import org.thp.scalligraph.query._
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{OutputAlert, OutputCase, OutputObservable, OutputTask, OutputUser}
import org.thp.thehive.models._
import org.thp.thehive.services._

import scala.reflect.runtime.{currentMirror ⇒ rm, universe ⇒ ru}

case class GetCaseParams(id: String)
case class RangeParams(from: Long, to: Long, withSize: Option[Boolean])

@Singleton
class TheHiveQueryExecutor @Inject()(
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    observableSrv: ObservableSrv,
    alertSrv: AlertSrv,
    logSrv: LogSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    implicit val db: Database
) extends QueryExecutor
    with CaseConversion
    with TaskConversion
    with AlertConversion
    with ObservableConversion
    with UserConversion {

  override val publicProperties: List[PublicProperty[_, _]] =
    caseProperties ++
      taskProperties ++
      alertProperties ++
      observableProperties ++
      userProperties(userSrv)
  override val queries: Seq[ParamQuery[_]] = Seq(
    Query.initWithParam[GetCaseParams, CaseSteps](
      "getCase",
      FieldsParser[GetCaseParams],
      (p, graph, authContext) ⇒ caseSrv.get(p.id)(graph).visible(authContext)
    ),
    Query.init[CaseSteps]("listCase", (graph, authContext) ⇒ caseSrv.initSteps(graph).visible(authContext)),
    Query.init[UserSteps]("listUser", (graph, authContext) ⇒ organisationSrv.get(authContext.organisation)(graph).users),
    Query.init[TaskSteps]("listTask", (graph, _) ⇒ taskSrv.initSteps(graph)), // FIXME check permission,
    Query.init[AlertSteps]("listAlert", (graph, _) ⇒ alertSrv.initSteps(graph)),
    Query.init[ObservableSteps]("listObservable", (graph, _) ⇒ observableSrv.initSteps(graph)),
    Query.init[LogSteps]("listLog", (graph, _) ⇒ logSrv.initSteps(graph)),
    Query.withParam[RangeParams, CaseSteps, PagedResult[RichCase]](
      "page",
      FieldsParser[RangeParams],
      (range, caseSteps, _) ⇒ caseSteps.richCase.page(range.from, range.to, range.withSize.getOrElse(false))
    ),
    Query.withParam[RangeParams, AlertSteps, AlertSteps](
      "range",
      FieldsParser[RangeParams],
      (range, alertSteps, _) ⇒ alertSteps.range(range.from, range.to)
    ),
    Query.withParam[RangeParams, TaskSteps, PagedResult[Task with Entity]](
      "page",
      FieldsParser[RangeParams],
      (range, taskSteps, _) ⇒ taskSteps.page(range.from, range.to, range.withSize.getOrElse(false))
    ),
    Query.withParam[RangeParams, ObservableSteps, PagedResult[Observable with Entity]](
      "page",
      FieldsParser[RangeParams],
      (range, observableSteps, _) ⇒ observableSteps.page(range.from, range.to, range.withSize.getOrElse(false))
    ),
    Query[CaseSteps, List[RichCase]]("toList", (caseSteps, _) ⇒ caseSteps.richCase.toList()),
    Query[UserSteps, List[RichUser]]("toList", (userSteps, authContext) ⇒ userSteps.richUser(authContext.organisation).toList()),
    Query[AlertSteps, List[RichAlert]]("toList", (alertSteps, _) ⇒ alertSteps.richAlert.toList()),
    Query[ObservableSteps, List[RichObservable]]("toList", (observableSteps, _) ⇒ observableSteps.richObservable.toList()),
    Query[CaseSteps, TaskSteps]("listTask", (caseSteps, _) ⇒ caseSteps.tasks),
    new ParentFilterQuery(publicProperties),
    Query.output[RichCase, OutputCase],
    Query.output[Task with Entity, OutputTask],
    Query.output[RichAlert, OutputAlert],
    Query.output[RichObservable, OutputObservable],
    Query.output[RichUser, OutputUser]
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

object ParentIdFilter {

  def unapply(field: Field): Option[(String, String)] =
    FieldsParser
      .string
      .on("_type")
      .andThen("parentId")(FieldsParser.string.on("_id"))((_, _))
      .apply(field)
      .fold(Some(_), _ ⇒ None)
}

class ParentIdInputFilter(parentId: String) extends InputFilter {
  override def apply[S <: ScalliSteps[_, _, _]](
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S = {
    val stepLabel   = StepLabel[Product with Entity]()
    val vertexSteps = step.asInstanceOf[BaseVertexSteps[Product, _]]

    val findParent: GremlinScala[Vertex] =
      if (stepType =:= ru.typeOf[TaskSteps]) vertexSteps.as(stepLabel).raw.inTo[CaseTask]
      else if (stepType =:= ru.typeOf[ObservableSteps]) vertexSteps.as(stepLabel).raw.inTo[CaseObservable]
      else if (stepType =:= ru.typeOf[LogSteps]) vertexSteps.as(stepLabel).raw.inTo[TaskLog]
      else ???

    vertexSteps
      .newInstance(findParent.select(stepLabel).asInstanceOf[GremlinScala[Vertex]])
      .asInstanceOf[S]
  }
}

object ParentQueryFilter {

  def unapply(field: Field): Option[(String, Field)] =
    FieldsParser
      .string
      .on("_type")
      .map("parentQuery")(parentType ⇒ (parentType, field.get("_query")))
      .apply(field)
      .fold(Some(_), _ ⇒ None)
}

class ParentQueryInputFilter(parentFilter: InputFilter) extends InputFilter {
  override def apply[S <: ScalliSteps[_, _, _]](
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S = {
    val vertexSteps = step.asInstanceOf[BaseVertexSteps[Product, _]]

    implicit val db: Database = vertexSteps.db
    implicit val graph: Graph = vertexSteps.graph

    val (parentType, linkFn): (ru.Type, GremlinScala[Vertex] ⇒ ScalliSteps[_, _, _ <: AnyRef]) =
      if (stepType =:= ru.typeOf[TaskSteps]) ru.typeOf[CaseSteps] → ((s: GremlinScala[Vertex]) ⇒ new CaseSteps(s.inTo[CaseTask]))
      else if (stepType =:= ru.typeOf[ObservableSteps]) ru.typeOf[CaseSteps] → ((s: GremlinScala[Vertex]) ⇒ new CaseSteps(s.inTo[CaseObservable]))
      else if (stepType =:= ru.typeOf[LogSteps]) ru.typeOf[TaskSteps] → ((s: GremlinScala[Vertex]) ⇒ new TaskSteps(s.inTo[TaskLog]))
      else ???
    vertexSteps
      .where(s ⇒ parentFilter.apply(publicProperties, parentType, linkFn(s), authContext).raw)
      .asInstanceOf[S]
  }
}

class ParentFilterQuery(publicProperties: List[PublicProperty[_, _]]) extends FilterQuery(publicProperties) {
  override val paramParser: FieldsParser[InputFilter] = FieldsParser("parentIdFilter") {
    case (path, FObjOne("_and", FSeq(fields))) ⇒
      fields.zipWithIndex.validatedBy { case (field, index) ⇒ paramParser((path :/ "_and").toSeq(index), field) }.map(and)
    case (path, FObjOne("_or", FSeq(fields))) ⇒
      fields.zipWithIndex.validatedBy { case (field, index) ⇒ paramParser((path :/ "_or").toSeq(index), field) }.map(or)
    case (path, FObjOne("_not", field))                       ⇒ paramParser(path :/ "_not", field).map(not)
    case (_, FObjOne("_parent", ParentIdFilter(_, parentId))) ⇒ Good(new ParentIdInputFilter(parentId))
    case (path, FObjOne("_parent", ParentQueryFilter(_, queryField))) ⇒
      paramParser.apply(path, queryField).map(query ⇒ new ParentQueryInputFilter(query))
  }.orElse(InputFilter.fieldsParser)
  override val name: String                   = "filter"
  override def checkFrom(t: ru.Type): Boolean = t <:< ru.typeOf[TaskSteps] || t <:< ru.typeOf[ObservableSteps] || t <:< ru.typeOf[LogSteps]
  override def toType(t: ru.Type): ru.Type    = t
  override def apply(inputFilter: InputFilter, from: Any, authContext: AuthContext): Any =
    inputFilter(publicProperties, rm.classSymbol(from.getClass).toType, from.asInstanceOf[ScalliSteps[_, _, _ <: AnyRef]], authContext)
}
