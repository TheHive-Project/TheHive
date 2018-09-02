package org.thp.thehive.services
import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.FPath
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.UpdateOps
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.thehive.models.{Audit, AuditableAction, Audited}

@Singleton
class AuditSrv @Inject()(implicit db: HookableDatabase) {
  def isAudit(model: Model): Boolean = model == vertexSrv.model || model == edgeSrv.model

  db.registerCreateVertexHook(createVertex ⇒
    (graph: Graph, authContext: AuthContext, model: Model.Vertex[Product], v: Product) ⇒ {
      if (isAudit(model)) createVertex(graph, authContext, model, v)
      else {
        val vEntity = createVertex(graph, authContext, model, v)
        create(Audit(AuditableAction.Creation, authContext.requestId, None, None, None), vEntity)(graph, authContext)
        vEntity
      }
  })
  db.registerUpdateHook(update ⇒
    (graph: Graph, authContext: AuthContext, model: Model, id: String, fields: Map[FPath, UpdateOps.Type]) ⇒ {
      if (isAudit(model)) update(graph, authContext, model, id, fields)
      else {
        val element = model.get(id)(db, graph)
        val updateFields = fields.map {
          case (FPath(field), UpdateOps.SetAttribute(value)) ⇒
            val mapping  = model.fields(field)
            val oldValue = db.getProperty(element, field, mapping)
            db.setProperty(element, field, value, mapping.asInstanceOf[Mapping[Any, _, _]])
            (field, oldValue.toString, value.toString)
        }.toSeq

        update(graph, authContext, model, id, fields)

        updateFields.foreach {
          case (field, oldValue, newValue) ⇒
            create(
              Audit(AuditableAction.Update, authContext.requestId, Some(field), Some(oldValue), Some(newValue)),
              model.toDomain(element.asInstanceOf[model.ElementType]))(graph, authContext)
        }
      }
  })

  val vertexSrv: VertexSrv[Audit]               = new VertexSrv[Audit]
  val edgeSrv: EdgeSrv[Audited, Audit, Product] = new EdgeSrv[Audited, Audit, Product]

  def create(audit: Audit, entity: Entity)(implicit graph: Graph, authContext: AuthContext): Unit = {
    val createdAudit = vertexSrv.create(audit)
    edgeSrv.create(Audited(), createdAudit, entity)
    ()
  }
}
