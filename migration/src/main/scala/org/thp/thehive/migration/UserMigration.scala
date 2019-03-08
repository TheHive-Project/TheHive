package org.thp.thehive.migration
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{search, RichString}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.Instance
import org.thp.scalligraph.auth.{AuthContext, Permission, UserSrv ⇒ UserDB}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.models._
import org.thp.thehive.services.UserSrv

import org.elastic4play.database.DBFind

@Singleton
class UserMigration @Inject()(dbFind: DBFind, userSrv: UserSrv, userDB: UserDB, implicit val mat: Materializer) extends Utils {
  private var userMap: Map[String, RichUser] = Map.empty[String, RichUser]

  implicit val userReads: Reads[User] =
    ((JsPath \ "_id").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "key").readNullable[String] and
      (JsPath \ "roles")
        .readWithDefault[Seq[String]](Nil)
        .map(_.map(_.toLowerCase).map(Permissions.toPermission.orElse { case p ⇒ sys.error(s"InvalidPermission: $p") })) and
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
            val user = userJs.as[User]
            userMap += user.login → userSrv.create(user, organisation)
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
        new AuthContext {
          override def userId: String               = user.login
          override def userName: String             = user.name
          override def organisation: String         = user.organisation
          override def requestId: String            = Instance.getInternalId
          override def permissions: Seq[Permission] = Nil
        }
      case None ⇒
        if (name != "init") logger.warn(s"User $name not found, using initial user context")
        userDB.initialAuthContext
    }
    body(authContext)
  }

  def get(name: String): Option[RichUser] = userMap.get(name)
}
