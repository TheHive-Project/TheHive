package org.thp.thehive.connector.cortex.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services._
import org.thp.thehive.connector.cortex.models.{Job, ObservableJob}
import org.thp.thehive.models.{OrganisationShare, RoleOrganisation, ShareObservable, UserRole}

@Singleton
class JobSrv @Inject()(implicit db: Database) extends VertexSrv[Job, JobSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): JobSteps = new JobSteps(raw)
}

@EntitySteps[Job]
class JobSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Job, JobSteps](raw) {

  def visible(implicit authContext: AuthContext): JobSteps = newInstance(
    raw.filter(
      _.inTo[ObservableJob]
        .inTo[ShareObservable]
        .inTo[OrganisationShare]
        .inTo[RoleOrganisation]
        .inTo[UserRole]
        .has(Key("login") of authContext.userId)
    )
  )

  override def newInstance(raw: GremlinScala[Vertex]): JobSteps = new JobSteps(raw)
}
