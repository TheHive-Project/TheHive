package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.scalligraph.utils.FunctionalCondition._
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.PatternOps._
import org.thp.thehive.services.ProcedureOps._

import java.util.{Map => JMap}
import javax.inject.{Inject, Named, Singleton}
import scala.util.{Success, Try}

@Singleton
class PatternSrv @Inject() (
    auditSrv: AuditSrv,
    caseSrv: CaseSrv,
    organisationSrv: OrganisationSrv
)(implicit
    @Named("with-thehive-schema") db: Database
) extends VertexSrv[Pattern] {
  val patternPatternSrv = new EdgeSrv[PatternPattern, Pattern, Pattern]

  def cannotBeParent(child: Pattern with Entity, parent: Pattern with Entity)(implicit graph: Graph): Boolean =
    child._id == parent._id || get(child).parent.getEntity(parent).exists

  def setParent(child: Pattern with Entity, parent: Pattern with Entity)(implicit authContext: AuthContext, graph: Graph): Try[Unit] =
    if (cannotBeParent(child, parent)) Success(())
    else patternPatternSrv.create(PatternPattern(), parent, child).map(_ => ())

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Pattern] =
    Try(startTraversal.getByPatternId(name)).getOrElse(startTraversal.limit(0))

  def getCasePatterns(caseId: String)(implicit graph: Graph): Try[Seq[RichPattern]] =
    for {
      caze <- caseSrv.get(EntityIdOrName(caseId)).getOrFail("Case")
    } yield caseSrv.get(caze).procedure.pattern.richPattern.toSeq

  def update(
      pattern: Pattern with Entity,
      input: Pattern
  )(implicit graph: Graph): Try[Pattern with Entity] =
    for {
      updatedPattern <- get(pattern)
        .when(pattern.patternId != input.patternId)(_.update(_.patternId, input.patternId))
        .when(pattern.name != input.name)(_.update(_.name, input.name))
        .when(pattern.description != input.description)(_.update(_.description, input.description))
        .when(pattern.tactics != input.tactics)(_.update(_.tactics, input.tactics))
        .when(pattern.url != input.url)(_.update(_.url, input.url))
        .when(pattern.patternType != input.patternType)(_.update(_.patternType, input.patternType))
        .when(pattern.capecId != input.capecId)(_.update(_.capecId, input.capecId))
        .when(pattern.capecUrl != input.capecUrl)(_.update(_.capecUrl, input.capecUrl))
        .when(pattern.revoked != input.revoked)(_.update(_.revoked, input.revoked))
        .when(pattern.dataSources != input.dataSources)(_.update(_.dataSources, input.dataSources))
        .when(pattern.defenseBypassed != input.defenseBypassed)(_.update(_.defenseBypassed, input.defenseBypassed))
        .when(pattern.detection != input.detection)(_.update(_.detection, input.detection))
        .when(pattern.permissionsRequired != input.permissionsRequired)(_.update(_.permissionsRequired, input.permissionsRequired))
        .when(pattern.platforms != input.platforms)(_.update(_.platforms, input.platforms))
        .when(pattern.remoteSupport != input.remoteSupport)(_.update(_.remoteSupport, input.remoteSupport))
        .when(pattern.systemRequirements != input.systemRequirements)(_.update(_.systemRequirements, input.systemRequirements))
        .when(pattern.revision != input.revision)(_.update(_.revision, input.revision))
        .getOrFail("Pattern")
    } yield updatedPattern

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

    def children: Traversal.V[Pattern] =
      traversal.out[PatternPattern].v[Pattern]

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

    def richPatternWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Pattern] => Traversal[D, G, C]
    ): Traversal[(RichPattern, D), JMap[String, Any], Converter[(RichPattern, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.in[PatternPattern].v[Pattern].fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (pattern, parent, renderedEntity) =>
            RichPattern(pattern, parent.headOption) -> renderedEntity
        }

  }
}
