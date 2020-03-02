package org.thp.thehive.services

import play.api.test.PlaySpecification

import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class ObservableSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local").authContext

  def printTiming[A](message: String)(body: => A): A = {
    val start = System.currentTimeMillis()
    val ret   = body
    val time  = System.currentTimeMillis() - start
    println(s"$message: $time ms")
    ret
  }

  "observable service" should {
    "create observable" in testApp { app =>
      val observable        = Observable(Some("test observable message"), 2, ioc = false, sighted = false)
      val observableSrv     = app[ObservableSrv]
      val observableTypeSrv = app[ObservableTypeSrv]
      val dataSrv           = app[DataSrv]
      val tagNames          = Set("t1", "t2", "t3")

      (1 to 100).map(i => s"data$i").toTry { dataValue =>
        println("====================================")
        printTiming("transaction") {
          app[Database].tryTransaction { implicit graph =>
            printTiming("createObservable") {
              for {
                createdObservable <- printTiming("createEntity")(observableSrv.createEntity(observable))
                tpe               <- printTiming("getType")(observableTypeSrv.getOrFail("domain"))
                _                 <- printTiming("linkType")(observableSrv.observableObservableType.create(ObservableObservableType(), createdObservable, tpe))
                data              <- printTiming("createData")(dataSrv.create(Data(dataValue)))
                _                 <- printTiming("linkData")(observableSrv.observableDataSrv.create(ObservableData(), createdObservable, data))
                tags              <- printTiming("createTags")(observableSrv.addTags(createdObservable, tagNames))
                //          ext               <- observableSrv.addExtensions(createdObservable, extensions)
              } yield RichObservable(createdObservable, tpe, Some(data), None, tags, Nil, Nil)
            }
          }
        }
      } must beSuccessfulTry
    }
  }
}
/*
observableType <- observableTypeSrv.getOrFail(inputObservable.`type`)
case Left(data) => observableSrv.create(inputObservable.observable, observableType, data, inputObservable.tags, Nil)
        }
        case0 <- caseSrv.getOrFail(caseId)
        orgs  <- inputObservable.organisations.toTry(organisationSrv.getOrFail)
        _     <- orgs.toTry(o => shareSrv.shareObservable(richObservable, case0, o))
 */
