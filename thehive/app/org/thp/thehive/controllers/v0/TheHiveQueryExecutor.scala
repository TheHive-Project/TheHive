package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.scalactic.Good
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{Field, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.{InputFilter, _}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{BaseTraversal, BaseVertexSteps}
import org.thp.thehive.services.{ObservableSteps, _}

import scala.reflect.runtime.{universe => ru}

case class OutputParam(from: Long, to: Long, withStats: Boolean, withParents: Int)

@Singleton
class TheHiveQueryExecutor @Inject() (
    override val db: Database,
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
    logCtrl: LogCtrl,
    observableCtrl: ObservableCtrl,
    alertCtrl: AlertCtrl,
    userCtrl: UserCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    dashboardCtrl: DashboardCtrl,
    organisationCtrl: OrganisationCtrl,
    auditCtrl: AuditCtrl,
    profileCtrl: ProfileCtrl,
    tagCtrl: TagCtrl,
    pageCtrl: PageCtrl,
    observableTypeCtrl: ObservableTypeCtrl,
    queryCtrlBuilder: QueryCtrlBuilder
) extends QueryExecutor {

  lazy val controllers: List[QueryableCtrl] =
    caseCtrl :: taskCtrl :: logCtrl :: observableCtrl :: alertCtrl :: userCtrl :: caseTemplateCtrl :: dashboardCtrl :: organisationCtrl :: auditCtrl :: profileCtrl :: tagCtrl :: pageCtrl :: observableTypeCtrl :: Nil
  override lazy val publicProperties: List[PublicProperty[_, _]] = controllers.flatMap(_.publicProperties)

  val childTypes: PartialFunction[(ru.Type, String), ru.Type] = {
    case (tpe, "case_task_log") if SubType(tpe, ru.typeOf[TaskSteps]) => ru.typeOf[LogSteps]
    case (tpe, "case_task") if SubType(tpe, ru.typeOf[CaseSteps])     => ru.typeOf[TaskSteps]
    case (tpe, "case_artifact") if SubType(tpe, ru.typeOf[CaseSteps]) => ru.typeOf[ObservableSteps]
  }
  val parentTypes: PartialFunction[ru.Type, ru.Type] = {
    case tpe if SubType(tpe, ru.typeOf[TaskSteps])       => ru.typeOf[CaseSteps]
    case tpe if SubType(tpe, ru.typeOf[ObservableSteps]) => ru.typeOf[CaseSteps]
    case tpe if SubType(tpe, ru.typeOf[LogSteps])        => ru.typeOf[ObservableSteps]
  }
  override val customFilterQuery: FilterQuery = FilterQuery(db, publicProperties) { (tpe, globalParser) =>
    FieldsParser.debug("parentChildFilter") {
      case (_, FObjOne("_parent", ParentIdFilter(_, parentId))) if parentTypes.isDefinedAt(tpe) =>
        Good(new ParentIdInputFilter(parentId))
      case (path, FObjOne("_parent", ParentQueryFilter(_, parentFilterField))) if parentTypes.isDefinedAt(tpe) =>
        globalParser(parentTypes(tpe)).apply(path, parentFilterField).map(query => new ParentQueryInputFilter(query))
      case (path, FObjOne("_child", ChildQueryFilter(childType, childQueryField))) if childTypes.isDefinedAt((tpe, childType)) =>
        globalParser(childTypes((tpe, childType))).apply(path, childQueryField).map(query => new ChildQueryInputFilter(childType, query))
    }
  }

  override lazy val queries: Seq[ParamQuery[_]] =
    controllers.map(_.initialQuery) :::
      controllers.map(_.getQuery) :::
      controllers.map(_.pageQuery) :::
      controllers.map(_.outputQuery) :::
      controllers.flatMap(_.extraQueries)
  override val version: (Int, Int) = 0 -> 0
  val `case`: QueryCtrl            = queryCtrlBuilder(caseCtrl, this)
  val task: QueryCtrl              = queryCtrlBuilder(taskCtrl, this)
  val log: QueryCtrl               = queryCtrlBuilder(logCtrl, this)
  val alert: QueryCtrl             = queryCtrlBuilder(alertCtrl, this)
  val user: QueryCtrl              = queryCtrlBuilder(userCtrl, this)
  val caseTemplate: QueryCtrl      = queryCtrlBuilder(caseTemplateCtrl, this)
  val observable: QueryCtrl        = queryCtrlBuilder(observableCtrl, this)
  val observableType: QueryCtrl    = queryCtrlBuilder(observableTypeCtrl, this)
  val dashboard: QueryCtrl         = queryCtrlBuilder(dashboardCtrl, this)
  val organisation: QueryCtrl      = queryCtrlBuilder(organisationCtrl, this)
  val audit: QueryCtrl             = queryCtrlBuilder(auditCtrl, this)
  val profile: QueryCtrl           = queryCtrlBuilder(profileCtrl, this)
  val tag: QueryCtrl               = queryCtrlBuilder(tagCtrl, this)
  val page: QueryCtrl              = queryCtrlBuilder(pageCtrl, this)
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
  override def apply[S <: BaseTraversal](
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S =
    if (stepType =:= ru.typeOf[TaskSteps]) step.asInstanceOf[TaskSteps].filter(_.`case`.getByIds(parentId)).asInstanceOf[S]
    else if (stepType =:= ru.typeOf[ObservableSteps]) step.asInstanceOf[ObservableSteps].filter(_.`case`.getByIds(parentId)).asInstanceOf[S]
    else if (stepType =:= ru.typeOf[LogSteps]) step.asInstanceOf[LogSteps].filter(_.task.getByIds(parentId)).asInstanceOf[S]
    else throw BadRequestError(s"$stepType hasn't parent")
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
  override def apply[S <: BaseTraversal](
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S =
    if (stepType =:= ru.typeOf[TaskSteps])
      step.filter(t => parentFilter.apply(db, publicProperties, ru.typeOf[CaseSteps], t.asInstanceOf[TaskSteps].`case`, authContext))
    else if (stepType =:= ru.typeOf[ObservableSteps])
      step.filter(t => parentFilter.apply(db, publicProperties, ru.typeOf[CaseSteps], t.asInstanceOf[ObservableSteps].`case`, authContext))
    else if (stepType =:= ru.typeOf[LogSteps])
      step.filter(t => parentFilter.apply(db, publicProperties, ru.typeOf[TaskSteps], t.asInstanceOf[LogSteps].task, authContext))
    else throw BadRequestError(s"$stepType hasn't parent")
}

object ChildQueryFilter {
  def unapply(field: Field): Option[(String, Field)] =
    FieldsParser
      .string
      .on("_type")
      .map("childQuery")(childType => (childType, field.get("_query")))
      .apply(field)
      .fold(Some(_), _ => None)
}

class ChildQueryInputFilter(childType: String, childFilter: InputFilter) extends InputFilter {
  override def apply[S <: BaseVertexSteps](
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      stepType: ru.Type,
      step: S,
      authContext: AuthContext
  ): S =
    if (stepType =:= ru.typeOf[CaseSteps] && childType == "case_task")
      step.filter(t => childFilter.apply(db, publicProperties, ru.typeOf[TaskSteps], t.asInstanceOf[CaseSteps].tasks(authContext), authContext))
    else if (stepType =:= ru.typeOf[CaseSteps] && childType == "case_artifact")
      step.filter(t =>
        childFilter.apply(db, publicProperties, ru.typeOf[ObservableSteps], t.asInstanceOf[CaseSteps].observables(authContext), authContext)
      )
    else if (stepType =:= ru.typeOf[TaskSteps] && childType == "case_task_log")
      step.filter(t => childFilter.apply(db, publicProperties, ru.typeOf[LogSteps], t.asInstanceOf[TaskSteps].logs, authContext))
    else throw BadRequestError(s"$stepType hasn't child of type $childType")
}
