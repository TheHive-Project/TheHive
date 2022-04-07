package org.thp.thehive.cloner

import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.stream.Materializer
import com.google.inject.{Guice, Injector => GInjector}
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.auth.{UserSrv => UserDB}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services._
import org.thp.thehive.migration.th4.DummyActor
import org.thp.thehive.services._
import play.api.cache.ehcache.EhCacheModule
import play.api.inject.guice.GuiceInjector
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle, Injector}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Environment}

import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

trait IntegrityCheckApp {
  private def buildApp(configuration: Configuration, db: Database)(implicit actorSystem: ActorSystem): GInjector =
    Guice
      .createInjector(
        (play.api.inject.guice.GuiceableModule.guiceable(new EhCacheModule).guiced(Environment.simple(), configuration, Set.empty) :+
          new ScalaModule with AkkaGuiceSupport {
            override def configure(): Unit = {
              bind[Database].toInstance(db)

              bind[Configuration].toInstance(configuration)
              bind[ActorSystem].toInstance(actorSystem)
              bind[Materializer].toInstance(Materializer(actorSystem))
              bind[ExecutionContext].toInstance(actorSystem.dispatcher)
              bind[Injector].to[GuiceInjector]
              bind[UserDB].to[LocalUserSrv]
              bindActor[DummyActor]("notification-actor")
              bindActor[DummyActor]("config-actor")
              bindActor[DummyActor]("cortex-actor")
              bindActor[DummyActor]("integrity-check-actor")
              bind[ActorRef[CaseNumberActor.Request]].toProvider[CaseNumberActorProvider]

              val integrityCheckOpsBindings = ScalaMultibinder.newSetBinder[IntegrityCheck](binder)
              integrityCheckOpsBindings.addBinding.to[AlertIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[CaseIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[CaseTemplateIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[CustomFieldIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[DataIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[ImpactStatusIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[LogIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[ObservableIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[ObservableTypeIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[OrganisationIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[ProfileIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[ResolutionStatusIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[TagIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[TaskIntegrityCheck]
              integrityCheckOpsBindings.addBinding.to[UserIntegrityCheck]

              bind[Environment].toInstance(Environment.simple())
              bind[ApplicationLifecycle].to[DefaultApplicationLifecycle]
              configuration.get[String]("storage.provider") match {
                case "localfs"  => bind(classOf[StorageSrv]).to(classOf[LocalFileSystemStorageSrv])
                case "database" => bind(classOf[StorageSrv]).to(classOf[DatabaseStorageSrv])
                case "hdfs"     => bind(classOf[StorageSrv]).to(classOf[HadoopStorageSrv])
                case "s3"       => bind(classOf[StorageSrv]).to(classOf[S3StorageSrv])
              }
              ()
            }
          }).asJava
      )

  def runChecks(db: Database, configuration: Configuration)(implicit actorSystem: ActorSystem): Unit =
    buildApp(configuration, db).getInstance(classOf[IntegrityChecks]).runChecks()
}

class IntegrityChecks @Inject() (checks: immutable.Set[IntegrityCheck]) extends MapMerger {
  def runChecks(): Unit =
    checks.foreach { c =>
      println(s"Running check on ${c.name} ...")
      val desupStats = c match {
        case dc: DedupCheck[_] => dc.dedup(KillSwitch.alwaysOn)
        case _                 => Map.empty[String, Long]
      }
      val globalStats = c match {
        case gc: GlobalCheck[_] => gc.runGlobalCheck(24.hours, KillSwitch.alwaysOn)
        case _                  => Map.empty[String, Long]
      }
      val statsStr = (desupStats <+> globalStats)
        .collect {
          case (k, v) if v != 0 => s"$k:$v"
        }
        .mkString(" ")
      if (statsStr.isEmpty)
        println("  no change needed")
      else
        println(s"  $statsStr")

    }
}
