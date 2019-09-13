package org.thp.thehive.services

import scala.util.Try

import gremlin.scala.{Graph, GremlinScala, Key, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.Tag

@Singleton
class TagSrv @Inject()(appConfig: ApplicationConfig)(implicit db: Database) extends VertexSrv[Tag, TagSteps] {

  val autoCreateConfig: ConfigItem[Boolean, Boolean] =
    appConfig.item[Boolean]("tags.autocreate", "If true, create automatically tag if it doesn't exist")
  def autoCreate: Boolean                                                        = autoCreateConfig.get
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): TagSteps = new TagSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): TagSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  def getOrCreate(tagName: String)(implicit graph: Graph, authContext: AuthContext): Try[Tag with Entity] =
    initSteps
      .getByName(tagName)
      .getOrFail()
      .recoverWith {
        case _ if autoCreate => createEntity(Tag(tagName))
      }
}

class TagSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Tag, TagSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): TagSteps = new TagSteps(raw)

  def get(idOrName: String): TagSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): TagSteps = newInstance(raw.has(Key("name") of name))
}
