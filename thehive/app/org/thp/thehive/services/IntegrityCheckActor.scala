package org.thp.thehive.services

import akka.actor.{Actor, Cancellable}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.services.{GenIntegrityCheckOps, IntegrityCheckOps}
import play.api.Logger

import java.util.concurrent.Executors
import scala.concurrent.duration.{Duration, FiniteDuration, NANOSECONDS}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Success}

sealed trait IntegrityCheckMessage
case class EntityAdded(name: String)                                      extends IntegrityCheckMessage
case class NeedCheck(name: String)                                        extends IntegrityCheckMessage
case class DuplicationCheck(name: String)                                 extends IntegrityCheckMessage
case class DuplicationCheckResult(name: String, stats: Map[String, Long]) extends IntegrityCheckMessage
case class GlobalCheckRequest(name: String)                               extends IntegrityCheckMessage
case class GlobalCheckResult(name: String, stats: Map[String, Long])      extends IntegrityCheckMessage
case class GetCheckStats(name: String)                                    extends IntegrityCheckMessage

case class CheckStats(global: Map[String, Long], last: Map[String, Long], lastDate: Long) extends IntegrityCheckMessage {
  def +(stats: Map[String, Long]): CheckStats = {
    val mergedMap = (stats.keySet ++ global.keySet).map(k => k -> (global.getOrElse(k, 0L) + stats.getOrElse(k, 0L))).toMap
    CheckStats(mergedMap + ("iteration" -> (mergedMap.getOrElse("iteration", 0L) + 1)), stats, System.currentTimeMillis())
  }
}
object CheckState {
  val empty: CheckState = {
    val emptyStats = CheckStats(Map.empty, Map.empty, 0L)
    CheckState(needCheck = true, None, emptyStats, emptyStats, 0L)
  }
}
case class CheckState(
    needCheck: Boolean,
    duplicateTimer: Option[Cancellable],
    duplicateStats: CheckStats,
    globalStats: CheckStats,
    globalCheckRequestTime: Long
)

sealed trait IntegrityCheckTag
class IntegrityCheckActor(appConfig: ApplicationConfig, db: Database, _integrityCheckOps: Seq[GenIntegrityCheckOps]) extends Actor {

  import context.dispatcher

  lazy val logger: Logger = Logger(getClass)
  lazy val integrityCheckOps: Seq[IntegrityCheckOps[_ <: Product]] = _integrityCheckOps
    .asInstanceOf[Seq[IntegrityCheckOps[_ <: Product]]]
  lazy val checkExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))

  val defaultInitialDelayConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("integrityCheck.default.initialDelay", "Default delay between the creation of data and the check")

  def defaultInitialDelay: FiniteDuration = defaultInitialDelayConfig.get

  val defaultIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("integrityCheck.default.interval", "Default interval between two checks")

  def defaultInterval: FiniteDuration = defaultIntervalConfig.get

  val defaultGlobalCheckIntervalConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("integrityCheck.default.globalInterval", "Default interval between two global checks")

  def defaultGlobalCheckInterval: FiniteDuration = defaultGlobalCheckIntervalConfig.get

  def initialDelay(name: String): FiniteDuration =
    appConfig
      .get(s"integrityCheck.$name.initialDelay")
      .asInstanceOf[Option[ConfigItem[FiniteDuration, FiniteDuration]]]
      .fold(defaultInitialDelay)(_.get)

  def interval(name: String): FiniteDuration =
    appConfig
      .get(s"integrityCheck.$name.interval")
      .asInstanceOf[Option[ConfigItem[FiniteDuration, FiniteDuration]]]
      .fold(defaultInterval)(_.get)

  def globalInterval(name: String): FiniteDuration =
    appConfig
      .get(s"integrityCheck.$name.globalInterval")
      .asInstanceOf[Option[ConfigItem[FiniteDuration, FiniteDuration]]]
      .fold(defaultGlobalCheckInterval)(_.get)

  lazy val integrityCheckMap: Map[String, IntegrityCheckOps[_]] =
    integrityCheckOps.map(d => d.name -> d).toMap

  def duplicationCheck(name: String): Map[String, Long] = {
    val startDate = System.currentTimeMillis()
    val result    = integrityCheckMap.get(name).fold(Map("checkNotFound" -> 1L))(_.duplicationCheck())
    val endDate   = System.currentTimeMillis()
    result + ("startDate" -> startDate) + ("endDate" -> endDate) + ("duration" -> (endDate - startDate))
  }

  private var globalTimers: Seq[Cancellable] = Nil

  override def preStart(): Unit = {
    super.preStart()

    integrityCheckOps.map(_.name).foreach { name =>
      appConfig.item[FiniteDuration](s"integrityCheck.$name.initialDelay", s"Delay between the creation of data and the check for $name")
      appConfig.item[FiniteDuration](s"integrityCheck.$name.interval", s"Interval between two checks for $name")
      appConfig.item[FiniteDuration](s"integrityCheck.$name.globalInterval", s"Interval between two global checks for $name")
    }

    implicit val authContext: AuthContext = LocalUserSrv.getSystemAuthContext
    integrityCheckOps.foreach { integrityCheck =>
      db.tryTransaction { implicit graph =>
        Success(integrityCheck.initialCheck())
      }
    }
    integrityCheckOps.foreach { integrityCheck =>
      self ! DuplicationCheck(integrityCheck.name)
    }
    globalTimers = integrityCheckOps.map { integrityCheck =>
      val interval     = globalInterval(integrityCheck.name)
      val initialDelay = FiniteDuration((interval.toNanos * Random.nextDouble()).round, NANOSECONDS)
      context
        .system
        .scheduler
        .scheduleWithFixedDelay(initialDelay, interval) { () =>
          logger.debug(s"Global check of ${integrityCheck.name}")
          val startDate = System.currentTimeMillis()
          val result    = integrityCheck.globalCheck()
          val duration  = System.currentTimeMillis() - startDate
          self ! GlobalCheckResult(integrityCheck.name, result + ("duration" -> duration))
        }
    }.toSeq
  }

  override def postStop(): Unit = {
    super.postStop()
    globalTimers.foreach(_.cancel())
  }

  override def receive: Receive = {
    val globalTimers = integrityCheckOps.map { integrityCheck =>
      val interval     = globalInterval(integrityCheck.name)
      val initialDelay = FiniteDuration((interval.toNanos * Random.nextDouble()).round, NANOSECONDS)
      context
        .system
        .scheduler
        .scheduleWithFixedDelay(initialDelay, interval) { () =>
          logger.debug(s"Global check of ${integrityCheck.name}")
          val startDate = System.currentTimeMillis()
          val result    = integrityCheckMap.get(integrityCheck.name).fold(Map("checkNotFound" -> 1L))(_.globalCheck())
          val duration  = System.currentTimeMillis() - startDate
          self ! GlobalCheckResult(integrityCheck.name, result + ("duration" -> duration))
        }
      integrityCheck.name -> CheckState.empty
    }
    receive(globalTimers.toMap)
  }

  def receive(states: Map[String, CheckState]): Receive = {
    case EntityAdded(name) =>
      logger.debug(s"An entity $name has been created")
      context.system.scheduler.scheduleOnce(initialDelay(name), self, NeedCheck(name))
      ()
    case NeedCheck(name) =>
      states.get(name).foreach { state =>
        if (state.duplicateTimer.isEmpty) {
          val timer = context.system.scheduler.scheduleWithFixedDelay(Duration.Zero, interval(name), self, DuplicationCheck(name))
          context.become(receive(states + (name -> state.copy(needCheck = true, duplicateTimer = Some(timer)))))
        } else if (!state.needCheck)
          context.become(receive(states + (name -> state.copy(needCheck = true))))
      }
    case DuplicationCheck(name) =>
      states.get(name).foreach { state =>
        if (state.needCheck) {
          Future {
            logger.debug(s"Duplication check of $name")
            val startDate = System.currentTimeMillis()
            val result    = integrityCheckMap.get(name).fold(Map("checkNotFound" -> 1L))(_.duplicationCheck())
            val duration  = System.currentTimeMillis() - startDate
            self ! DuplicationCheckResult(name, result + ("duration" -> duration))
          }(checkExecutionContext)
          context.become(receive(states + (name -> state.copy(needCheck = false))))
        } else {
          state.duplicateTimer.foreach(_.cancel())
          context.become(receive(states + (name -> state.copy(duplicateTimer = None))))
        }
      }
    case DuplicationCheckResult(name, stats) =>
      states.get(name).foreach { state =>
        context.become(receive(states + (name -> state.copy(duplicateStats = state.duplicateStats + stats))))
      }

    case GlobalCheckRequest(name) =>
      states.get(name).foreach { state =>
        val now                   = System.currentTimeMillis()
        val lastRequestIsObsolete = state.globalStats.lastDate >= state.globalCheckRequestTime
        val checkIsRunning        = state.globalStats.lastDate + globalInterval(name).toMillis > now
        if (lastRequestIsObsolete && !checkIsRunning) {
          Future {
            logger.debug(s"Global check of $name")
            val startDate = System.currentTimeMillis()
            val result    = integrityCheckMap.get(name).fold(Map("checkNotFound" -> 1L))(_.globalCheck())
            val duration  = System.currentTimeMillis() - startDate
            self ! GlobalCheckResult(name, result + ("duration" -> duration))
          }(checkExecutionContext)
          context.become(receive(states = states + (name -> state.copy(globalCheckRequestTime = now))))
        }
      }
    case GlobalCheckResult(name, stats) =>
      states.get(name).foreach { state =>
        context.become(receive(states + (name -> state.copy(globalStats = state.globalStats + stats))))
      }

    case GetCheckStats(name) =>
      sender() ! states.getOrElse(name, CheckStats(Map("checkNotFound" -> 1L), Map("checkNotFound" -> 1L), 0L))
  }
}
