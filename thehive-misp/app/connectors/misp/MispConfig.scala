package connectors.misp

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.Try

import play.api.{Configuration, Logger}

import com.typesafe.config.ConfigMemorySize
import services.CustomWSAPI

@Singleton
class MispConfig(val interval: FiniteDuration, val connections: Seq[MispConnection]) {

  def this(configuration: Configuration, defaultCaseTemplate: Option[String], globalWS: CustomWSAPI) =
    this(
      configuration.getOptional[FiniteDuration]("misp.interval").getOrElse(1.hour),
      for {
        cfg <- configuration.getOptional[Configuration]("misp").toSeq
        mispWS = globalWS.withConfig(cfg)

        defaultArtifactTags = cfg.getOptional[Seq[String]]("tags").getOrElse(Nil)
        name <- cfg.subKeys

        mispConnectionConfig <- Try(cfg.get[Configuration](name)).toOption.toSeq
        url                  <- mispConnectionConfig.getOptional[String]("url")
        key                  <- mispConnectionConfig.getOptional[String]("key")
        instanceWS            = mispWS.withConfig(mispConnectionConfig)
        artifactTags          = mispConnectionConfig.getOptional[Seq[String]]("tags").getOrElse(defaultArtifactTags)
        caseTemplate          = mispConnectionConfig.getOptional[String]("caseTemplate").orElse(defaultCaseTemplate)
        maxAge                = mispConnectionConfig.getOptional[Duration]("max-age")
        maxAttributes         = mispConnectionConfig.getOptional[Int]("max-attributes")
        maxSize               = mispConnectionConfig.getOptional[ConfigMemorySize]("max-size").map(_.toBytes)
        excludedOrganisations = mispConnectionConfig.getOptional[Seq[String]]("exclusion.organisation").getOrElse(Nil)
        excludedTags          = mispConnectionConfig.getOptional[Seq[String]]("exclusion.tags").fold(Set.empty[String])(_.toSet)
        whitelistTags         = mispConnectionConfig.getOptional[Seq[String]]("whitelist.tags").fold(Set.empty[String])(_.toSet)
        purpose = mispConnectionConfig
          .getOptional[String]("purpose")
          .fold(MispPurpose.ImportAndExport) { purposeName =>
            Try(MispPurpose.withName(purposeName)).getOrElse {
              Logger(getClass).error(
                s"Incorrect value for MISP purpose ($name.purpose), one of (${MispPurpose.values.mkString(", ")}) was expected. Using default value: ImportAndExport "
              )
              MispPurpose.ImportAndExport
            }
          }
        exportCaseTags = mispConnectionConfig.getOptional[Boolean]("exportCaseTags").contains(true)
      } yield MispConnection(
        name,
        url,
        key,
        instanceWS,
        caseTemplate,
        artifactTags,
        maxAge,
        maxAttributes,
        maxSize,
        excludedOrganisations,
        excludedTags,
        whitelistTags,
        purpose,
        exportCaseTags
      )
    )

  @Inject def this(configuration: Configuration, httpSrv: CustomWSAPI) =
    this(configuration, configuration.getOptional[String]("misp.caseTemplate"), httpSrv)

  def getConnection(name: String): Option[MispConnection] = connections.find(_.name == name)
}
