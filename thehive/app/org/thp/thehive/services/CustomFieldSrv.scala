package org.thp.thehive.services
import scala.util.Try

import play.api.libs.json.JsObject

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.models.CustomField

@Singleton
class CustomFieldSrv @Inject()(implicit db: Database, auditSrv: AuditSrv) extends VertexSrv[CustomField, CustomFieldSteps] {

  def create(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] =
    for {
      created <- createEntity(e)
      _       <- auditSrv.customField.create(created)
    } yield created

  def delete(e: CustomField with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      _ <- Try(get(e).remove())
      _ <- auditSrv.customField.delete(e)
    } yield ()

  override def update(
      steps: CustomFieldSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(CustomFieldSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (customFieldSteps, updatedFields) =>
        customFieldSteps
          .newInstance()
          .getOrFail()
          .flatMap(auditSrv.customField.update(_, updatedFields))
    }

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CustomFieldSteps = new CustomFieldSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): CustomFieldSteps =
    if (db.isValidId(idOrName)) super.getByIds(idOrName)
    else initSteps.getByName(idOrName)
}

@EntitySteps[CustomField]
class CustomFieldSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[CustomField](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): CustomFieldSteps = new CustomFieldSteps(newRaw)
  override def newInstance(): CustomFieldSteps                             = new CustomFieldSteps(raw.clone())

  def get(idOrName: String): CustomFieldSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): CustomFieldSteps = new CustomFieldSteps(raw.has(Key("name") of name))

}
