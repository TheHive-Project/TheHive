package org.thp.thehive.controllers.v0

import org.thp.scalligraph.controllers.Entrypoint
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.Permissions
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

class PermissionCtrl(entrypoint: Entrypoint) {
  def list: Action[AnyContent] =
    entrypoint("list permissions")
      .auth(_ => Success(Results.Ok(Permissions.list.toJson)))
}
