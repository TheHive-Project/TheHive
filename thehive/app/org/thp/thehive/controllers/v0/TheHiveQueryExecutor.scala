package org.thp.thehive.controllers.v0

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.scalactic.Good
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FObject, Field, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query._
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.utils.RichType
import org.thp.scalligraph.{BadRequestError, EntityId}
import org.thp.thehive.models._
import org.thp.thehive.services.TheHiveOpsNoDeps

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

class TheHiveQueryExecutor(
    override val db: Database,
    appConfig: ApplicationConfig,
    alert: PublicAlert,
    audit: PublicAudit,
    `case`: PublicCase,
    caseTemplate: PublicCaseTemplate,
    customField: PublicCustomField,
    observableType: PublicObservableType,
    dashboard: PublicDashboard,
    log: PublicLog,
    observable: PublicObservable,
    organisation: PublicOrganisation,
    page: PublicPage,
    profile: PublicProfile,
    tag: PublicTag,
    task: PublicTask,
    user: PublicUser
) extends QueryExecutor {

  lazy val publicDatas: Seq[PublicData] =
    Seq(alert, audit, `case`, caseTemplate, customField, dashboard, log, observable, observableType, organisation, page, profile, tag, task, user)

  val limitedCountThresholdConfig: ConfigItem[Long, Long] = appConfig.item[Long]("query.limitedCountThreshold", "Maximum number returned by a count")
  override val limitedCountThreshold: Long                = limitedCountThresholdConfig.get

  def metaProperties: PublicProperties =
    PublicPropertyListBuilder
      .metadata
      .property("createdBy", UMapping.string)(_.rename("_createdBy").readonly)
      .property("createdAt", UMapping.date)(_.rename("_createdAt").readonly)
      .property("updatedBy", UMapping.string.optional)(_.rename("_updatedBy").readonly)
      .property("updatedAt", UMapping.date.optional)(_.rename("_updatedAt").readonly)
      .build

  override lazy val publicProperties: PublicProperties = publicDatas.foldLeft(metaProperties)(_ ++ _.publicProperties)

  val childTypes: PartialFunction[(ru.Type, String), ru.Type] = {
    case (tpe, "case_task_log") if SubType(tpe, ru.typeOf[Traversal.V[Task]])             => ru.typeOf[Traversal.V[Log]]
    case (tpe, "case_task") if SubType(tpe, ru.typeOf[Traversal.V[Case]])                 => ru.typeOf[Traversal.V[Task]]
    case (tpe, "case_artifact") if SubType(tpe, ru.typeOf[Traversal.V[Case]])             => ru.typeOf[Traversal.V[Observable]]
    case (tpe, "alert_artifact") if SubType(tpe, ru.typeOf[Traversal.V[Alert]])           => ru.typeOf[Traversal.V[Observable]]
    case (tpe, "caseTemplate_task") if SubType(tpe, ru.typeOf[Traversal.V[CaseTemplate]]) => ru.typeOf[Traversal.V[Task]]
  }
  val parentTypes: PartialFunction[(ru.Type, String), ru.Type] = {
    case (tpe, "caseTemplate") if SubType(tpe, ru.typeOf[Traversal.V[Task]]) => ru.typeOf[Traversal.V[CaseTemplate]]
    case (tpe, _) if SubType(tpe, ru.typeOf[Traversal.V[Task]])              => ru.typeOf[Traversal.V[Case]]
    case (tpe, "alert") if SubType(tpe, ru.typeOf[Traversal.V[Observable]])  => ru.typeOf[Traversal.V[Alert]]
    case (tpe, _) if SubType(tpe, ru.typeOf[Traversal.V[Observable]])        => ru.typeOf[Traversal.V[Case]]
    case (tpe, _) if SubType(tpe, ru.typeOf[Traversal.V[Log]])               => ru.typeOf[Traversal.V[Task]]
  }
  override val customFilterQuery: FilterQuery = FilterQuery(publicProperties) { (tpe, globalParser) =>
    FieldsParser("parentChildFilter") {
      case (_, FObjOne("_parent", ParentIdFilter(parentType, parentId))) if parentTypes.isDefinedAt((tpe, parentType)) =>
        Good(new ParentIdInputFilter(parentId))
      case (path, FObjOne("_parent", ParentQueryFilter(parentType, parentFilterField))) if parentTypes.isDefinedAt((tpe, parentType)) =>
        globalParser(parentTypes((tpe, parentType))).apply(path, parentFilterField).map(query => new ParentQueryInputFilter(parentType, query))
      case (path, FObjOne("_child", ChildQueryFilter(childType, childQueryField))) if childTypes.isDefinedAt((tpe, childType)) =>
        globalParser(childTypes((tpe, childType))).apply(path, childQueryField).map(query => new ChildQueryInputFilter(childType, query))
    }
  }

  override lazy val queries: Seq[ParamQuery[_]] =
    publicDatas.map(_.initialQuery) ++
      publicDatas.map(_.getQuery) ++
      publicDatas.map(_.pageQuery(limitedCountThreshold)) ++ // FIXME the value of limitedCountThreshold is read only once. The value is not updated.
      publicDatas.map(_.outputQuery) ++
      publicDatas.flatMap(_.extraQueries) :+
      new Query {
        override val name: String = "audits"
        override def checkFrom(t: ru.Type): Boolean =
          RichType.getTypeArgs(t, ru.typeOf[Traversal[_, _, _]]).drop(1).headOption.exists(_ =:= ru.typeOf[Vertex])
        override def toType(t: ru.Type): ru.Type                                                     = ru.typeOf[Traversal.V[Audit]]
        override def apply(param: Unit, fromType: ru.Type, from: Any, authContext: AuthContext): Any = from.asInstanceOf[Traversal.V[Any]].audits
      } :+
      new Query {
        override val name: String = "auditsFromContext"
        override def checkFrom(t: ru.Type): Boolean =
          RichType.getTypeArgs(t, ru.typeOf[Traversal[_, _, _]]).drop(1).headOption.exists(_ =:= ru.typeOf[Vertex])
        override def toType(t: ru.Type): ru.Type = ru.typeOf[Traversal.V[Audit]]
        override def apply(param: Unit, fromType: ru.Type, from: Any, authContext: AuthContext): Any =
          from.asInstanceOf[Traversal.V[Any]].auditsFromContext
      }
  override val version: (Int, Int) = 0 -> 0
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

class ParentIdInputFilter(parentId: String) extends InputFilter with TheHiveOpsNoDeps {
  override def apply(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Unk =
    RichType
      .getTypeArgs(traversalType, ru.typeOf[Traversal[_, _, _]])
      .headOption
      .collect {
        case t if t <:< ru.typeOf[Task] && isNegate =>
          traversal
            .asInstanceOf[Traversal.V[Task]]
            .has(_.relatedId, P.neq(EntityId(parentId)))
            .asInstanceOf[Traversal.Unk]
        case t if t <:< ru.typeOf[Task] =>
          traversal
            .asInstanceOf[Traversal.V[Task]]
            .has(_.relatedId, EntityId(parentId))
            .asInstanceOf[Traversal.Unk]
        case t if t <:< ru.typeOf[Observable] && isNegate =>
          traversal
            .asInstanceOf[Traversal.V[Observable]]
            .has(_.relatedId, EntityId(parentId))
            .asInstanceOf[Traversal.Unk]
        case t if t <:< ru.typeOf[Observable] =>
          traversal
            .asInstanceOf[Traversal.V[Observable]]
            .has(_.relatedId, P.neq(EntityId(parentId)))
            .asInstanceOf[Traversal.Unk]
        case t if t <:< ru.typeOf[Log] && isNegate =>
          traversal
            .asInstanceOf[Traversal.V[Log]]
            .has(_.taskId, P.neq(EntityId(parentId)))
            .asInstanceOf[Traversal.Unk]
        case t if t <:< ru.typeOf[Log] =>
          traversal
            .asInstanceOf[Traversal.V[Log]]
            .has(_.taskId, EntityId(parentId))
            .asInstanceOf[Traversal.Unk]
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

class ParentQueryInputFilter(parentType: String, parentFilter: InputFilter) extends InputFilter with TheHiveOpsNoDeps {
  override def apply(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Unk = {
    def filter[F, T: ru.TypeTag](t: Traversal.V[F] => Traversal.V[T]): Traversal.Unk =
      if (isNegate)
        traversal.filterNot(parent =>
          parentFilter(
            publicProperties,
            ru.typeOf[Traversal.V[T]],
            t(parent.asInstanceOf[Traversal.V[F]]).asInstanceOf[Traversal.Unk],
            authContext
          )
        )
      else
        traversal.filter(parent =>
          parentFilter(
            publicProperties,
            ru.typeOf[Traversal.V[T]],
            t(parent.asInstanceOf[Traversal.V[F]]).asInstanceOf[Traversal.Unk],
            authContext
          )
        )

    RichType
      .getTypeArgs(traversalType, ru.typeOf[Traversal[_, _, _]])
      .headOption
      .collect {
        case t if t <:< ru.typeOf[Task] && parentType == "caseTemplate" => filter[Task, CaseTemplate](_.caseTemplate)
        case t if t <:< ru.typeOf[Task]                                 => filter[Task, Case](_.`case`)
        case t if t <:< ru.typeOf[Observable] && parentType == "alert"  => filter[Observable, Alert](_.alert)
        case t if t <:< ru.typeOf[Observable]                           => filter[Observable, Case](_.`case`)
        case t if t <:< ru.typeOf[Log]                                  => filter[Log, Task](_.task)
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

class ChildQueryInputFilter(childType: String, childFilter: InputFilter) extends InputFilter with TheHiveOpsNoDeps {
  override def apply(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Unk = {
    def filter[F, T: ru.TypeTag](t: Traversal.V[F] => Traversal.V[T]): Traversal.Unk =
      if (isNegate)
        traversal.filterNot(child =>
          childFilter(
            publicProperties,
            ru.typeOf[Traversal.V[T]],
            t(child.asInstanceOf[Traversal.V[F]]).asInstanceOf[Traversal.Unk],
            authContext
          )
        )
      else
        traversal.filter(child =>
          childFilter(
            publicProperties,
            ru.typeOf[Traversal.V[T]],
            t(child.asInstanceOf[Traversal.V[F]]).asInstanceOf[Traversal.Unk],
            authContext
          )
        )

    RichType
      .getTypeArgs(traversalType, ru.typeOf[Traversal[_, _, _]])
      .headOption
      .collect {
        case t if t <:< ru.typeOf[Case] && childType == "case_task"                 => filter[Case, Task](_.tasks(authContext))
        case t if t <:< ru.typeOf[Case] && childType == "case_artifact"             => filter[Case, Observable](_.observables(authContext))
        case t if t <:< ru.typeOf[Task] && childType == "case_task_log"             => filter[Task, Log](_.logs)
        case t if t <:< ru.typeOf[Alert] && childType == "alert_artifact"           => filter[Alert, Observable](_.observables)
        case t if t <:< ru.typeOf[CaseTemplate] && childType == "caseTemplate_task" => filter[CaseTemplate, Task](_.tasks)
      }
      .getOrElse(throw BadRequestError(s"$traversalType hasn't child $childType"))
  }
}
