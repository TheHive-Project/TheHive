package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.ProcedureOps._
import play.api.libs.json.JsObject

import java.util.{Map => JMap}
import javax.inject.{Inject, Named, Singleton}
import scala.util.Try

@Singleton
class ProcedureSrv @Inject() (
    auditSrv: AuditSrv,
    caseSrv: CaseSrv,
    patternSrv: PatternSrv
)(implicit
    @Named("with-thehive-schema") db: Database
) extends VertexSrv[Procedure] {
  val caseProcedureSrv    = new EdgeSrv[CaseProcedure, Case, Procedure]
  val procedurePatternSrv = new EdgeSrv[ProcedurePattern, Procedure, Pattern]

  def create(p: Procedure, caseId: String, patternId: String)(implicit graph: Graph, authContext: AuthContext): Try[RichProcedure] =
    for {
      caze      <- caseSrv.getOrFail(EntityIdOrName(caseId))
      pattern   <- patternSrv.getOrFail(EntityIdOrName(patternId))
      procedure <- createEntity(p)
      _         <- caseProcedureSrv.create(CaseProcedure(), caze, procedure)
      _         <- procedurePatternSrv.create(ProcedurePattern(), procedure, pattern)
      richProcedure = RichProcedure(procedure, pattern)
      _ <- auditSrv.procedure.create(procedure, caze, richProcedure.toJson)
    } yield richProcedure

  override def get(idOrName: EntityIdOrName)(implicit graph: Graph): Traversal.V[Procedure] =
    idOrName.fold(getByIds(_), _ => startTraversal.limit(0))

  override def update(
      traversal: Traversal.V[Procedure],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Procedure], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (procedureSteps, updatedFields) =>
        procedureSteps.clone().project(_.by.by(_.caze)).getOrFail("Procedure").flatMap {
          case (procedure, caze) => auditSrv.procedure.update(procedure, caze, updatedFields)
        }
    }

  def remove(procedure: Procedure with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      caze <- get(procedure).caze.getOrFail("Case")
      _    <- auditSrv.procedure.delete(procedure, Some(caze))
    } yield get(procedure).remove()

}

object ProcedureOps {
  implicit class ProcedureOpsDefs(traversal: Traversal.V[Procedure]) {

    def pattern: Traversal.V[Pattern] =
      traversal.out[ProcedurePattern].v[Pattern]

    def caze: Traversal.V[Case] =
      traversal.in[CaseProcedure].v[Case]

    def get(idOrName: EntityIdOrName): Traversal.V[Procedure] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.limit(0))

    def richProcedure: Traversal[RichProcedure, JMap[String, Any], Converter[RichProcedure, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.pattern)
        )
        .domainMap { case (procedure, pattern) => RichProcedure(procedure, pattern) }
  }
}
