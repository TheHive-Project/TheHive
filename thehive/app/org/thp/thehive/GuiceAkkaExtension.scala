package org.thp.thehive

import akka.actor.{ActorSystem, ClassicActorSystemProvider, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.google.inject.Injector

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}

class GuiceAkkaExtension extends Extension {
  private val injectorPromise = Promise[Injector]
  def set(injector: Injector): Unit = {
    injectorPromise.success(injector)
    ()
  }
  lazy val injector: Injector = Await.result(injectorPromise.future, 1.minute)
}

object GuiceAkkaExtension extends ExtensionId[GuiceAkkaExtension] with ExtensionIdProvider {
  override def lookup: ExtensionId[GuiceAkkaExtension]      = GuiceAkkaExtension
  override def createExtension(system: ExtendedActorSystem) = new GuiceAkkaExtension

  /**
    * Java API: retrieve the Count extension for the given system.
    */
  override def get(system: ActorSystem): GuiceAkkaExtension                = super.get(system)
  override def get(system: ClassicActorSystemProvider): GuiceAkkaExtension = super.get(system)
}
