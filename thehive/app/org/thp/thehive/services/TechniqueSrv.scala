package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.models._
import org.thp.thehive.services.TechniqueOps._

import java.util.{Map => JMap}
import javax.inject.{Inject, Named, Singleton}
import scala.util.{Success, Try}

@Singleton
class TechniqueSrv @Inject() ()(implicit
    @Named("with-thehive-schema") db: Database
) extends VertexSrv[Technique] {
  val techniqueTechniqueSrv = new EdgeSrv[TechniqueTechnique, Technique, Technique]

  def parentExists(child: Technique with Entity, parent: Technique with Entity)(implicit graph: Graph): Boolean =
    child._id == parent._id || get(child).parent.getEntity(parent).exists

  def setParent(child: Technique with Entity, parent: Technique with Entity)(implicit authContext: AuthContext, graph: Graph): Try[Unit] =
    if (parentExists(child, parent)) Success(())
    else techniqueTechniqueSrv.create(TechniqueTechnique(), child, parent).map(_ => ())

}

object TechniqueOps {
  implicit class TechniqueOpsDefs(traversal: Traversal.V[Technique]) {

    def get(idOrName: EntityIdOrName): Traversal.V[Technique] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.limit(0))

    def getByTechniqueId(techniqueId: String): Traversal.V[Technique] = traversal.has(_.techniqueId, techniqueId)

    def parent: Traversal.V[Technique] =
      traversal.in[TechniqueTechnique].v[Technique]

    def alreadyImported(techniqueId: String): Boolean =
      traversal.getByTechniqueId(techniqueId).exists

    def richTechnique: Traversal[RichTechnique, JMap[String, Any], Converter[RichTechnique, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.in[TechniqueTechnique].v[Technique].fold)
        )
        .domainMap {
          case (technique, parent) =>
            RichTechnique(technique, parent.headOption)
        }

  }
}
