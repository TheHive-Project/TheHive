package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.scalactic.Good
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FObject, Field, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.RichType
import org.thp.thehive.models.{Case, Log, Observable, Task}
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.TaskOps._

import scala.reflect.runtime.{universe => ru}

case class OutputParam(from: Long, to: Long, withStats: Boolean, withParents: Int)

object OutputParam {
  implicit val parser: FieldsParser[OutputParam] = FieldsParser[OutputParam]("OutputParam") {
    case (_, field: FObject) =>
      for {
        from        <- FieldsParser.long.on("from")(field)
        to          <- FieldsParser.long.on("to")(field)
        withStats   <- FieldsParser.boolean.optional.on("withStats")(field)
        withParents <- FieldsParser.int.optional.on("withParents")(field)
      } yield OutputParam(from, to, withStats.getOrElse(false), withParents.getOrElse(0))
  }
}

@Singleton
class TheHiveQueryExecutor @Inject() (
    @Named("with-thehive-schema") override val db: Database,
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

  def metaProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Product]
      .property("createdBy", UMapping.string)(_.rename("_createdBy").readonly)
      .property("createdAt", UMapping.date)(_.rename("_createdAt").readonly)
      .property("updatedBy", UMapping.string.optional)(_.rename("_updatedBy").readonly)
      .property("updatedAt", UMapping.date.optional)(_.rename("_updatedAt").readonly)
      .build

  override lazy val publicProperties: List[PublicProperty[_, _]] = controllers.flatMap(_.publicProperties) ::: metaProperties

  val childTypes: PartialFunction[(ru.Type, String), ru.Type] = {
    case (tpe, "case_task_log") if SubType(tpe, ru.typeOf[Traversal.V[Task]]) => ru.typeOf[Traversal.V[Log]]
    case (tpe, "case_task") if SubType(tpe, ru.typeOf[Traversal.V[Case]])     => ru.typeOf[Traversal.V[Task]]
    case (tpe, "case_artifact") if SubType(tpe, ru.typeOf[Traversal.V[Case]]) => ru.typeOf[Traversal.V[Observable]]
  }
  val parentTypes: PartialFunction[ru.Type, ru.Type] = {
    case tpe if SubType(tpe, ru.typeOf[Traversal.V[Task]])       => ru.typeOf[Traversal.V[Case]]
    case tpe if SubType(tpe, ru.typeOf[Traversal.V[Observable]]) => ru.typeOf[Traversal.V[Case]]
    case tpe if SubType(tpe, ru.typeOf[Traversal.V[Log]])        => ru.typeOf[Traversal.V[Observable]]
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

class ParentIdInputFilter(parentId: String) extends InputQuery[Traversal.Unk, Traversal.Unk] {
  override def apply(
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Unk =
    RichType
      .getTypeArgs(traversalType, ru.typeOf[Traversal[_, _, _]])
      .headOption
      .collect {
        case t if t <:< ru.typeOf[Task] => traversal.asInstanceOf[Traversal.V[Task]].filter(_.`case`.getByIds(parentId)).asInstanceOf[Traversal.Unk]
        case t if t <:< ru.typeOf[Observable] =>
          traversal.asInstanceOf[Traversal.V[Observable]].filter(_.`case`.getByIds(parentId)).asInstanceOf[Traversal.Unk]
        case t if t <:< ru.typeOf[Log] => traversal.asInstanceOf[Traversal.V[Log]].filter(_.task.getByIds(parentId)).asInstanceOf[Traversal.Unk]
      }
      .getOrElse(throw BadRequestError(s"$traversalType hasn't parent"))
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

class ParentQueryInputFilter(parentFilter: InputQuery[Traversal.Unk, Traversal.Unk]) extends InputQuery[Traversal.Unk, Traversal.Unk] {
  override def apply(
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Unk = {
    def filter[F, T: ru.TypeTag](t: Traversal.V[F] => Traversal.V[T]): Traversal.Unk =
      parentFilter(
        db,
        publicProperties,
        ru.typeOf[Traversal.V[T]],
        t(traversal.asInstanceOf[Traversal.V[F]]).asInstanceOf[Traversal.Unk],
        authContext
      )

    RichType
      .getTypeArgs(traversalType, ru.typeOf[Traversal[_, _, _]])
      .headOption
      .collect {
        case t if t <:< ru.typeOf[Task]       => filter[Task, Case](_.`case`)
        case t if t <:< ru.typeOf[Observable] => filter[Observable, Case](_.`case`)
        case t if t <:< ru.typeOf[Log]        => filter[Log, Task](_.task)
      }
      .getOrElse(throw BadRequestError(s"$traversalType hasn't parent"))
  }
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

class ChildQueryInputFilter(childType: String, childFilter: InputQuery[Traversal.Unk, Traversal.Unk])
    extends InputQuery[Traversal.Unk, Traversal.Unk] {
  override def apply(
      db: Database,
      publicProperties: List[PublicProperty[_, _]],
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Unk = {
    def filter[F, T: ru.TypeTag](t: Traversal.V[F] => Traversal.V[T]): Traversal.Unk =
      childFilter(
        db,
        publicProperties,
        ru.typeOf[Traversal.V[T]],
        t(traversal.asInstanceOf[Traversal.V[F]]).asInstanceOf[Traversal.Unk],
        authContext
      )

    RichType
      .getTypeArgs(traversalType, ru.typeOf[Traversal[_, _, _]])
      .headOption
      .collect {
        case t if t <:< ru.typeOf[Case] && childType == "case_task"     => filter[Case, Task](_.tasks(authContext))
        case t if t <:< ru.typeOf[Case] && childType == "case_artifact" => filter[Case, Observable](_.observables(authContext))
        case t if t <:< ru.typeOf[Task] && childType == "case_task_log" => filter[Task, Log](_.logs)
      }
      .getOrElse(throw BadRequestError(s"$traversalType hasn't child $childType"))
  }
}
