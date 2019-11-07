package org.thp.thehive.services

import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models.{OrganisationPage, Page}

@Singleton
class PageSrv @Inject()(implicit db: Database) extends VertexSrv[Page, PageSteps] {

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): PageSteps = new PageSteps(raw)

//  override def get(idOrTitle: String)(implicit graph: Graph): PageSteps =
//    if (db.isValidId(idOrTitle)) getByIds(idOrTitle)
//    else initSteps.getByTitle(idOrTitle)

}

@EntitySteps[Page]
class PageSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Page](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): PageSteps = new PageSteps(newRaw)
  override def newInstance(): PageSteps                             = new PageSteps(raw.clone())

  def getByTitle(title: String): PageSteps = this.has("title", title)

  def visible(implicit authContext: AuthContext): PageSteps = this.filter(
    _.inTo[OrganisationPage]
      .has("name", authContext.organisation)
  )
}
