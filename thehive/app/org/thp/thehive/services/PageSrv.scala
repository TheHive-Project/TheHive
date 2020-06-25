package org.thp.thehive.services

import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models.{Organisation, OrganisationPage, Page}
import play.api.libs.json.Json

import scala.util.Try

@Singleton
class PageSrv @Inject() (implicit @Named("with-thehive-schema") db: Database, organisationSrv: OrganisationSrv, auditSrv: AuditSrv)
    extends VertexSrv[Page, PageSteps] {

  val organisationPageSrv = new EdgeSrv[OrganisationPage, Organisation, Page]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): PageSteps = new PageSteps(raw)

  override def get(idOrSlug: String)(implicit graph: Graph): PageSteps =
    if (db.isValidId(idOrSlug)) getByIds(idOrSlug)
    else initSteps.getBySlug(idOrSlug)

  def create(page: Page)(implicit authContext: AuthContext, graph: Graph): Try[Page with Entity] =
    for {
      created      <- createEntity(page)
      organisation <- organisationSrv.get(authContext.organisation).getOrFail()
      _            <- organisationPageSrv.create(OrganisationPage(), organisation, created)
      _            <- auditSrv.page.create(created, Json.obj("title" -> page.title))
    } yield created

  def update(page: Page with Entity, propertyUpdaters: Seq[PropertyUpdater])(implicit graph: Graph, authContext: AuthContext): Try[Page with Entity] =
    for {
      updated <- update(get(page), propertyUpdaters)
      p       <- updated._1.getOrFail()
      _       <- auditSrv.page.update(p, Json.obj("title" -> p.title))
    } yield p

  def delete(page: Page with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(page).remove()
    auditSrv.page.delete(page)
  }
}

@EntitySteps[Page]
class PageSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph) extends VertexSteps[Page](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): PageSteps = new PageSteps(newRaw)
  override def newInstance(): PageSteps                             = new PageSteps(raw.clone())

  def getByTitle(title: String): PageSteps = this.has("title", title)
  def getBySlug(slug: String): PageSteps   = this.has("slug", slug)

  def visible(implicit authContext: AuthContext): PageSteps = this.filter(
    _.inTo[OrganisationPage]
      .has("name", authContext.organisation)
  )
}
