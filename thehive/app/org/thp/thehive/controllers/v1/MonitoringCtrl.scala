package org.thp.thehive.controllers.v1

import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.Permissions
import play.api.libs.json.{Format, JsArray, Json}
import play.api.mvc.{Action, AnyContent, Results}

import java.io.File
import javax.inject.{Inject, Singleton}
import scala.util.Success

@Singleton
class MonitoringCtrl @Inject() (
    appConfig: ApplicationConfig,
    entrypoint: Entrypoint,
    db: Database
) {
  case class PartitionConfig(location: String)

  object PartitionConfig {
    implicit val format: Format[PartitionConfig] = Json.format[PartitionConfig]
  }

  val diskLocations: ConfigItem[Seq[PartitionConfig], Seq[PartitionConfig]] =
    appConfig.item[Seq[PartitionConfig]]("monitor.disk", "disk locations to monitor")

  def monitorDiskUsage: Action[AnyContent] =
    entrypoint("monitor disk usage")
      .authPermittedTransaction(db, Permissions.managePlatform)(implicit request =>
        implicit graph =>
          for {
            _ <- Success(())
            locations =
              diskLocations
                .get
                .foldLeft[JsArray](JsArray.empty)((array, p) =>
                  array :+ Json.obj(
                    "location"   -> p.location,
                    "freeSpace"  -> new File(p.location).getFreeSpace,
                    "totalSpace" -> new File(p.location).getTotalSpace
                  )
                )
          } yield Results.Ok(locations)
      )

}
