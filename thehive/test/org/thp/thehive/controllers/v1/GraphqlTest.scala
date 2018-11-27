package org.thp.thehive.controllers.v1

import scala.concurrent.Future

import play.api.mvc.RequestHeader
import play.api.test.PlaySpecification

import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.controllers.Authenticated
import org.thp.scalligraph.{graphql, AppBuilder}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.models._
import sangria.renderer.SchemaRenderer

class GraphqlTest extends PlaySpecification with Mockito {
  val dummyUserSrv                 = DummyUserSrv(permissions = Seq(Permissions.read))
  val authenticated: Authenticated = mock[Authenticated]
  authenticated.getContext(any[RequestHeader]) returns Future.successful(dummyUserSrv.authContext)

  Fragments.foreach(new DatabaseProviders().list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
      .bindInstance[InitialAuthContext](InitialAuthContext(dummyUserSrv.initialAuthContext))
      .bindToProvider(dbProvider)
      .bindInstance[AuthSrv](mock[AuthSrv])
      .bindInstance[Authenticated](authenticated)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    DatabaseBuilder.build(app.instanceOf[TheHiveSchema])(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val queryExecutor: TheHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]

    s"[$name] TheHive" should {
      "have graphql schema" in {

        val schema    = graphql.SchemaGenerator(queryExecutor)
        val schemaStr = SchemaRenderer.renderSchema(schema)
        //println(s"new modern graphql schema is:\n$schemaStr")

        schemaStr must_!== ""
      }
    }
  }
}
