package org.thp.thehive.connector.cortex.controllers.v0

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import org.scalactic.Good
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FieldsParser
import org.thp.scalligraph.models._
import org.thp.scalligraph.query._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{BaseTraversal, BaseVertexSteps}
import org.thp.thehive.connector.cortex.services.{JobSteps, RichObservableSteps}
import org.thp.thehive.controllers.v0._
import org.thp.thehive.services.ObservableSteps

import scala.reflect.runtime.{universe => ru}

@Singleton
class CortexQueryExecutor @Inject() (
    jobCtrl: JobCtrl,
    queryCtrlBuilder: QueryCtrlBuilder,
    @Named("with-thehive-cortex-schema") implicit val db: Database,
    reportCtrl: AnalyzerTemplateCtrl,
    actionCtrl: ActionCtrl
) extends QueryExecutor {
  override lazy val publicProperties: List[PublicProperty[_, _]] =
    jobCtrl.publicProperties ++ reportCtrl.publicProperties ++ actionCtrl.publicProperties
  override lazy val queries: Seq[ParamQuery[_]] =
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

  val childTypes: PartialFunction[(ru.Type, String), ru.Type] = {
    case (tpe, "case_artifact_job") if SubType(tpe, ru.typeOf[ObservableSteps]) => ru.typeOf[ObservableSteps]
  }
  val parentTypes: PartialFunction[ru.Type, ru.Type] = {
    case tpe if SubType(tpe, ru.typeOf[JobSteps]) => ru.typeOf[ObservableSteps]
  }

  override val customFilterQuery: FilterQuery = FilterQuery(db, publicProperties) { (tpe, globalParser) =>
    FieldsParser("parentChildFilter") {
      case (_, FObjOne("_parent", ParentIdFilter(_, parentId))) if parentTypes.isDefinedAt(tpe) =>
        Good(new CortexParentIdInputFilter(parentId))
      case (path, FObjOne("_parent", ParentQueryFilter(_, parentFilterField))) if parentTypes.isDefinedAt(tpe) =>
        globalParser(parentTypes(tpe)).apply(path, parentFilterField).map(query => new CortexParentQueryInputFilter(query))
      case (path, FObjOne("_child", ChildQueryFilter(childType, childQueryField))) if childTypes.isDefinedAt((tpe, childType)) =>
        globalParser(childTypes((tpe, childType))).apply(path, childQueryField).map(query => new CortexChildQueryInputFilter(childType, query))
    }
  }

  override val version: (Int, Int) = 0 -> 0
  val job: QueryCtrl               = queryCtrlBuilder.apply(jobCtrl, this)
  val report: QueryCtrl            = queryCtrlBuilder.apply(reportCtrl, this)
  val action: QueryCtrl            = queryCtrlBuilder.apply(actionCtrl, this)
}

class CortexParentIdInputFilter(parentId: String) extends InputFilter {
  override def apply[S <: BaseTraversal](
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S =
    if (stepType =:= ru.typeOf[JobSteps]) step.asInstanceOf[JobSteps].filter(_.observable.getByIds(parentId)).asInstanceOf[S]
    else throw BadRequestError(s"$stepType hasn't parent")
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
    else throw BadRequestError(s"$stepType hasn't parent")
}

class CortexChildQueryInputFilter(childType: String, childFilter: InputFilter) extends InputFilter {
  override def apply[S <: BaseVertexSteps](
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S =
    if (stepType =:= ru.typeOf[ObservableSteps] && childType == "case_artifact_job")
      step.filter(t => childFilter.apply(db, publicProperties, ru.typeOf[JobSteps], t.asInstanceOf[ObservableSteps].jobs, authContext))
    else throw BadRequestError(s"$stepType hasn't child of type $childType")
}
