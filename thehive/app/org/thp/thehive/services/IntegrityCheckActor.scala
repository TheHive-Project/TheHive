package org.thp.thehive.services

import akka.actor.ActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import org.quartz
import org.quartz._
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.{DedupCheck, GlobalCheck, IntegrityCheck, KillSwitch}
import org.thp.scalligraph.utils.FunctionalCondition.When
import play.api.Logger
import play.api.libs.json._

import javax.inject.{Inject, Provider, Singleton}
import scala.collection.immutable
import scala.concurrent.duration.{DurationDouble, DurationLong, FiniteDuration}
import scala.util.Try

case class CheckStats(global: Map[String, Long], last: Map[String, Long], lastDate: Long) {
  def +(stats: Map[String, Long]): CheckStats = {
    val mergedMap = (stats.keySet ++ global.keySet).map(k => k -> (global.getOrElse(k, 0L) + stats.getOrElse(k, 0L))).toMap
    CheckStats(mergedMap + ("iteration" -> (mergedMap.getOrElse("iteration", 0L) + 1)), stats, System.currentTimeMillis())
  }
}
object CheckState {
  val empty: CheckState = {
    val emptyStats = CheckStats(Map.empty, Map.empty, 0L)
    CheckState(
      needCheck = false,
      None,
      dedupRequested = false,
      dedupIsRunning = false,
      emptyStats,
      globalCheckRequested = false,
      globalCheckIsRunning = false,
      emptyStats
    )
  }
}
case class CheckState(
    needCheck: Boolean,
    dedupTimer: Option[AnyRef],
    dedupRequested: Boolean,
    dedupIsRunning: Boolean,
    dedupStats: CheckStats,
    globalCheckRequested: Boolean,
    globalCheckIsRunning: Boolean,
    globalStats: CheckStats
)

case class IntegrityCheckGlobalConfig(
    enabled: Boolean,
    schedule: String,
    maxDuration: FiniteDuration,
    integrityCheckConfig: Map[String, IntegrityCheckConfig]
)
object IntegrityCheckGlobalConfig {
  implicit val format: OFormat[IntegrityCheckGlobalConfig] = Json.format[IntegrityCheckGlobalConfig]
}

sealed trait DedupStrategy
object DedupStrategy {
  final case object AfterAddition      extends DedupStrategy
  final case object DuringGlobalChecks extends DedupStrategy
  final case object Disable            extends DedupStrategy
  implicit val reads: Reads[DedupStrategy] = Reads.StringReads.flatMap {
    case "AfterAddition"      => Reads.pure(AfterAddition)
    case "DuringGlobalChecks" => Reads.pure(DuringGlobalChecks)
    case "Disable"            => Reads.pure(Disable)
    case other                => Reads.failed(s"Dedup strategy `$other` is not recognised (accepted: AfterAddition, DuringGlobalChecks and Disable)")
  }
  implicit val writes: Writes[DedupStrategy] = Writes[DedupStrategy](s => JsString(s.toString))
}
case class IntegrityCheckConfig(
    enabled: Boolean,
    minTime: Option[FiniteDuration],
    maxTime: Option[FiniteDuration],
    dedupStrategy: DedupStrategy,
    initialDelay: FiniteDuration,
    minInterval: FiniteDuration
)
object IntegrityCheckConfig {
  implicit val format: OFormat[IntegrityCheckConfig] = Json.format[IntegrityCheckConfig]
}

object IntegrityCheck {
  private val logger = Logger(getClass)

  sealed trait Message
  sealed trait Request         extends Message
  sealed trait Response        extends Message
  sealed trait InternalMessage extends Request

  case class EntityAdded(name: String)                                             extends Request
  case class NeedCheck(name: String)                                               extends InternalMessage
  case class CheckRequest(name: String, dedup: Boolean, global: Boolean)           extends Request
  case class GetAllCheckStats(replyTo: ActorRef[AllCheckStats])                    extends Request
  case class AllCheckStats(stats: Map[String, Map[String, Long]])                  extends Response
  case class StartDedup(name: String)                                              extends InternalMessage
  case class FinishDedup(name: String, cancel: Boolean, stats: Map[String, Long])  extends InternalMessage
  case class StartGlobal(name: String)                                             extends InternalMessage
  case class FinishGlobal(name: String, cancel: Boolean, stats: Map[String, Long]) extends InternalMessage
  case object CancelCheck                                                          extends Request

  private val jobKey             = JobKey.jobKey("IntegrityCheck")
  private val triggerKey         = TriggerKey.triggerKey("IntegrityCheck")
  private val checksContextKey   = "IntegrityCheck-checks"
  private val configContextKey   = "IntegrityCheck-config"
  private val actorRefContextKey = "IntegrityCheck-actor"

  def behavior(
      quartzScheduler: quartz.Scheduler,
      appConfig: ApplicationConfig,
      integrityChecks: Seq[IntegrityCheck]
  ): Behavior[Request] =
    Behaviors.setup[Request] { context =>
      Behaviors.withTimers[Request] { timers =>
        val configItem: ConfigItem[IntegrityCheckGlobalConfig, IntegrityCheckGlobalConfig] = appConfig.validatedItem[IntegrityCheckGlobalConfig](
          "integrityCheck",
          "Integrity check config",
          config =>
            Try {
              CronScheduleBuilder.cronSchedule(config.schedule)
              config
            }
        )

        setupScheduling(context.self, quartzScheduler, integrityChecks, configItem)
        behavior(context.self, quartzScheduler, configItem, timers, integrityChecks.map(_.name))
      }
    }

  private def behavior(
      self: ActorRef[IntegrityCheck.Request],
      quartzScheduler: quartz.Scheduler,
      configItem: ConfigItem[IntegrityCheckGlobalConfig, IntegrityCheckGlobalConfig],
      timers: TimerScheduler[Request],
      checkNames: Seq[String]
  ): Behavior[Request] = {
    def onMessage(states: Map[String, CheckState]): Behavior[IntegrityCheck.Request] =
      Behaviors
        .receiveMessage[Request] {
          case EntityAdded(name) =>
            logger.debug(s"An entity $name has been created")
            configItem.get.integrityCheckConfig.get(name).foreach {
              case cfg if cfg.dedupStrategy == DedupStrategy.AfterAddition =>
                timers.startSingleTimer(NeedCheck(name), cfg.initialDelay)
            }
            Behaviors.same

          case NeedCheck(name) =>
            val state   = states.getOrElse(name, CheckState.empty)
            val configs = configItem.get.integrityCheckConfig
            val cfg     = configs.getOrElse(name, configs("default"))
            if (state.dedupTimer.isEmpty) {
              val checkRequest = CheckRequest(name, dedup = true, global = false)
              self ! checkRequest
              val timer = new AnyRef
              timers.startTimerWithFixedDelay(timer, checkRequest, cfg.minInterval)
              onMessage(states + (name -> state.copy(needCheck = true, dedupTimer = Some(timer))))
            } else if (!state.needCheck)
              onMessage(states + (name -> state.copy(needCheck = true)))
            else Behaviors.same[Request]

          case CheckRequest(name, dedup, global) =>
            val state         = states.getOrElse(name, CheckState.empty)
            val dedupRequest  = dedup && !state.dedupRequested
            val globalRequest = global && !state.globalCheckRequested
            if (dedupRequest || globalRequest) {
              val trigger = TriggerBuilder
                .newTrigger()
                .withIdentity(s"$triggerKey-$name${if (dedupRequest) "-dedup" else ""}${if (globalRequest) "-global" else ""}")
                .startNow()
                .forJob(jobKey)
                .usingJobData("name", name)
                .usingJobData("dedup", dedupRequest)
                .usingJobData("global", globalRequest)
                .build()
              val nextRun = quartzScheduler.scheduleJob(trigger)
              logger.info(
                s"Integrity check on $name ${if (dedupRequest) "( dedup" else "("}${if (globalRequest) " global )" else " )"}: job scheduled, it will start at $nextRun"
              )

              onMessage(
                states + (name -> state
                  .copy(dedupRequested = state.dedupRequested || dedupRequest, globalCheckRequested = state.globalCheckRequested || globalRequest))
              )
            } else {
              logger.info(s"Integrity check on $name ignore because a job is already pending")
              onMessage(states)
            }
          case StartDedup(name) =>
            logger.info(s"Start of deduplication of $name")
            val state = states.getOrElse(name, CheckState.empty)
            onMessage(states + (name -> state.copy(needCheck = false, dedupIsRunning = true)))
          case FinishDedup(name, cancel, result) =>
            logger.info(s"End of deduplication of $name${if (cancel) " (cancelled)" else ""}:${result.map(kv => s"\n  ${kv._1}: ${kv._2}").mkString}")
            val state    = states.getOrElse(name, CheckState.empty)
            val newState = state.copy(dedupStats = state.dedupStats + result, dedupIsRunning = false)

            if (state.needCheck) onMessage(states + (name -> newState))
            else {
              state.dedupTimer.foreach(timers.cancel)
              onMessage(states + (name -> newState.copy(dedupTimer = None, dedupRequested = false)))
            }
          case StartGlobal(name) =>
            logger.info(s"Start of global check of $name")
            val state = states.getOrElse(name, CheckState.empty)
            onMessage(states + (name -> state.copy(globalCheckIsRunning = true)))
          case FinishGlobal(name, cancel, result) =>
            logger.info(s"End of global check of $name${if (cancel) " (cancelled)" else ""}:${result.map(kv => s"\n  ${kv._1}: ${kv._2}").mkString}")
            val state    = states.getOrElse(name, CheckState.empty)
            val newState = state.copy(dedupStats = state.dedupStats + result, globalCheckRequested = false, globalCheckIsRunning = false)
            onMessage(states + (name -> newState))

          case CancelCheck =>
            quartzScheduler.interrupt(jobKey)
            Behaviors.same

          case GetAllCheckStats(replyTo) =>
            val state = states.mapValues { s =>
              Map(
                "needCheck"            -> (if (s.needCheck) 1L else 0L),
                "dedupTimer"           -> s.dedupTimer.fold(0L)(_ => 1L),
                "dedupRequested"       -> (if (s.dedupRequested) 1L else 0L),
                "dedupIsRunning"       -> (if (s.dedupIsRunning) 1L else 0L),
                "globalCheckRequested" -> (if (s.globalCheckRequested) 1L else 0L),
                "globalCheckIsRunning" -> (if (s.globalCheckIsRunning) 1L else 0L)
              ) ++
                s.globalStats.global.map { case (k, v) => s"global.$k" -> v } ++
                s.globalStats.last.map {
                  case (k, v) => s"globalLast.$k" -> v
                } +
                ("globalLastDate" -> s.globalStats.lastDate) ++
                s.dedupStats.global.map { case (k, v) => s"dedup.$k" -> v } ++
                s.dedupStats.last.map {
                  case (k, v) => s"dedupLast.$k" -> v
                } +
                ("dedupLastDate" -> s.dedupStats.lastDate)
            }

            replyTo ! AllCheckStats(state)
            Behaviors.same
        }
        .receiveSignal {
          case (_, PostStop) =>
            quartzScheduler.interrupt(jobKey)
            quartzScheduler.deleteJob(jobKey)
            logger.info("Remove integrity check job")
            Behaviors.same
        }

    onMessage(checkNames.map(_ -> CheckState.empty).toMap)

  }

  private def setupScheduling(
      integrityCheckActorRef: ActorRef[IntegrityCheck.Request],
      quartzScheduler: quartz.Scheduler,
      integrityChecks: Seq[IntegrityCheck],
      configItem: ConfigItem[IntegrityCheckGlobalConfig, IntegrityCheckGlobalConfig]
  ): Unit = {
    logger.debug("Setting up Integrity check actor with its schedule")
    quartzScheduler.addJob(job(), true)
    quartzScheduler.getContext.put(checksContextKey, integrityChecks)
    quartzScheduler.getContext.put(configContextKey, configItem)
    quartzScheduler.getContext.put(actorRefContextKey, integrityCheckActorRef)

    configItem.onUpdate { (_, newConfig) =>
      if (newConfig.enabled) {
        val trigger = jobTrigger(newConfig.schedule, newConfig.maxDuration)
        val nextRun = Option(quartzScheduler.getTrigger(triggerKey)) match {
          case Some(_) => quartzScheduler.rescheduleJob(triggerKey, trigger)
          case None    => quartzScheduler.scheduleJob(trigger)
        }
        logger.info(s"Config updated, will run next integrity checks at $nextRun")
      } else {
        quartzScheduler.unscheduleJob(triggerKey)
        logger.info("Config updated, removing scheduling for integrity check job")
      }
    }

    val initConfig = configItem.get
    if (initConfig.enabled) {
      val trigger = jobTrigger(initConfig.schedule, initConfig.maxDuration)
      val nextRun = quartzScheduler.scheduleJob(trigger)
      logger.info(s"Integrity checks is enabled and will start at $nextRun")
    } else
      logger.info("Integrity checks is disabled")
  }

  private def job() =
    JobBuilder
      .newJob()
      .ofType(classOf[RunJob])
      .withIdentity(jobKey)
      .storeDurably()
      .build()

  private def jobTrigger(cronExpression: String, maxDuration: FiniteDuration) =
    TriggerBuilder
      .newTrigger()
      .withIdentity(triggerKey)
      .usingJobData("maxDuration", java.lang.Long.valueOf(maxDuration.toMillis))
      .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
      .forJob(jobKey)
      .build()

  @DisallowConcurrentExecution
  private class RunJob extends Job with InterruptableJob with KillSwitch {
    override def interrupt(): Unit = {
      logger.info("Cancellation of check job has been requested")
      _continueProcess = false
    }
    override def reset(): Unit            = _continueProcess = true
    override def continueProcess: Boolean = _continueProcess
    private var _continueProcess          = true

    def runDedup(
        integrityCheckActor: ActorRef[IntegrityCheck.Request],
        integrityChecks: Seq[IntegrityCheck],
        name: String
    ): Unit = {
      integrityCheckActor ! IntegrityCheck.StartDedup(name)
      val startDate = System.currentTimeMillis()
      val result = integrityChecks
        .collectFirst {
          case dc: DedupCheck[_] if dc.name == name => dc
        }
        .fold(Map("checkNotFound" -> 1L))(_.dedup(killSwitch = this))
      val duration = System.currentTimeMillis() - startDate
      if (continueProcess)
        integrityCheckActor ! FinishDedup(name, cancel = false, result + ("duration" -> duration))
      else {
        reset()
        integrityCheckActor ! FinishDedup(name, cancel = true, result + ("duration" -> duration))
      }
    }

    def runGlobal(
        integrityCheckActor: ActorRef[IntegrityCheck.Request],
        integrityChecks: Seq[IntegrityCheck],
        name: String,
        maxDuration: FiniteDuration
    ): Unit = {
      integrityCheckActor ! IntegrityCheck.StartGlobal(name)
      val result = integrityChecks
        .collectFirst {
          case gc: GlobalCheck[_] if gc.name == name => gc
        }
        .fold(Map("checkNotFound" -> 1L))(_.runGlobalCheck(maxDuration, killSwitch = this))
      if (continueProcess)
        integrityCheckActor ! FinishGlobal(name, cancel = false, result)
      else {
        reset()
        integrityCheckActor ! FinishGlobal(name, cancel = true, result)
      }
    }

    def getConfig(config: IntegrityCheckGlobalConfig, name: String): IntegrityCheckConfig =
      config.integrityCheckConfig.getOrElse(name, config.integrityCheckConfig("default"))

    def runBoth(
        config: IntegrityCheckGlobalConfig,
        integrityCheckActor: ActorRef[IntegrityCheck.Request],
        integrityChecks: Seq[IntegrityCheck],
        name: String,
        maxDuration: FiniteDuration
    ): Unit = {
      val cfg = getConfig(config, name)
      runGlobal(integrityCheckActor, integrityChecks, name, maxDuration.merge(cfg.maxTime)(min).merge(cfg.minTime)(max))
      if (cfg.dedupStrategy == DedupStrategy.DuringGlobalChecks)
        runDedup(integrityCheckActor, integrityChecks, name)
    }

    def max(a: FiniteDuration, b: FiniteDuration): FiniteDuration = if (a < b) b else a

    def min(a: FiniteDuration, b: FiniteDuration): FiniteDuration = if (a > b) b else a

    override def execute(context: JobExecutionContext): Unit = {
      reset()
      val integrityChecks = context.getScheduler.getContext.get(checksContextKey).asInstanceOf[Seq[IntegrityCheck]]
      val configItem =
        context.getScheduler.getContext.get(configContextKey).asInstanceOf[ConfigItem[IntegrityCheckGlobalConfig, IntegrityCheckGlobalConfig]]
      val integrityCheckActor = context.getScheduler.getContext.get(actorRefContextKey).asInstanceOf[ActorRef[IntegrityCheck.Request]]
      val jobData             = context.getMergedJobDataMap
      val maxDuration         = jobData.get("maxDuration").asInstanceOf[Long]

      if (jobData.containsKey("name")) {
        val name   = jobData.get("name").asInstanceOf[String]
        val dedup  = if (jobData.containsKey("dedup")) jobData.get("dedup").asInstanceOf[Boolean] else true
        val global = if (jobData.containsKey("global")) jobData.get("global").asInstanceOf[Boolean] else true
        if (dedup) runDedup(integrityCheckActor, integrityChecks, name)
        if (global) runGlobal(integrityCheckActor, integrityChecks, name, 24.hours)
      } else {
        val config: IntegrityCheckGlobalConfig = configItem.get
        val enabledChecks                      = integrityChecks.filter(c => getConfig(config, c.name).enabled)
        if (config.enabled && enabledChecks.nonEmpty) {
          val startAt = System.currentTimeMillis()
          val checksWithPerf = enabledChecks.collect {
            case c: GlobalCheck[_] => (c, c.getPerformanceIndicator)
          }
          val avg1 = (maxDuration / enabledChecks.size).millis
          // checks are quick if they have finished the process of all the dataset in one turn
          val (quickChecks, otherChecks) = checksWithPerf.partition(p => p._2.period.isEmpty && p._2.duration.isDefined)
          quickChecks.foreach(c => runBoth(config, integrityCheckActor, enabledChecks, c._1.name, avg1))
          val remainingTime1 = maxDuration - (System.currentTimeMillis - startAt)
          // checks are known if there is performance indicator (period and duration)
          if (otherChecks.nonEmpty) {
            val (knownChecks, unknownChecks) = otherChecks.partition(p => p._2.duration.exists(_ > 0) && p._2.period.exists(_ > 0))
            val avg2                         = remainingTime1 / otherChecks.size
            unknownChecks.foreach(c => runBoth(config, integrityCheckActor, enabledChecks, c._1.name, avg2.millis))
            val remainingTime2 = maxDuration - (System.currentTimeMillis - startAt)
            val sum            = knownChecks.map(c => c._2.duration.get.toDouble / c._2.period.get).sum
            knownChecks.foreach(c =>
              runBoth(config, integrityCheckActor, enabledChecks, c._1.name, (remainingTime2 * sum * c._2.period.get / c._2.duration.get).millis)
            )
          }
        }
      }
    }

  }
}

@Singleton
class IntegrityCheckActorProvider @Inject() (
    system: ActorSystem,
    quartzScheduler: quartz.Scheduler,
    appConfig: ApplicationConfig,
    integrityChecks: immutable.Set[IntegrityCheck]
) extends Provider[ActorRef[IntegrityCheck.Request]] {
  override lazy val get: ActorRef[IntegrityCheck.Request] =
    ClusterSingleton(system.toTyped)
      .init(SingletonActor(IntegrityCheck.behavior(quartzScheduler, appConfig, integrityChecks.toSeq), "IntegrityCheckActor"))
}
