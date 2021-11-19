package org.thp.thehive.connector.cortex.services

import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.cortex.dto.v0.OutputJob
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv, Schema}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{AppBuilder, EntityName}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.models.{Job, JobStatus, TheHiveCortexSchemaProvider}
import org.thp.thehive.connector.cortex.services.JobOps._
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import org.thp.thehive.services.notification.triggers.JobFinished
import play.api.libs.json.{JsObject, Json}
import play.api.test.PlaySpecification

import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.io.Source

class JobSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all).authContext
  override def appConfigure: AppBuilder =
    super
      .appConfigure
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexClient, TestCortexClientProvider]
      .bind[Connector, TestConnector]
      .`override`(_.bindToProvider[Schema, TheHiveCortexSchemaProvider])

  "job service" should {
    "handle creation and then finished job" in testApp { app =>
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

      val cortexOutputJob = {
        val dataSource = Source.fromResource("cortex-jobs.json")
        val data       = dataSource.mkString
        dataSource.close()
        Json.parse(data).as[List[OutputJob]].find(_.id == "ZWu85Q1OCVNx03hXK4df").get
      }

      val createdJobTry = app[Database].tryTransaction { implicit graph =>
        for {
          observable <- app[ObservableSrv].startTraversal.has(_.message, "hello world").getOrFail("Observable")
          createdJob <- app[JobSrv].create(job, observable)
        } yield createdJob
      }
      createdJobTry.map { createdJob =>
        Await.result(app[JobSrv].finished(app[CortexClient].name, createdJob._id, cortexOutputJob), 20.seconds)
      } must beASuccessfulTry.which { updatedJob =>
        updatedJob.status shouldEqual JobStatus.Success
        updatedJob.report must beSome
        (updatedJob.report.get \ "data").as[String] shouldEqual "imageedit_2_3904987689.jpg"

        app[Database].roTransaction { implicit graph =>
          app[JobSrv].get(updatedJob).observable.has(_.message, "hello world").exists must beTrue
          app[JobSrv].get(updatedJob).reportObservables.toList.length must equalTo(2).updateMessage { s =>
            s"$s\nreport observables are : ${app[JobSrv].get(updatedJob).reportObservables.richObservable.toList.mkString("\n")}"
          }

          for {
            audit        <- app[AuditSrv].startTraversal.has(_.objectId, updatedJob._id.toString).getOrFail("Audit")
            organisation <- app[OrganisationSrv].getByName("cert").getOrFail("Organisation")
            user         <- app[UserSrv].startTraversal.getByName("certuser@thehive.local").getOrFail("User")
          } yield new JobFinished().filter(audit, Some(updatedJob), organisation, Some(user))
        } must beASuccessfulTry(true)
      }
    }

    "submit a job" in testApp { app =>
      val x = for {
        observable <- app[Database].roTransaction { implicit graph =>
          app[ObservableSrv].startTraversal.has(_.message, "Some weird domain").richObservable.getOrFail("Observable")
        }
        case0 <- app[Database].roTransaction { implicit graph =>
          app[CaseSrv].getOrFail(EntityName("1"))
        }
      } yield await(app[JobSrv].submit("test", "anaTest1", observable, case0, JsObject.empty))

      x must beASuccessfulTry.which { job =>
        job.cortexId shouldEqual "test"
        job.workerId shouldEqual "anaTest1"
      }
    }
  }
}
