package org.thp.thehive.controllers.v1

import scala.concurrent.Future

import play.api.mvc.RequestHeader
import play.api.test.PlaySpecification

import org.specs2.mock.Mockito
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.Authenticated
import org.thp.scalligraph.graphql
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{DatabaseProvider, DummyUserSrv}
import org.thp.thehive.models._
import sangria.renderer.SchemaRenderer

class GraphqlTest extends PlaySpecification with Mockito {
  val dummyUserSrv                 = DummyUserSrv(permissions = Seq(Permissions.read))
  val authenticated: Authenticated = mock[Authenticated]
  authenticated.getContext(any[RequestHeader]) returns Future.successful(dummyUserSrv.authContext)

  implicit val db: DatabaseProvider = new DatabaseProvider("janus", new JanusDatabase())
  val app: AppBuilder = AppBuilder()
    .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
    .bindInstance[InitialAuthContext](InitialAuthContext(dummyUserSrv.initialAuthContext))
    .bindInstance[AuthSrv](mock[AuthSrv])
    .bindToProvider(db)
    .bindInstance[Authenticated](authenticated)
  app.instanceOf[DatabaseBuilder]
  val queryExecutor: TheHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]

  "TheHive" should {
    "have graphql schema" in {

      val schema    = graphql.SchemaGenerator(queryExecutor)
      val schemaStr = SchemaRenderer.renderSchema(schema)
      println(s"new modern graphql schema is:\n$schemaStr")

      schemaStr must_!== ""
    }
  }
}
