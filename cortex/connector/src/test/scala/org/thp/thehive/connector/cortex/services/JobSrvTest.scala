package org.thp.thehive.connector.cortex.services

import java.util.Date

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.util.Try

import play.api.libs.json.Json
import play.api.test.PlaySpecification

import akka.actor.Terminated
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.cortex.dto.v0.CortexOutputJob
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import org.thp.thehive.services._
import org.thp.thehive.services.notification.triggers.JobFinished

class JobSrvTest extends PlaySpecification with Mockito {
  val dummyUserSrv = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexClient, TestCortexClientProvider]
      .bind[Connector, TestConnector]
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
    val userSrv                      = app.instanceOf[UserSrv]
    val auditSrv                     = app.instanceOf[AuditSrv]
    val orgSrv                       = app.instanceOf[OrganisationSrv]

    s"[$name] job service" should {
      implicit val authContext: AuthContext = dummyUserSrv.authContext
      val client                            = app.instanceOf[CortexClient]

      "handle creation and then finished job" in {
        val maybeObservable = db.roTransaction { implicit graph =>
          observableSrv.initSteps.has("message", "hello world").getOrFail()
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

        val cortexOutputJob = cortexOutputJobOpt.get
        val createdJob = db.tryTransaction { implicit graph =>
          jobSrv.create(job, observable)
        }
        val updatedJob = Await.result(
          jobSrv.finished(client.name, createdJob.get._id, cortexOutputJob),
          20.seconds
        )
        updatedJob.status shouldEqual JobStatus.Success
        updatedJob.report must beSome
        updatedJob.report.get \ "artifacts" must beEmpty
        (updatedJob.report.get \ "full" \ "data").as[String] shouldEqual "imageedit_2_3904987689.jpg"

        db.roTransaction { implicit graph =>
          jobSrv.get(updatedJob).observable.toList.map(_._id) must contain(exactly(observable._id))
          jobSrv.get(updatedJob).reportObservables.toList.length must equalTo(2)

          val jobFinished = new JobFinished()

          val audit = auditSrv.initSteps.has("objectId", updatedJob._id).getOrFail()

          audit must beSuccessfulTry

          val orga = orgSrv.get("cert").getOrFail()

          orga must beSuccessfulTry

          val user1 = userSrv.initSteps.getByName("user1@thehive.local").getOrFail()

          user1 must beSuccessfulTry

          jobFinished.filter(audit.get, Some(updatedJob), orga.get, user1.get) must beTrue
        }
      }

      "submit a job" in {
        val maybeObservable = db.roTransaction { implicit graph =>
          observableSrv.initSteps.has("message", "Some weird domain").getOrFail()
        }

        maybeObservable must beSuccessfulTry

        val observable = maybeObservable.get
        val c = db.roTransaction { implicit graph =>
          caseSrv.initSteps.has("title", "case#1").getOrFail()
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
