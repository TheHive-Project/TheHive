package org.thp.thehive.connector.cortex.services

import gremlin.scala.Key
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.{CustomWSAPI, FakeCortexClient, KeyAuthentication}
import org.thp.cortex.dto.v0.{CortexOutputArtifact, CortexOutputAttachment}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.models.{DatabaseBuilder, Observable, Permissions, TheHiveSchema}
import org.thp.thehive.services.{DataSrv, LocalUserSrv, ObservableSrv}
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
      .bindActor[CortexActor]("cortex-actor")
      .addConfiguration(
        Configuration(
          "play.modules.disabled"      -> List("org.thp.scalligraph.ScalligraphModule", "org.thp.thehive.TheHiveModule"),
          "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on"
        )
      )

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app)) ^ step(shutdownActorSystem(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def shutdownActorSystem(app: AppBuilder): Unit = app.app.actorSystem.terminate()

  def specs(name: String, app: AppBuilder): Fragment = {
    val artifactSrv: ArtifactSrv         = app.instanceOf[ArtifactSrv]
    val jobSrv: JobSrv                   = app.instanceOf[JobSrv]
    val dataSrv: DataSrv                 = app.instanceOf[DataSrv]
    val observableSrv: ObservableSrv     = app.instanceOf[ObservableSrv]
    val db: Database                     = app.instanceOf[Database]
    implicit val auth: KeyAuthentication = KeyAuthentication("test")
    implicit val ws: CustomWSAPI         = app.instanceOf[CustomWSAPI]

    s"[$name] artifact service" should {
      "download and store an attachment" in {
        withCortexClient { client =>
          val r = artifactSrv.downloadAttachment(
            "test",
            "test",
            CortexOutputArtifact("file", None, Some(CortexOutputAttachment("file", Some("file.test.txt"), Some("txt"))), Some("test"), 1, Nil),
            client
          )(dummyUserSrv.authContext)

          val attach = await(r)

          attach.name shouldEqual "file.test.txt"
          attach.contentType shouldEqual "txt"
        }
      }

      "handle artifacts correctly" in {
        withCortexClient { client =>
          "process an artifact coming from Cortex report" in {
            db.transaction { graph =>
              val job = jobSrv.create(getJobs.head)(graph, dummyUserSrv.authContext)
              val obs = observableSrv.create(getObservables.head)(graph, dummyUserSrv.authContext)

              val r = artifactSrv.process(
                CortexOutputArtifact("ip", Some("test"), None, None, 1, Nil),
                job,
                obs,
                client
              )(dummyUserSrv.authContext, graph)
              val data = dataSrv.initSteps(graph).filter(_.has(Key("data") of "test")).getOrFail()

              data must beSuccessfulTry
              r.right.get.get must beEqualTo(data.get)
            }
          }
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

  def getObservables: Seq[Observable] = {
    val dataSource = Source.fromFile(getClass.getResource("/observables.json").getPath)
    val data       = dataSource.mkString
    dataSource.close()

    implicit val reads: Reads[Observable] = Json.reads[Observable]

    Json.parse(data).as[Seq[Observable]]
  }
}
