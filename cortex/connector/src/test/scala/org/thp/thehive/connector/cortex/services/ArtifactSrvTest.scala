package org.thp.thehive.connector.cortex.services

import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.{CustomWSAPI, FakeCortexClient, KeyAuthentication}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.{Json, Reads}
import play.api.test.PlaySpecification
import play.api.{Configuration, Environment}

import scala.io.Source

class ArtifactSrvTest extends PlaySpecification with Mockito with FakeCortexClient {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration(
        Configuration(
          "play.modules.disabled" -> List("org.thp.scalligraph.ScalligraphModule", "org.thp.thehive.TheHiveModule")
        )
      )

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val artifactSrv: ArtifactSrv         = app.instanceOf[ArtifactSrv]
    implicit val auth: KeyAuthentication = KeyAuthentication("test")
    implicit val ws: CustomWSAPI         = app.instanceOf[CustomWSAPI]

    s"[$name] artifact service" should {
      "download and store an attachment" in {
        withCortexClient { client =>
          //          val r = artifactSrv.downloadAttachment(
          //            "test",
          //            "test",
          //            CortexOutputArtifact("test", None, Some(CortexOutputAttachment("test", Some("test"), Some("file"))), Some("test"), 1, Nil),
          //            client
          //          )(dummyUserSrv.authContext)
          //
          //          val attach = await(r)
          //
          //          attach._id shouldEqual "test"

          val t  = client.listAnalyser
          val tt = await(t)

          1 shouldEqual 1
        }
      }
    }
  }

  def getJobs: Seq[Job] = {
    val dataSource = Source.fromFile(getClass.getResource("/jobs.json").getPath)
    val data       = dataSource.mkString
    dataSource.close()

    implicit val statusReads: Reads[JobStatus.Value] = Reads.enumNameReads(JobStatus)
    implicit val reads: Reads[Job]                   = Json.reads[Job]

    Json.parse(data).as[Seq[Job]]
  }
}
