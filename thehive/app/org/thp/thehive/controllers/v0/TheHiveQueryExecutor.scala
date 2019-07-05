package org.thp.thehive.controllers.v0

import scala.language.existentials
import scala.reflect.runtime.{currentMirror => rm, universe => ru}

import play.api.libs.json.JsObject

import gremlin.scala.{Graph, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FSeq, Field, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.InputFilter.{and, not, or}
import org.thp.scalligraph.query._
import org.thp.thehive.dto.v0._
import org.thp.thehive.models._
import org.thp.thehive.services.{ObservableSteps, _}

case class GetCaseParams(id: String)
case class OutputParam(from: Long, to: Long, withSize: Option[Boolean], withStats: Option[Boolean])

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
) extends QueryExecutor {
  import AlertConversion._
  import CaseConversion._
  import LogConversion._
  import ObservableConversion._
  import TaskConversion._
  import UserConversion._

  override val version: (Int, Int) = 0 -> 0

  override val publicProperties: List[PublicProperty[_, _]] =
    caseProperties(caseSrv, userSrv) ++
      taskProperties(taskSrv, userSrv) ++
      alertProperties ++
      observableProperties ++
      userProperties(userSrv) ++
      logProperties
  override val queries: Seq[ParamQuery[_]] = Seq(
    Query.initWithParam[GetCaseParams, CaseSteps](
      "getCase",
      FieldsParser[GetCaseParams],
      (p, graph, authContext) => caseSrv.get(p.id)(graph).visible(authContext)
    ),
    Query.init[CaseSteps]("listCase", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).cases),
    Query.init[UserSteps]("listUser", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).users),
    Query.init[TaskSteps]("listTask", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks),
    Query.init[AlertSteps]("listAlert", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).alerts),
    Query.init[ObservableSteps]("listObservable", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.observables),
    Query.init[LogSteps]("listLog", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).shares.tasks.logs),
    Query.withParam[OutputParam, CaseSteps, PagedResult[(RichCase, JsObject)]](
      "page",
      FieldsParser[OutputParam], {
        case (OutputParam(from, to, withSize, withStats), caseSteps, authContext) =>
          caseSteps
            .richPage(from, to, withSize.getOrElse(false)) {
              case c if withStats.contains(true) =>
                caseSteps.newInstance(c).richCaseWithCustomRenderer(caseStatsRenderer(authContext, db, caseSteps.graph)).raw
              case c =>
                caseSteps.newInstance(c).richCase.raw.map(_ -> JsObject.empty)
            }
      }
    ),
    Query.withParam[OutputParam, AlertSteps, AlertSteps](
      "range",
      FieldsParser[OutputParam],
      (range, alertSteps, _) => alertSteps.range(range.from, range.to)
    ),
    Query.withParam[OutputParam, LogSteps, PagedResult[RichLog]](
      "page",
      FieldsParser[OutputParam],
      (range, logSteps, _) => logSteps.richLog.page(range.from, range.to, range.withSize.getOrElse(false))
    ),
    Query.withParam[OutputParam, TaskSteps, PagedResult[RichTask]](
      "page",
      FieldsParser[OutputParam],
      (range, taskSteps, _) => taskSteps.richTask.page(range.from, range.to, range.withSize.getOrElse(false))
    ),
    Query.withParam[OutputParam, UserSteps, PagedResult[RichUser]](
      "page",
      FieldsParser[OutputParam],
      (range, userSteps, authContext) => userSteps.richUser(authContext.organisation).page(range.from, range.to, range.withSize.getOrElse(false))
    ),
    Query.withParam[OutputParam, ObservableSteps, PagedResult[(RichObservable, JsObject)]](
      "page",
      FieldsParser[OutputParam], {
        case (OutputParam(from, to, withSize, withStats), observableSteps, authContext) =>
          observableSteps
            .richPage(from, to, withSize.getOrElse(false)) {
              case c if withStats.contains(true) =>
                observableSteps.newInstance(c).richObservableWithCustomRenderer(observableStatsRenderer(authContext, db, observableSteps.graph)).raw
              case c =>
                observableSteps.newInstance(c).richObservable.raw.map(_ -> JsObject.empty)
            }
      }
    ),
    Query.withParam[OutputParam, AlertSteps, PagedResult[(RichAlert, Seq[RichObservable])]](
      "page",
      FieldsParser[OutputParam],
      (range, alertSteps, _) =>
        alertSteps
          .richAlert
          .page(range.from, range.to, range.withSize.getOrElse(false))
          .map { richAlert =>
            richAlert -> alertSrv.get(richAlert.alert)(alertSteps.graph).observables.richObservable.toList
          }
    ),
    Query[CaseSteps, Seq[RichCase]]("toList", (caseSteps, _) => caseSteps.richCase.toList),
    Query[TaskSteps, Seq[RichTask]]("toList", (taskSteps, _) => taskSteps.richTask.toList),
    Query[UserSteps, Seq[RichUser]]("toList", (userSteps, authContext) => userSteps.richUser(authContext.organisation).toList),
    Query[AlertSteps, Seq[RichAlert]]("toList", (alertSteps, _) => alertSteps.richAlert.toList),
    Query[ObservableSteps, Seq[RichObservable]]("toList", (observableSteps, _) => observableSteps.richObservable.toList),
    Query[CaseSteps, TaskSteps]("listTask", (caseSteps, authContext) => caseSteps.tasks(authContext)),
    Query[CaseSteps, Seq[(RichCase, JsObject)]](
      "listWithStats",
      (caseSteps, authContext) => caseSteps.richCaseWithCustomRenderer(caseStatsRenderer(authContext, db, caseSteps.graph)).toList
    ),
    new ParentFilterQuery(publicProperties),
    Query.output[RichCase, OutputCase],
    Query.output[RichTask, OutputTask],
    Query.output[RichAlert, OutputAlert],
    Query.output[RichObservable, OutputObservable],
    Query.output[RichUser, OutputUser],
    Query.output[RichLog, OutputLog],
    Query.output[(RichAlert, Seq[RichObservable]), OutputAlert],
    Query.output[(RichCase, JsObject), OutputCase],
    Query.output[(RichObservable, JsObject), OutputObservable]
  )
}

object ParentIdFilter {

  def unapply(field: Field): Option[(String, String)] =
    FieldsParser
      .string
      .on("_type")
      .andThen("parentId")(FieldsParser.string.on("_id"))((_, _))
      .apply(field)
      .fold(Some(_), _ => None)
}

class ParentIdInputFilter(parentId: String) extends InputFilter {
  override def apply[S <: ScalliSteps[_, _, _]](
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S = {
    val s =
      if (stepType =:= ru.typeOf[TaskSteps]) step.asInstanceOf[TaskSteps].where(_.`case`.has(Key("_id"), P.eq(parentId)))
      else if (stepType =:= ru.typeOf[ObservableSteps]) step.asInstanceOf[ObservableSteps].where(_.`case`.has(Key("_id"), P.eq(parentId)))
      else if (stepType =:= ru.typeOf[LogSteps]) step.asInstanceOf[LogSteps].where(_.task.has(Key("_id"), P.eq(parentId)))
      else ???
    s.asInstanceOf[S]
  }
}

object ParentQueryFilter {

  def unapply(field: Field): Option[(String, Field)] =
    FieldsParser
      .string
      .on("_type")
      .map("parentQuery")(parentType => (parentType, field.get("_query")))
      .apply(field)
      .fold(Some(_), _ => None)
}

class ParentQueryInputFilter(parentFilter: InputFilter) extends InputFilter {
  override def apply[S <: ScalliSteps[_, Vertex, S]](
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S = {
    val vertexSteps = step.asInstanceOf[BaseVertexSteps[Product, _]]

    implicit val db: Database = vertexSteps.db
    implicit val graph: Graph = vertexSteps.graph

    vertexSteps
      .filter { s =>
        if (stepType =:= ru.typeOf[TaskSteps])
          parentFilter.apply(publicProperties, ru.typeOf[CaseSteps], new TaskSteps(s).`case`, authContext).raw
        else if (stepType =:= ru.typeOf[ObservableSteps])
          parentFilter.apply(publicProperties, ru.typeOf[CaseSteps], new ObservableSteps(s).`case`, authContext).raw
        else if (stepType =:= ru.typeOf[LogSteps])
          parentFilter.apply(publicProperties, ru.typeOf[TaskSteps], new LogSteps(s).task, authContext).raw
        else ???
      }
      .asInstanceOf[S]
  }
}

class ParentFilterQuery(publicProperties: List[PublicProperty[_, _]]) extends FilterQuery(publicProperties) {
  override val paramParser: FieldsParser[InputFilter] = FieldsParser("parentIdFilter") {
    case (path, FObjOne("_and", FSeq(fields))) =>
      fields.zipWithIndex.validatedBy { case (field, index) => paramParser((path :/ "_and").toSeq(index), field) }.map(and)
    case (path, FObjOne("_or", FSeq(fields))) =>
      fields.zipWithIndex.validatedBy { case (field, index) => paramParser((path :/ "_or").toSeq(index), field) }.map(or)
    case (path, FObjOne("_not", field))                       => paramParser(path :/ "_not", field).map(not)
    case (_, FObjOne("_parent", ParentIdFilter(_, parentId))) => Good(new ParentIdInputFilter(parentId))
    case (path, FObjOne("_parent", ParentQueryFilter(_, queryField))) =>
      paramParser.apply(path, queryField).map(query => new ParentQueryInputFilter(query))
  }.orElse(InputFilter.fieldsParser)
  override val name: String                   = "filter"
  override def checkFrom(t: ru.Type): Boolean = t <:< ru.typeOf[TaskSteps] || t <:< ru.typeOf[ObservableSteps] || t <:< ru.typeOf[LogSteps]
  override def toType(t: ru.Type): ru.Type    = t
  override def apply(inputFilter: InputFilter, from: Any, authContext: AuthContext): Any =
    inputFilter(
      publicProperties,
      rm.classSymbol(from.getClass).toType,
      from.asInstanceOf[X forSome { type X <: BaseVertexSteps[_, X] }],
      authContext
    )
}
