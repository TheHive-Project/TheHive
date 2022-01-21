package org.thp.thehive.controllers.v1

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.models.{Permissions, User}
import org.thp.thehive.services.LocalPasswordAuthSrv
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import play.api.libs.json._

import java.util.{Map => JMap}

trait UserRenderer extends BaseRenderer[User] {

  def lockout(
      localPasswordAuthSrv: Option[LocalPasswordAuthSrv]
  )(implicit authContext: AuthContext): Traversal.V[User] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.project(_.by.by(_.organisations.users(Permissions.manageUser).current.option))
      .domainMap {
        case (user, Some(_)) =>
          Json.obj(
            "lastFailed"     -> user.lastFailed,
            "failedAttempts" -> user.failedAttempts,
            "lockedUntil"    -> localPasswordAuthSrv.flatMap(_.lockedUntil(user))
          )
        case _ => JsObject.empty
      }

  def userStatsRenderer(extraData: Set[String], authSrv: Option[LocalPasswordAuthSrv])(implicit
      authContext: AuthContext
  ): Traversal.V[User] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "lockout") => addData("lockout", f)(lockout(authSrv))
        case (f, _)         => f
      }
    )
  }
}
