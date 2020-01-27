package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models._

import scala.util.Try

@Singleton
class ObservableTypeSrv @Inject() (auditSrv: AuditSrv)(implicit db: Database) extends VertexSrv[ObservableType, ObservableTypeSteps] {

  override val initialValues: Seq[ObservableType] = Seq(
    ObservableType("url", isAttachment = false),
    ObservableType("other", isAttachment = false),
    ObservableType("user-agent", isAttachment = false),
    ObservableType("regexp", isAttachment = false),
    ObservableType("mail-subject", isAttachment = false),
    ObservableType("registry", isAttachment = false),
    ObservableType("mail", isAttachment = false),
    ObservableType("autonomous-system", isAttachment = false),
    ObservableType("domain", isAttachment = false),
    ObservableType("ip", isAttachment = false),
    ObservableType("uri_path", isAttachment = false),
    ObservableType("filename", isAttachment = false),
    ObservableType("hash", isAttachment = false),
    ObservableType("file", isAttachment = true),
    ObservableType("fqdn", isAttachment = false),
    ObservableType("hostname", isAttachment = false)
  )

  val observableObservableTypeSrv                                                           = new EdgeSrv[ObservableObservableType, Observable, ObservableType]
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ObservableTypeSteps = new ObservableTypeSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ObservableTypeSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  def create(observableType: ObservableType)(implicit graph: Graph, authContext: AuthContext): Try[ObservableType with Entity] =
    createEntity(observableType)
}

@EntitySteps[ObservableType]
class ObservableTypeSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[ObservableType](raw) {

  override def newInstance(newRaw: GremlinScala[Vertex]): ObservableTypeSteps = new ObservableTypeSteps(newRaw)
  override def newInstance(): ObservableTypeSteps                             = new ObservableTypeSteps(raw.clone())

  def get(idOrName: String): ObservableTypeSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ObservableTypeSteps = new ObservableTypeSteps(raw.has(Key("name") of name))
}
