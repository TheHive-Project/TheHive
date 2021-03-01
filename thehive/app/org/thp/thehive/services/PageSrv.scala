package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.thehive.models.{Organisation, OrganisationPage, Page}
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.PageOps._
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}
import scala.util.Try

@Singleton
class PageSrv @Inject() (organisationSrv: OrganisationSrv, auditSrv: AuditSrv) extends VertexSrv[Page] {

  val organisationPageSrv = new EdgeSrv[OrganisationPage, Organisation, Page]

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Page] = startTraversal.getBySlug(name)

  def create(page: Page)(implicit authContext: AuthContext, graph: Graph): Try[Page with Entity] =
    for {
      created      <- createEntity(page)
      organisation <- organisationSrv.get(authContext.organisation).getOrFail("Organisation")
      _            <- organisationPageSrv.create(OrganisationPage(), organisation, created)
      _            <- auditSrv.page.create(created, Json.obj("title" -> page.title))
    } yield created

  def update(page: Page with Entity, propertyUpdaters: Seq[PropertyUpdater])(implicit graph: Graph, authContext: AuthContext): Try[Page with Entity] =
    for {
      updated <- update(get(page), propertyUpdaters)
      p       <- updated._1.getOrFail("Page")
      _       <- auditSrv.page.update(p, Json.obj("title" -> p.title))
    } yield p

  def delete(page: Page with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    organisationSrv.getOrFail(authContext.organisation).flatMap { organisation =>
      get(page).remove()
      auditSrv.page.delete(page, organisation)
    }
}

object PageOps {

  implicit class PageOpsDefs(traversal: Traversal.V[Page]) {

    def getByTitle(title: String): Traversal.V[Page] = traversal.has(_.title, title)

    def getBySlug(slug: String): Traversal.V[Page] = traversal.has(_.slug, slug)

    def organisation: Traversal.V[Organisation] = traversal.in[OrganisationPage].v[Organisation]

    def visible(implicit authContext: AuthContext): Traversal.V[Page] =
      traversal.filter(_.organisation.current)
  }

}
