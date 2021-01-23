package org.thp.thehive.services

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{Converter, Graph, StepLabel, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.ProcedureOps._

import java.util.{Map => JMap}
import javax.inject.{Inject, Singleton}
import scala.util.Try

@Singleton
class ProcedureSrv @Inject() (
    auditSrv: AuditSrv,
    caseSrv: CaseSrv,
    patternSrv: PatternSrv
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

  def remove(procedure: Procedure with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      caze <- get(procedure).caze.getOrFail("Case")
      _    <- auditSrv.procedure.delete(procedure, caze)
    } yield get(procedure).remove()

}

object ProcedureOps {
  implicit class ProcedureOpsDefs(traversal: Traversal.V[Procedure]) {

    def pattern: Traversal.V[Pattern] =
      traversal.out[ProcedurePattern].v[Pattern]

    def caze: Traversal.V[Case] =
      traversal.in[CaseProcedure].v[Case]

    def richProcedure: Traversal[RichProcedure, JMap[String, Any], Converter[RichProcedure, JMap[String, Any]]] = {
      val procedure = StepLabel.v[Procedure]
      val pattern   = StepLabel.v[Pattern]
      traversal
        .as(procedure)
        .in[ProcedurePattern]
        .v[Pattern]
        .as(pattern)
        .select((procedure, pattern))
        .domainMap { case (procedure, pattern) => RichProcedure(procedure, pattern) }
    }
  }
}
