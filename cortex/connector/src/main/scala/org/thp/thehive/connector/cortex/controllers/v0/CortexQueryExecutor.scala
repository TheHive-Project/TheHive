package org.thp.thehive.connector.cortex.controllers.v0

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import org.scalactic.Good
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FieldsParser
import org.thp.scalligraph.models._
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.connector.cortex.models.Job
import org.thp.thehive.connector.cortex.services.JobOps._
import org.thp.thehive.controllers.v0._
import org.thp.thehive.models.Observable

import scala.reflect.runtime.{universe => ru}

@Singleton
class CortexQueryExecutor @Inject() (
    @Named("with-thehive-cortex-schema") implicit override val db: Database,
    job: PublicJob,
    report: PublicAnalyzerTemplate,
    action: PublicAction,
    analyzerTemplate: PublicAnalyzerTemplate
) extends QueryExecutor {
  lazy val controllers: List[PublicData] = action :: report :: job :: analyzerTemplate :: Nil

  override lazy val publicProperties: PublicProperties = controllers.map(_.publicProperties).reduce(_ ++ _)

  override lazy val queries: Seq[ParamQuery[_]] =
    controllers.map(_.initialQuery) :::
      controllers.map(_.getQuery) :::
      controllers.map(_.pageQuery) :::
      controllers.map(_.outputQuery) :::
      controllers.flatMap(_.extraQueries)

  val childTypes: PartialFunction[(ru.Type, String), ru.Type] = {
    case (tpe, "case_artifact_job") if SubType(tpe, ru.typeOf[Traversal.V[Observable]]) => ru.typeOf[Traversal.V[Observable]]
  }
  val parentTypes: PartialFunction[ru.Type, ru.Type] = {
    case tpe if SubType(tpe, ru.typeOf[Traversal.V[Job]]) => ru.typeOf[Traversal.V[Observable]]
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

  override val version: (Int, Int) = 0 -> 1
}

class CortexParentIdInputFilter(parentId: String) extends InputQuery[Traversal.Unk, Traversal.Unk] {
  override def apply(
      db: Database,
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Unk =
    if (traversalType =:= ru.typeOf[Traversal.V[Job]])
      traversal.asInstanceOf[Traversal.V[Job]].filter(_.observable.getByIds(parentId)).asInstanceOf[Traversal.Unk]
    else throw BadRequestError(s"$traversalType hasn't parent")
}

/**
  * The parent query parser traversing properly to appropriate parent
  *
  * @param parentFilter the query
  */
class CortexParentQueryInputFilter(parentFilter: InputQuery[Traversal.Unk, Traversal.Unk]) extends InputQuery[Traversal.Unk, Traversal.Unk] {
  override def apply(
      db: Database,
      publicProperties: PublicProperties,
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
    if (traversalType =:= ru.typeOf[Traversal.V[Job]]) filter[Job, Observable](_.observable)
    else throw BadRequestError(s"$traversalType hasn't parent")
  }
}

class CortexChildQueryInputFilter(childType: String, childFilter: InputQuery[Traversal.Unk, Traversal.Unk])
    extends InputQuery[Traversal.Unk, Traversal.Unk] {
  override def apply(
      db: Database,
      publicProperties: PublicProperties,
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
    if (traversalType =:= ru.typeOf[Traversal.V[Observable]] && childType == "case_artifact_job") filter[Observable, Job](_.jobs)
    else throw BadRequestError(s"$traversalType hasn't child of type $childType")
  }
}
