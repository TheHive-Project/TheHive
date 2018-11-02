package org.thp.thehive.services

import java.util.{List ⇒ JList}

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, InternalError}
import org.thp.thehive.models._

@Singleton
class UserSrv @Inject()(implicit val db: Database) extends VertexSrv[User, UserSteps] {

  val userOrganisationSrv = new EdgeSrv[UserOrganisation, User, Organisation]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): UserSteps = new UserSteps(raw)

  override def get(id: String)(implicit graph: Graph): UserSteps = initSteps.get(id)

  def create(user: User, organisation: Organisation with Entity)(implicit graph: Graph, authContext: AuthContext): RichUser = {
    val createdUser = create(user)
    userOrganisationSrv.create(UserOrganisation(), createdUser, organisation)
    RichUser(createdUser, organisation.name)
  }

  def getOrganisation(user: User with Entity)(implicit graph: Graph): Organisation with Entity =
    get(user).organisation.headOption.getOrElse(throw InternalError(s"The user $user (${user._id}) has no organisation."))
}

@EntitySteps[User]
class UserSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[User, UserSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): UserSteps = new UserSteps(raw)

  def get(id: String): UserSteps = new UserSteps(raw.coalesce(_.has(Key("login") of id), _.has(Key("_id") of id)))

  def organisation: OrganisationSteps = new OrganisationSteps(raw.outTo[UserOrganisation])

  def richUser: GremlinScala[RichUser] =
    raw
      .project[Any]("user", "organisation")
      .by()
      .by(__[Vertex].outTo[UserOrganisation].values[String]("name").fold.traversal)
      .map {
        case ValueMap(m) ⇒
          RichUser(
            m.get[Vertex]("user").as[User],
            onlyOneOf[String](m.get[JList[String]]("organisation")),
          )
      }
}
