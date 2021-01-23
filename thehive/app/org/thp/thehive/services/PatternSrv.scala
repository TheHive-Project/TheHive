package org.thp.thehive.services

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.PatternOps._
import org.thp.thehive.services.ProcedureOps._

import java.util.{Map => JMap}
import javax.inject.{Inject, Singleton}
import scala.util.{Success, Try}

@Singleton
class PatternSrv @Inject() (
    auditSrv: AuditSrv,
    caseSrv: CaseSrv,
    organisationSrv: OrganisationSrv
) extends VertexSrv[Pattern] {
  val patternPatternSrv = new EdgeSrv[PatternPattern, Pattern, Pattern]

  def cannotBeParent(child: Pattern with Entity, parent: Pattern with Entity)(implicit graph: Graph): Boolean =
    child._id == parent._id || get(child).parent.getEntity(parent).exists

  def setParent(child: Pattern with Entity, parent: Pattern with Entity)(implicit authContext: AuthContext, graph: Graph): Try[Unit] =
    if (cannotBeParent(child, parent)) Success(())
    else patternPatternSrv.create(PatternPattern(), parent, child).map(_ => ())

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Pattern] =
    Try(startTraversal.getByPatternId(name)).getOrElse(startTraversal.limit(0))

  def getCasePatterns(caseId: String)(implicit graph: Graph): Try[Seq[String]] =
    for {
      caze <- caseSrv.get(EntityIdOrName(caseId)).getOrFail("Case")
      patterns = caseSrv.get(caze).procedure.pattern.richPattern.toSeq
    } yield patterns.map(_.patternId)

  def remove(pattern: Pattern with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      organisation <- organisationSrv.getOrFail(authContext.organisation)
      _            <- auditSrv.pattern.delete(pattern, organisation)
    } yield get(pattern).remove()

}

object PatternOps {
  implicit class PatternOpsDefs(traversal: Traversal.V[Pattern]) {
    def getByPatternId(patternId: String): Traversal.V[Pattern] = traversal.has(_.patternId, patternId)

    def parent: Traversal.V[Pattern] =
      traversal.in[PatternPattern].v[Pattern]

    def procedure: Traversal.V[Procedure] =
      traversal.in[ProcedurePattern].v[Procedure]

    def alreadyImported(patternId: String): Boolean =
      traversal.getByPatternId(patternId).exists

    def richPattern: Traversal[RichPattern, JMap[String, Any], Converter[RichPattern, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.in[PatternPattern].v[Pattern].fold)
        )
        .domainMap {
          case (pattern, parent) =>
            RichPattern(pattern, parent.headOption)
        }

  }
}
