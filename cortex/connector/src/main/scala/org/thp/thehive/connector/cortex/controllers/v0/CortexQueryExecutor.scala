package org.thp.thehive.connector.cortex.controllers.v0

import scala.reflect.runtime.{universe => ru}

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FSeq, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.InputFilter.{and, not, or}
import org.thp.scalligraph.query._
import org.thp.thehive.connector.cortex.services.{JobSrv, JobSteps}
import org.thp.thehive.controllers.v0._

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
    jobCtrl: JobCtrl,
    jobSrv: JobSrv,
    queryCtrlBuilder: QueryCtrlBuilder,
    implicit val db: Database
) extends QueryExecutor {
  override val version: (Int, Int)                               = 0 -> 0
  override lazy val publicProperties: List[PublicProperty[_, _]] = jobCtrl.publicProperties

  override lazy val queries: Seq[ParamQuery[_]] = jobCtrl.initialQuery :: jobCtrl.pageQuery :: jobCtrl.outputQuery :: Nil

  val job: QueryCtrl = queryCtrlBuilder.apply(jobCtrl, this)
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
    vertexSteps
      .filter { s =>
        if (stepType =:= ru.typeOf[JobSteps])
          parentFilter.apply(publicProperties, ru.typeOf[JobSteps], new JobSteps(s).observable, authContext).raw
        else ???
      }
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
