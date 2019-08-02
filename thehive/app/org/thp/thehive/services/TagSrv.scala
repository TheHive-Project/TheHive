package org.thp.thehive.services

import gremlin.scala.{Graph, GremlinScala, Key, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.{ApplicationConfiguration, ConfigItem, VertexSrv}
import org.thp.thehive.models.Tag

import scala.util.Try

@Singleton
class TagSrv @Inject()(appConfig: ApplicationConfiguration)(implicit db: Database) extends VertexSrv[Tag, TagSteps] {
  val autoCreateConfig: ConfigItem[Boolean]                                      = appConfig.item[Boolean]("tags.autocreate", "If true, create automatically tag if it doesn't exist")
  def autoCreate: Boolean                                                        = autoCreateConfig.get
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): TagSteps = new TagSteps(raw)

  override def get(id: String)(implicit graph: Graph): TagSteps =
    if (db.isValidId(id)) super.get(id)
    else initSteps.getByName(id)

  def getOrCreate(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    initSteps
      .getByName(tagName)
      .getOrFail()
      .recoverWith {
        case _ if autoCreate => create(Tag(tagName))
      }
}

class TagSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Tag, TagSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): TagSteps = new TagSteps(raw)

  override def get(id: String): TagSteps =
    if (db.isValidId(id)) getById(id)
    else getByName(id)

  def getById(id: String): TagSteps = newInstance(raw.hasId(id))

  def getByName(name: String): TagSteps = newInstance(raw.has(Key("name") of name))
}
