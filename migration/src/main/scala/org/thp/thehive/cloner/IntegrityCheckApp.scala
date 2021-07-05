package org.thp.thehive.cloner

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.{Guice, Injector => GInjector}
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.auth.{AuthContext, UserSrv => UserDB}
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
import scala.util.Success

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
              bindTypedActor(CaseNumberActor.behavior, "case-number-actor")

              val integrityCheckOpsBindings = ScalaMultibinder.newSetBinder[GenIntegrityCheckOps](binder)
              integrityCheckOpsBindings.addBinding.to[ProfileIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[OrganisationIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[TagIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[UserIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[ImpactStatusIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[ResolutionStatusIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[ObservableTypeIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[CustomFieldIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[CaseTemplateIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[DataIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[CaseIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[AlertIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[TaskIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[ObservableIntegrityCheckOps]
              integrityCheckOpsBindings.addBinding.to[LogIntegrityCheckOps]

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

class IntegrityChecks @Inject() (db: Database, checks: immutable.Set[GenIntegrityCheckOps], userSrv: UserDB) extends MapMerger {
  def runChecks(): Unit = {
    implicit val authContext: AuthContext = userSrv.getSystemAuthContext
    checks.foreach { c =>
      db.tryTransaction { implicit graph =>
        println(s"Running check on ${c.name} ...")
        c.initialCheck()
        val stats = c.duplicationCheck() <+> c.globalCheck()
        val statsStr = stats
          .collect {
            case (k, v) if v != 0 => s"$k:$v"
          }
          .mkString(" ")
        if (statsStr.isEmpty)
          println("  no change needed")
        else
          println(s"  $statsStr")
        Success(())
      }
    }
  }
}
