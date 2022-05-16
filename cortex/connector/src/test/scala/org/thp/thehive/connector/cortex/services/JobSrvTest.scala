package org.thp.thehive.connector.cortex.services

import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.cortex.dto.v0.OutputJob
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv, Schema}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{AppBuilder, EntityName}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.models.{ActionOperationStatus, Job, JobStatus, TheHiveCortexSchemaProvider}
import org.thp.thehive.connector.cortex.services.JobOps._
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.CaseOps._
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
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext
  override def appConfigure: AppBuilder =
    super
      .appConfigure
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexClient, TestCortexClientProvider]
      .bind[Connector, TestConnector]
      .`override`(_.bindToProvider[Schema, TheHiveCortexSchemaProvider])

  "job service" should {
    val cortexOutputJob = {
      val dataSource = Source.fromResource("cortex-jobs.json")
      val data       = dataSource.mkString
      dataSource.close()
      Json.parse(data).as[List[OutputJob]].find(_.id == "ZWu85Q1OCVNx03hXK4df").get
    }

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
        cortexJobId = "LVyYKFstq3Rtrdc9DFmL",
        operations = Nil
      )

      val createdJobTry = app[Database].tryTransaction { implicit graph =>
        for {
          observable <- app[ObservableSrv].startTraversal.has(_.message, "hello world").getOrFail("Observable")
          createdJob <- app[JobSrv].create(job, observable)
        } yield createdJob
      }
      val finishedJobTry = createdJobTry.map { createdJob =>
        Await.result(app[JobSrv].finished(app[CortexClient].name, createdJob._id, cortexOutputJob), 20.seconds)
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

      app[Database].roTransaction { implicit graph =>
        app[JobSrv].get(updatedJob).observable.has(_.message, "hello world").exists must beTrue
        val reportObservables = app[JobSrv].get(updatedJob).reportObservables.toSeq
        reportObservables.length must equalTo(2).updateMessage { s =>
          s"$s\nreport observables are : ${app[JobSrv].get(updatedJob).reportObservables.richObservable.toList.mkString("\n")}"
        }
        val ipObservable = reportObservables.find(_.dataType == "ip").get
        ipObservable.data    must beSome("192.168.1.1")
        ipObservable.message must beSome("myIp")
        ipObservable.tags    must contain(exactly("tag-test"))
        ipObservable.tlp     must beEqualTo(2)

        val operationObservableMaybe = app[JobSrv]
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
          audit        <- app[AuditSrv].startTraversal.has(_.objectId, updatedJob._id.toString).getOrFail("Audit")
          organisation <- app[OrganisationSrv].getByName("cert").getOrFail("Organisation")
          user         <- app[UserSrv].startTraversal.getByName("certadmin@thehive.local").getOrFail("User")
        } yield JobFinished.filter(audit, Some(updatedJob), organisation, Some(user))
      } must beASuccessfulTry(true).setMessage("The audit doesn't match the expected criteria")
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
