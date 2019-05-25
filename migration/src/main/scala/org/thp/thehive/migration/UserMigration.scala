package org.thp.thehive.migration

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{search, RichString}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.Instance
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, UserSrv ⇒ UserDB}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.models._
import org.thp.thehive.services.{ProfileSrv, UserSrv}

import org.elastic4play.database.DBFind

@Singleton
class UserMigration @Inject()(
    config: Configuration,
    dbFind: DBFind,
    userSrv: UserSrv,
    userDB: UserDB,
    profileSrv: ProfileSrv,
    implicit val mat: Materializer
) extends Utils {
  private var userMap: Map[String, RichUser] = Map.empty[String, RichUser]
  val defaultOrganisation: String            = config.get[String]("organisation.name")

  implicit val userReads: Reads[User] =
    ((JsPath \ "_id").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "key").readNullable[String] and
      (JsPath \ "status").readWithDefault[String]("ok").map(_.toLowerCase).map(UserStatus.withName) and
      (JsPath \ "password").readNullable[String])(User.apply _)

  def importUsers(terminal: Terminal, organisation: Organisation with Entity)(implicit db: Database, authContext: AuthContext): Unit = {
    val (srv, total) = dbFind(Some("all"), Nil)(index ⇒ search(index / "user"))
    val progress     = new ProgressBar(terminal, "Importing user", Await.result(total, Duration.Inf).toInt)
    val done = srv
      .map { userJs ⇒
        db.transaction { implicit graph ⇒
          catchError("User", userJs, progress) {
            progress.inc(extraMessage = (userJs \ "_id").asOpt[String].getOrElse("***"))
            ((userJs \ "role").asOpt[String].getOrElse("read") match {
              case "admin"      ⇒ profileSrv.getOrFail("admin")
              case "write"      ⇒ profileSrv.getOrFail("analyst")
              case /*"read"*/ _ ⇒ profileSrv.getOrFail("read-only")
            }).map { profile ⇒
              val user = userJs.as[User]
              userMap += user.login → userSrv.create(user, organisation, profile)
            }
          }
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }

  def withUser[A](name: String)(body: AuthContext ⇒ A): A = {
    val authContext = userMap.get(name) match {
      case Some(user) ⇒
        AuthContextImpl(user.login, user.name, defaultOrganisation, Instance.getInternalId, Set.empty)
      case None ⇒
        if (name != "init") logger.warn(s"User $name not found, using initial user context")
        userDB.initialAuthContext
    }
    body(authContext)
  }

  def get(name: String): Option[RichUser] = userMap.get(name)
}
