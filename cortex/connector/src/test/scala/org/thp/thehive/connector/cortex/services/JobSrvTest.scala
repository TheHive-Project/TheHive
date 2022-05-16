package org.thp.thehive.connector.cortex.services

import org.thp.cortex.dto.v0.OutputJob
import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.connector.cortex.TestAppBuilder
import org.thp.thehive.connector.cortex.models.{Job, JobStatus}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.notification.triggers.JobFinished
import org.thp.thehive.services.{TestAppBuilder => _}
import play.api.libs.json.{JsObject, Json}
import play.api.test.PlaySpecification

import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.io.Source

class JobSrvTest extends PlaySpecification with TestAppBuilder with CortexOps {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  "job service" should {
    "handle creation and then finished job" in testApp { app =>
      import app._
      import app.cortexModule.{cortexClient, jobSrv}
      import app.thehiveModule._

      val job = Job(
        workerId = "anaTest2",
        workerName = "anaTest2",
        workerDefinition = "test2",
        status = JobStatus.Waiting,
        startDate = new Date(1561625908856L),
        endDate = new Date(1561625908856L),
        report = None,
        cortexId = "test",
        cortexJobId = "LVyYKFstq3Rtrdc9DFmL",
        operations = Nil
      )

      val cortexOutputJob = {
        val dataSource = Source.fromResource("cortex-jobs.json")
        val data       = dataSource.mkString
        dataSource.close()
        Json.parse(data).as[List[OutputJob]].find(_.id == "ZWu85Q1OCVNx03hXK4df").get
      }

      val createdJobTry = database.tryTransaction { implicit graph =>
        for {
          observable <- observableSrv.startTraversal.has(_.message, "hello world").getOrFail("Observable")
          createdJob <- jobSrv.create(job, observable)
        } yield createdJob
      }

      val finishedJobTry = createdJobTry.map { createdJob =>
        Await.result(jobSrv.finished(cortexClient.name, createdJob._id, cortexOutputJob), 20.seconds)
      }
      finishedJobTry must beASuccessfulTry
      val updatedJob = finishedJobTry.get
      updatedJob.status shouldEqual JobStatus.Success
      updatedJob.report must beSome
      (updatedJob.report.get \ "data").as[String] shouldEqual "imageedit_2_3904987689.jpg"
      updatedJob.operations must haveSize(1)
      updatedJob.operations.map(o => (o \ "status").as[String]) must contain(beEqualTo("Success"))
        .forall
        .updateMessage(s => s"$s\nOperation has failed: ${updatedJob.operations.map("\n -" + _).mkString}")

      database.roTransaction { implicit graph =>
        jobSrv.get(updatedJob).observable.has(_.message, "hello world").exists must beTrue
        val reportObservables = jobSrv.get(updatedJob).reportObservables.toSeq
        reportObservables.length must equalTo(2).updateMessage { s =>
          s"$s\nreport observables are : ${jobSrv.get(updatedJob).reportObservables.richObservable.toList.mkString("\n")}"
        }
        val ipObservable = reportObservables.find(_.dataType == "ip").get
        ipObservable.data    must beSome("192.168.1.1")
        ipObservable.message must beSome("myIp")
        ipObservable.tags    must contain(exactly("tag-test"))
        ipObservable.tlp     must beEqualTo(2)

        val operationObservableMaybe = jobSrv
          .get(updatedJob)
          .observable
          .`case`
          .observables
          .has(_.message, "test-operation")
          .headOption
        operationObservableMaybe must beSome.which { operationObservable =>
          operationObservable.data must beSome("myData")
          operationObservable.tlp  must beEqualTo(3)
          operationObservable.tags must contain(exactly("tag1", "tag2"))
        }

        for {
          audit        <- auditSrv.startTraversal.has(_.objectId, updatedJob._id.toString).getOrFail("Audit")
          organisation <- organisationSrv.getByName("cert").getOrFail("Organisation")
          user         <- userSrv.startTraversal.getByName("certuser@thehive.local").getOrFail("User")
        } yield JobFinished.filter(audit, Some(updatedJob), organisation, Some(user))
      } must beASuccessfulTry(true)
    }

    "submit a job" in testApp { app =>
      import app._
      import app.cortexModule.jobSrv
      import app.thehiveModule._

      val x = for {
        observable <- database.roTransaction { implicit graph =>
          observableSrv.startTraversal.has(_.message, "Some weird domain").richObservable.getOrFail("Observable")
        }
        case0 <- database.roTransaction { implicit graph =>
          caseSrv.getOrFail(EntityName("1"))
        }
      } yield await(jobSrv.submit("test", "anaTest1", observable, case0, JsObject.empty))

      x must beASuccessfulTry.which { job =>
        job.cortexId shouldEqual "test"
        job.workerId shouldEqual "anaTest1"
      }
    }
  }
}
