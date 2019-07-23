package org.thp.thehive.connector.cortex.services

import java.util.Date

import akka.actor.Terminated
import gremlin.scala.{Key, P}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.{Authentication, CustomWSAPI, FakeCortexClient, KeyAuthentication}
import org.thp.cortex.dto.v0.CortexOutputJob
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, UserSrv}
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.{LocalUserSrv, ObservableSrv}
import play.api.libs.json.Json
import play.api.test.PlaySpecification
import play.api.{Configuration, Environment}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.io.Source

class JobSrvTest(implicit executionEnv: ExecutionEnv) extends PlaySpecification with Mockito with FakeCortexClient {
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
          "akka.remote.netty.tcp.port" -> 3334,
          "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on"
        )
      )

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app)) ^ step(shutdownActorSystem(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def shutdownActorSystem(app: AppBuilder): Future[Terminated] = app.app.actorSystem.terminate()

  def specs(name: String, app: AppBuilder): Fragment = {
    val jobSrv: JobSrv               = app.instanceOf[JobSrv]
    val observableSrv: ObservableSrv = app.instanceOf[ObservableSrv]
    val db: Database                 = app.instanceOf[Database]

    s"[$name] job service" should {
      implicit lazy val ws: CustomWSAPI      = app.instanceOf[CustomWSAPI]
      implicit val authContext: AuthContext  = dummyUserSrv.authContext
      implicit lazy val auth: Authentication = KeyAuthentication("test")

      "create a job" in db.transaction { implicit graph =>
        val maybeObservable = observableSrv.initSteps.has(Key("message"), P.eq("This domain")).getOrFail()
        maybeObservable must beSuccessfulTry
        val observable = maybeObservable.get
        val j = Job(
          workerId = "anaTest1",
          workerName = "anaTest1",
          workerDefinition = "test",
          status = JobStatus.Waiting,
          startDate = new Date(1561625908856L),
          endDate = new Date(1561625908856L),
          report = None,
          cortexId = "test",
          cortexJobId = "AWuYKFatq3Rtqym9DFmL"
        )
        val job = jobSrv.create(j, observable)
        jobSrv.get(job).observable.getOrFail().map(_._id) must beSuccessfulTry(observable._id)
      }

      "handle a finished job" in {
        val maybeObservable = db.transaction { implicit graph =>
          observableSrv.initSteps.has(Key("message"), P.eq("hello world")).getOrFail()
        }
        maybeObservable must beSuccessfulTry
        val observable = maybeObservable.get

        val job = Job(
          workerId = "anaTest2",
          workerName = "anaTest2",
          workerDefinition = "test2",
          status = JobStatus.Waiting,
          startDate = new Date(1561625908856L),
          endDate = new Date(1561625908856L),
          report = None,
          cortexId = "test",
          cortexJobId = "LVyYKFstq3Rtrdc9DFmL"
        )
        val createdJob = db.transaction { implicit graph =>
          jobSrv.create(job, observable)
        }

        val cortexOutputJob: CortexOutputJob = {
          val dataSource = Source.fromResource("cortex-jobs.json")
          val data       = dataSource.mkString
          dataSource.close()
          Json.parse(data).as[CortexOutputJob]
        }

        withCortexClient { client =>
          jobSrv
            .finished(createdJob._id, cortexOutputJob, client)
            .map { updatedJob =>
              updatedJob.status shouldEqual JobStatus.Success
              updatedJob.report must beSome
              updatedJob.report.get \ "artifacts" must beEmpty
              (updatedJob.report.get \ "full" \ "data").as[String] shouldEqual "imageedit_2_3904987689.jpg"

              db.transaction { implicit graph =>
                jobSrv.get(updatedJob).observable.toList.map(_._id) must contain(exactly(observable._id))
                jobSrv.get(updatedJob).reportObservables.toList.length must equalTo(2)
              }
            }
            .await(0, 5.seconds)
        }
      }
    }
  }
}
