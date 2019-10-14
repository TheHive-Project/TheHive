package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FSeq, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.InputFilter.{and, not, or}
import org.thp.scalligraph.query._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{BaseTraversal, BaseVertexSteps}
import org.thp.thehive.connector.cortex.services.{JobSrv, JobSteps}
import org.thp.thehive.controllers.v0._
import org.thp.thehive.services.ObservableSteps

import scala.reflect.runtime.{currentMirror => rm, universe => ru}

@Singleton
class CortexQueryExecutor @Inject()(
    jobCtrl: JobCtrl,
    jobSrv: JobSrv,
    queryCtrlBuilder: QueryCtrlBuilder,
    implicit val db: Database,
    reportCtrl: AnalyzerTemplateCtrl,
    actionCtrl: ActionCtrl
) extends QueryExecutor {
  override lazy val publicProperties
      : List[PublicProperty[_, _]] = jobCtrl.publicProperties ++ reportCtrl.publicProperties ++ actionCtrl.publicProperties
  override lazy val queries: Seq[ParamQuery[_]] =
    new CortexParentFilterQuery(db, publicProperties) ::
      actionCtrl.initialQuery ::
      actionCtrl.pageQuery ::
      actionCtrl.outputQuery ::
      jobCtrl.initialQuery ::
      jobCtrl.pageQuery ::
      jobCtrl.outputQuery ::
      reportCtrl.initialQuery ::
      reportCtrl.pageQuery ::
      reportCtrl.outputQuery ::
      Nil
  override lazy val filterQuery    = new CortexParentFilterQuery(db, publicProperties)
  override val version: (Int, Int) = 0 -> 0
  val job: QueryCtrl               = queryCtrlBuilder.apply(jobCtrl, this)
  val report: QueryCtrl            = queryCtrlBuilder.apply(reportCtrl, this)
  val action: QueryCtrl            = queryCtrlBuilder.apply(actionCtrl, this)
}

/**
  * The parent query parser traversing properly to appropriate parent
  *
  * @param parentFilter the query
  */
class CortexParentQueryInputFilter(parentFilter: InputFilter) extends InputFilter {
  override def apply[S <: BaseTraversal](
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S =
    if (stepType =:= ru.typeOf[JobSteps])
      step.filter(t => parentFilter.apply(db, publicProperties, ru.typeOf[ObservableSteps], t.asInstanceOf[JobSteps].observable, authContext))
    else ???
}

/**
  * Query parser for parent traversing heavily relaying on the v0/TheHiveQueryExecutor ParentFilterQuery and dependencies
  *
  * @param publicProperties the models properties
  */
class CortexParentFilterQuery(db: Database, publicProperties: List[PublicProperty[_, _]]) extends FilterQuery(db, publicProperties) {
  override val name: String = "filter"

  override def paramParser(tpe: ru.Type, properties: Seq[PublicProperty[_, _]]): FieldsParser[InputFilter] =
    FieldsParser("parentIdFilter") {
      case (path, FObjOne("_and", FSeq(fields))) =>
        fields.zipWithIndex.validatedBy { case (field, index) => paramParser(tpe, properties)((path :/ "_and").toSeq(index), field) }.map(and)
      case (path, FObjOne("_or", FSeq(fields))) =>
        fields.zipWithIndex.validatedBy { case (field, index) => paramParser(tpe, properties)((path :/ "_or").toSeq(index), field) }.map(or)
      case (path, FObjOne("_not", field))                       => paramParser(tpe, properties)(path :/ "_not", field).map(not)
      case (_, FObjOne("_parent", ParentIdFilter(_, parentId))) => Good(new ParentIdInputFilter(parentId))
      case (path, FObjOne("_parent", ParentQueryFilter(_, queryField))) =>
        paramParser(tpe, properties).apply(path, queryField).map(query => new CortexParentQueryInputFilter(query))
    }.orElse(InputFilter.fieldsParser(tpe, properties))

  override def checkFrom(t: ru.Type): Boolean = SubType(t, ru.typeOf[JobSteps])
  override def toType(t: ru.Type): ru.Type    = t
  override def apply(inputFilter: InputFilter, from: Any, authContext: AuthContext): Any =
    inputFilter(
      db,
      publicProperties,
      rm.classSymbol(from.getClass).toType,
      from.asInstanceOf[BaseVertexSteps],
      authContext
    )
}
