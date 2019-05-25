package org.thp.thehive.controllers.v0

import scala.concurrent.{ExecutionContext, Promise}

import play.api.mvc.{Action, AnyContent, Result, Results}
import scala.concurrent.duration.DurationInt
import scala.util.Success

import play.api.libs.json.{JsArray, Json}

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint

@Singleton
class StreamCtrl @Inject()(entryPoint: EntryPoint, implicit val ec: ExecutionContext, system: ActorSystem) {

  def create: Action[AnyContent] = // TODO
    entryPoint("create stream") { _ ⇒
      Success(Results.Ok("none"))
    }

  def get(id: String): Action[AnyContent] = // TODO
    entryPoint("get stream").async { _ ⇒
      val response = Promise[Result]
      system.scheduler.scheduleOnce(1.minute)(response.success(Results.Ok(JsArray.empty)))
      response.future
    }

  def status: Action[AnyContent] = // TODO
    entryPoint("get stream") { _ ⇒
      Success(
        Results.Ok(
          Json.obj(
            "remaining" → 3600,
            "warning"   → false
          )
        )
      )
    }
}
