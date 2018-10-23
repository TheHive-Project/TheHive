package org.thp.thehive.services
import gremlin.scala.{ By, GremlinScala, Key, Vertex }
import javax.inject.{ Inject, Singleton }
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models.{ BaseVertexSteps, Database }
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.Audit

@Singleton
class AuditSrv @Inject() (implicit db: Database) extends VertexSrv[Audit, AuditSteps] {
  override def steps(raw: GremlinScala[Vertex]): AuditSteps = new AuditSteps(raw)
}

@EntitySteps[Audit]
class AuditSteps(raw: GremlinScala[Vertex])(implicit db: Database) extends BaseVertexSteps[Audit, AuditSteps](raw) {

  override def newInstance(raw: GremlinScala[Vertex]): AuditSteps = new AuditSteps(raw)

//  def plop()= {
//    raw
//      .group(By(Key("requestId")))
//      .order(By("_createdAt"))
//  }
}

