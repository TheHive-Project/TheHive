package org.thp.thehive.connector.cortex.controllers.v0

import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FSeq, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.InputFilter.{and, not, or}
import org.thp.scalligraph.query._
import org.thp.scalligraph.services._
import org.thp.thehive.connector.cortex.dto.v0.OutputJob
import org.thp.thehive.connector.cortex.models.{Job, ObservableJob}
import org.thp.thehive.connector.cortex.services.{JobSrv, JobSteps}
import org.thp.thehive.controllers.v0.{ParentFilterQuery, ParentIdFilter, ParentIdInputFilter, ParentQueryFilter}
import org.thp.thehive.services.ObservableSteps

import scala.reflect.runtime.{universe => ru}

/**
  * Range param case class for search query parsing
  *
  * @param from the start
  * @param to the end nb
  * @param withSize with or without the total length
  */
case class RangeParams(from: Long, to: Long, withSize: Option[Boolean])

@Singleton
class CortexQueryExecutor @Inject()(
    implicit val db: Database,
    jobSrv: JobSrv
) extends QueryExecutor
    with JobConversion {

  override val version: (Int, Int)                          = 0 -> 0
  override val publicProperties: List[PublicProperty[_, _]] = jobProperties

  override val queries: Seq[ParamQuery[_]] = Seq(
    Query.init[JobSteps]("listJob", (graph, authContext) => jobSrv.initSteps(graph).visible(authContext)),
    Query.withParam[RangeParams, JobSteps, PagedResult[Job with Entity]](
      "page",
      FieldsParser[RangeParams],
      (range, jobSteps, _) => jobSteps.page(range.from, range.to, range.withSize.getOrElse(false))
    ),
    Query[JobSteps, List[Job with Entity]]("toList", (jobSteps, _) => jobSteps.toList()),
    new CortexParentFilterQuery(publicProperties),
    Query.output[Job with Entity, OutputJob]
  )
}

/**
  * The parent query parser traversing properly to appropriate parent
  *
  * @param parentFilter the query
  */
class CortexParentQueryInputFilter(parentFilter: InputFilter) extends InputFilter {
  override def apply[S <: ScalliSteps[_, _, _]](
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S = {
    val vertexSteps = step.asInstanceOf[BaseVertexSteps[Product, _]]

    implicit val db: Database = vertexSteps.db
    implicit val graph: Graph = vertexSteps.graph

    val (parentType, linkFn): (ru.Type, GremlinScala[Vertex] => ScalliSteps[_, _, _ <: AnyRef]) =
      if (stepType =:= ru.typeOf[JobSteps]) ru.typeOf[ObservableSteps] -> ((s: GremlinScala[Vertex]) => new ObservableSteps(s.inTo[ObservableJob]))
      else ???

    vertexSteps
      .where(s => parentFilter.apply(publicProperties, parentType, linkFn(s), authContext).raw)
      .asInstanceOf[S]
  }
}

/**
  * Query parser for parent traversing heavily relaying on the v0/TheHiveQueryExecutor ParentFilterQuery and dependencies
  *
  * @param publicProperties the models properties
  */
class CortexParentFilterQuery(publicProperties: List[PublicProperty[_, _]]) extends ParentFilterQuery(publicProperties) {
  override val paramParser: FieldsParser[InputFilter] = FieldsParser("parentIdFilter") {
    case (path, FObjOne("_and", FSeq(fields))) =>
      fields.zipWithIndex.validatedBy { case (field, index) => paramParser((path :/ "_and").toSeq(index), field) }.map(and)
    case (path, FObjOne("_or", FSeq(fields))) =>
      fields.zipWithIndex.validatedBy { case (field, index) => paramParser((path :/ "_or").toSeq(index), field) }.map(or)
    case (path, FObjOne("_not", field))                       => paramParser(path :/ "_not", field).map(not)
    case (_, FObjOne("_parent", ParentIdFilter(_, parentId))) => Good(new ParentIdInputFilter(parentId))
    case (path, FObjOne("_parent", ParentQueryFilter(_, queryField))) =>
      paramParser.apply(path, queryField).map(query => new CortexParentQueryInputFilter(query))
  }.orElse(InputFilter.fieldsParser)

  override def checkFrom(t: ru.Type): Boolean = t <:< ru.typeOf[JobSteps]
}
