package org.thp.thehive.shell

import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.{ScalligraphApplication, ScalligraphApplicationImpl, ScalligraphModule}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.TheHiveStarter.readConfig
import org.thp.thehive.services.{TheHiveOps, UserSrv => _}
import play.api._
import play.api.libs.concurrent.ActorSystemProvider.ApplicationShutdownReason
import play.core.server.RealServerProcess

import scala.util.Success

object Shell extends App {
  val process = new RealServerProcess(args.toSeq)
  val config  = readConfig(process)

  val app = new ScalligraphApplicationImpl(config.rootDir, process.classLoader, Mode.Dev, Map("config.file" -> "conf/application.conf"))
  try {
    val thehiveModule = new TheHiveModule(app)
    val shellModule   = new ShellModule(app, thehiveModule)
    app.initializeLogger()
    app.database.tryTransaction { implicit graph =>
      TheHiveOps(thehiveModule.organisationSrv, thehiveModule.customFieldSrv) { ops =>
        ammonite
          .Main(
            predefCode = """
                           |import ops._
                           |import org.thp.scalligraph.EntityIdOrName.Implicit._
                           |val auth = org.thp.thehive.shell.MutableAuthContext
                           |implicit val _auth: org.thp.scalligraph.auth.AuthContext = auth
                           |""".stripMargin,
            welcomeBanner = Some("TheHive Shell")
          )
          .run("data" -> shellModule.data, "log" -> LogAccess, "ops" -> ops)
      }
      Success(())
    }
  } finally app.coordinatedShutdown.run(ApplicationShutdownReason).map(_ => System.exit(1))(app.executionContext)
}

class ShellModule(app: ScalligraphApplication, thehiveModule: TheHiveModule) extends ScalligraphModule {
  import com.softwaremill.macwire._

  lazy val dataProvider: DataAccessProvider   = wire[DataAccessProvider]
  def data(implicit graph: Graph): DataAccess = dataProvider.get(graph)
}
