package org.thp.thehive.controllers.v0

import scala.language.existentials
import scala.reflect.runtime.{currentMirror => rm, universe => ru}

import gremlin.scala.{Graph, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FSeq, Field, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.{InputFilter, _}
import org.thp.thehive.services.{ObservableSteps, _}

case class OutputParam(from: Long, to: Long, withStats: Boolean)

@Singleton
class TheHiveQueryExecutor @Inject()(
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
    logCtrl: LogCtrl,
    observableCtrl: ObservableCtrl,
    alertCtrl: AlertCtrl,
    userCtrl: UserCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    dashboardCtrl: DashboardCtrl,
    queryCtrlBuilder: QueryCtrlBuilder
) extends QueryExecutor {

  lazy val controllers: List[QueryableCtrl] =
    caseCtrl :: taskCtrl :: logCtrl :: observableCtrl :: alertCtrl :: userCtrl :: caseTemplateCtrl :: dashboardCtrl :: Nil
  override val version: (Int, Int) = 0 -> 0

  override lazy val publicProperties: List[PublicProperty[_, _]] = controllers.flatMap(_.publicProperties)

  override lazy val queries: Seq[ParamQuery[_]] =
    new ParentFilterQuery(publicProperties).asInstanceOf[ParamQuery[_]] ::
      controllers.map(_.initialQuery) :::
      controllers.map(_.pageQuery) :::
      controllers.map(_.outputQuery)

  val `case`: QueryCtrl       = queryCtrlBuilder.apply(caseCtrl, this)
  val task: QueryCtrl         = queryCtrlBuilder.apply(taskCtrl, this)
  val log: QueryCtrl          = queryCtrlBuilder.apply(logCtrl, this)
  val alert: QueryCtrl        = queryCtrlBuilder.apply(alertCtrl, this)
  val user: QueryCtrl         = queryCtrlBuilder.apply(userCtrl, this)
  val caseTemplate: QueryCtrl = queryCtrlBuilder.apply(caseTemplateCtrl, this)
  val observable: QueryCtrl   = queryCtrlBuilder.apply(observableCtrl, this)
  val dashboard: QueryCtrl    = queryCtrlBuilder.apply(dashboardCtrl, this)
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
  override def paramParser(tpe: ru.Type, properties: Seq[PublicProperty[_, _]]): FieldsParser[InputFilter] =
    FieldsParser("parentIdFilter") {
      case (path, FObjOne("_and", FSeq(fields))) =>
        fields
          .zipWithIndex
          .validatedBy { case (field, index) => paramParser(tpe, properties)((path :/ "_and").toSeq(index), field) }
          .map(InputFilter.and)
      case (path, FObjOne("_or", FSeq(fields))) =>
        fields
          .zipWithIndex
          .validatedBy { case (field, index) => paramParser(tpe, properties)((path :/ "_or").toSeq(index), field) }
          .map(InputFilter.or)
      case (path, FObjOne("_not", field))                       => paramParser(tpe, properties)(path :/ "_not", field).map(InputFilter.not)
      case (_, FObjOne("_parent", ParentIdFilter(_, parentId))) => Good(new ParentIdInputFilter(parentId))
      case (path, FObjOne("_parent", ParentQueryFilter(_, queryField))) =>
        paramParser(tpe, properties).apply(path, queryField).map(query => new ParentQueryInputFilter(query))
    }.orElse(InputFilter.fieldsParser(tpe, properties))
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
