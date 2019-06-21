package org.thp.thehive.migration

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{search, RichString}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Database
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv

import org.elastic4play.database.DBFind

@Singleton
class DBListMigration @Inject()(customFieldSrv: CustomFieldSrv, dbFind: DBFind, userMigration: UserMigration, implicit val mat: Materializer)
    extends Utils {

  implicit val customFieldReads: Reads[CustomField] =
    ((JsPath \ "name").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "type").readWithDefault[String]("number").map {
        case "string"  ⇒ CustomFieldString
        case "number"  ⇒ CustomFieldInteger
        case "date"    ⇒ CustomFieldDate
        case "boolean" ⇒ CustomFieldBoolean
        //        case "float" => CustomFieldFloat
      })(CustomField.apply _)

  def importDBLists(terminal: Terminal)(implicit db: Database): Unit = {
    val (srv, total) = dbFind(Some("all"), Nil)(index ⇒ search(index / "dblist"))
    val progress     = new ProgressBar(terminal, "Importing customField/metric", Await.result(total, Duration.Inf).toInt)
    val done = srv
      .map { dblist ⇒
        db.transaction { implicit graph ⇒
          catchError("DBList", dblist, progress) {
            for {
              tpe      ← (dblist \ "dblist").asOpt[String]
              valueStr ← (dblist \ "value").asOpt[String]
              createdBy = (dblist \ "createdBy").asOpt[String].getOrElse("init")
              value     = Json.parse(valueStr)
            } yield userMigration.withUser(createdBy) { implicit authContext ⇒
              tpe match {
                case "case_metrics" | "custom_fields" ⇒
                  progress.inc(extraMessage = (value \ "name").asOpt[String].getOrElse("***"))
                  customFieldSrv.create(value.as[CustomField])
                case "list_artifactDataType" ⇒
                  progress.inc()
                // TODO ?
              }
            }
            ()
          }
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }
}
