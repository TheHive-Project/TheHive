package org.thp.thehive.connector.cortex.services

import java.util.Date

import akka.actor.Terminated
import gremlin.scala.{Key, P}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.{CortexClient, CortexConfig, TestCortexClientProvider, TestCortexConfigProvider}
import org.thp.cortex.dto.v0.CortexOutputJob
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.TestAuthSrv
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.{CaseSrv, LocalUserSrv, ObservableSrv}
import play.api.libs.json.Json
import play.api.test.PlaySpecification
import play.api.{Configuration, Environment}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.util.Try

class JobSrvTest(implicit executionEnv: ExecutionEnv) extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthSrv, TestAuthSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[ConfigActor]("config-actor")
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexConfig, TestCortexConfigProvider]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app)) ^ step(shutdownActorSystem(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def shutdownActorSystem(app: AppBuilder): Future[Terminated] = app.app.actorSystem.terminate()

  def specs(name: String, app: AppBuilder): Fragment = {
    val jobSrv: JobSrv               = app.instanceOf[JobSrv]
    val observableSrv: ObservableSrv = app.instanceOf[ObservableSrv]
    val db: Database                 = app.instanceOf[Database]
    val caseSrv                      = app.instanceOf[CaseSrv]

    s"[$name] job service" should {
      implicit val authContext: AuthContext = dummyUserSrv.authContext
      val cortexConfig: CortexConfig        = app.instanceOf[CortexConfig]

      "handle creation and then finished job" in {
        val maybeObservable = db.roTransaction { implicit graph =>
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

        val cortexOutputJobOpt = {
          val dataSource = Source.fromResource("cortex-jobs.json")
          val data       = dataSource.mkString
          dataSource.close()
          Json.parse(data).as[List[CortexOutputJob]].find(_.id == "ZWu85Q1OCVNx03hXK4df")
        }

        (for {
          cortexOutputJob <- Future(cortexOutputJobOpt.get)
          createdJob <- Future.fromTry(db.tryTransaction { implicit graph =>
            jobSrv.create(job, observable)
          })
          // FIXME mixing db transaction tries and futures does not seem to work when running full test suite
          // org.janusgraph.diskstorage.locking.PermanentLockingException: Local lock contention on importCortexArtifacts
          // i.e. cortex-jobs.json report.artifacts length > 1 succeeds testOnly but fails test with one attachmentType and one dataType
          updatedJob <- jobSrv
            .finished(createdJob._id, cortexOutputJob, cortexConfig.instances.values.head)
        } yield {
          updatedJob.status shouldEqual JobStatus.Success
          updatedJob.report must beSome
          updatedJob.report.get \ "artifacts" must beEmpty
          (updatedJob.report.get \ "full" \ "data").as[String] shouldEqual "imageedit_2_3904987689.jpg"

          db.roTransaction { implicit graph =>
            jobSrv.get(updatedJob).observable.toList.map(_._id) must contain(exactly(observable._id))
            jobSrv.get(updatedJob).reportObservables.toList.length must equalTo(1)
          }
        }).await(0, 5.seconds)
      }

      "submit a job" in {
        val maybeObservable = db.roTransaction { implicit graph =>
          observableSrv.initSteps.has(Key("message"), P.eq("Some weird domain")).getOrFail()
        }

        maybeObservable must beSuccessfulTry

        val observable = maybeObservable.get
        val c = db.roTransaction { implicit graph =>
          caseSrv.initSteps.has(Key("title"), P.eq("case#1")).getOrFail()
        }
        val obs = db.roTransaction { implicit graph =>
          observableSrv.get(observable).richObservable.getOrFail()
        }

        c must beSuccessfulTry.which(case1 => {
          obs must beSuccessfulTry.which(hfr => {
            val r = await(jobSrv.submit("test", "anaTest1", hfr, case1))

            r.cortexId shouldEqual "test"
            r.workerId shouldEqual "anaTest1"
          })
        })
      }
    }
  }
}
