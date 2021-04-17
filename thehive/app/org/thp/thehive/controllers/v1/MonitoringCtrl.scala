package org.thp.thehive.controllers.v1

import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.Permissions
import play.api.libs.json.{Format, JsArray, Json}
import play.api.mvc.{Action, AnyContent, Results}

import java.io.File
import scala.util.Success

class MonitoringCtrl(
    appConfig: ApplicationConfig,
    entrypoint: Entrypoint,
    db: Database
) {
  case class PartitionConfig(location: String)

  object PartitionConfig {
    implicit val format: Format[PartitionConfig] = Json.format[PartitionConfig]
  }

  val diskLocationsConfig: ConfigItem[Seq[PartitionConfig], Seq[PartitionConfig]] =
    appConfig.item[Seq[PartitionConfig]]("monitor.disk", "disk locations to monitor")
  def diskLocations: Seq[PartitionConfig] = diskLocationsConfig.get

  def diskUsage: Action[AnyContent] =
    entrypoint("monitor disk usage")
      .authPermitted(Permissions.managePlatform)(_ =>
        for {
          _ <- Success(())
          locations = diskLocations.map { dl =>
            val file = new File(dl.location)
            Json.obj(
              "location"   -> dl.location,
              "freeSpace"  -> file.getFreeSpace,
              "totalSpace" -> file.getTotalSpace
            )
          }
        } yield Results.Ok(JsArray(locations))
      )

}
