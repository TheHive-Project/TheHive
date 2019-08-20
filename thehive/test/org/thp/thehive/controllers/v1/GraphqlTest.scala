//package org.thp.thehive.controllers.v1
//
//import scala.util.Success
//import play.api.mvc.RequestHeader
//import play.api.test.PlaySpecification
//import org.specs2.mock.Mockito
//import org.specs2.specification.core.{Fragment, Fragments}
//import org.thp.scalligraph.auth.AuthSrv
//import org.thp.scalligraph.controllers.AuthenticateSrv
//import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
//import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
//import org.thp.scalligraph.{graphql, AppBuilder}
//import org.thp.thehive.models._
//import sangria.renderer.SchemaRenderer
//
//class GraphqlTest extends PlaySpecification with Mockito {
//  val dummyUserSrv                 = DummyUserSrv()
//  val authenticated: AuthenticateSrv = mock[AuthenticateSrv]
//  authenticated.getContext(any[RequestHeader]) returns Success(dummyUserSrv.authContext)
//
//  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
//    val app: AppBuilder = AppBuilder()
//      .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
//      .bindInstance[InitialAuthContext](InitialAuthContext(dummyUserSrv.getSystemAuthContext))
//      .bindToProvider(dbProvider)
//      .bindInstance[AuthSrv](mock[AuthSrv])
//      .bindInstance[AuthenticateSrv](authenticated)
//      .bind[StorageSrv, LocalFileSystemStorageSrv]
//      .bind[Schema, TheHiveSchema]
//      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
//    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
//  }
//
//  def setupDatabase(app: AppBuilder): Try[Unit] =
//    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)
//
//  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()
//
//  def specs(name: String, app: AppBuilder): Fragment = {
//    val queryExecutor: TheHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]
//
//    s"[$name] TheHive" should {
//      "have graphql schema" in {
//
//        val schema    = graphql.SchemaGenerator(queryExecutor)
//        val schemaStr = SchemaRenderer.renderSchema(schema)
//        //println(s"new modern graphql schema is:\n$schemaStr")
//
//        schemaStr must_!== ""
//      }
//    }
//  }
//}
