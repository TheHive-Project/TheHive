package org.thp.thehive.connector.cortex.controllers.v0

import scala.util.Success

import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint

@Singleton
class ActionCtrl @Inject()(
    entryPoint: EntryPoint
) {

  def list: Action[AnyContent] =
    entryPoint("list analyzer")
      .auth { _ ⇒
        Success(Results.Ok(JsArray.empty))
      }
}
