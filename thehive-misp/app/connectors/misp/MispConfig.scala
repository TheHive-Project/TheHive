package connectors.misp

import javax.inject.{ Inject, Singleton }

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.Try

import play.api.Configuration

import services.CustomWSAPI

@Singleton
class MispConfig(val interval: FiniteDuration, val connections: Seq[MispConnection]) {

  def this(configuration: Configuration, defaultCaseTemplate: Option[String], globalWS: CustomWSAPI) = this(
    configuration.getOptional[FiniteDuration]("misp.interval").getOrElse(1.hour),

    for {
      cfg ← configuration.getOptional[Configuration]("misp").toSeq
      mispWS = globalWS.withConfig(cfg)

      defaultArtifactTags = cfg.getOptional[Seq[String]]("tags").getOrElse(Nil)
      name ← cfg.subKeys

      mispConnectionConfig ← Try(cfg.get[Configuration](name)).toOption.toSeq
      url ← mispConnectionConfig.getOptional[String]("url")
      key ← mispConnectionConfig.getOptional[String]("key")
      instanceWS = mispWS.withConfig(mispConnectionConfig)
      artifactTags = mispConnectionConfig.getOptional[Seq[String]]("tags").getOrElse(defaultArtifactTags)
      caseTemplate = mispConnectionConfig.getOptional[String]("caseTemplate").orElse(defaultCaseTemplate)
    } yield MispConnection(name, url, key, instanceWS, caseTemplate, artifactTags))

  @Inject def this(configuration: Configuration, httpSrv: CustomWSAPI) =
    this(
      configuration,
      configuration.getOptional[String]("misp.caseTemplate"),
      httpSrv)

  def getConnection(name: String): Option[MispConnection] = connections.find(_.name == name)
}
