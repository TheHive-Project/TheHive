package org.thp.thehive.connector.misp.servives

import java.nio.file.Files
import java.util.Date

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

import play.api.Logger

import akka.actor.Actor
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import gremlin.scala.{__, By, Key, P, Vertex}
import javax.inject.Inject
import org.thp.misp.dto.{Attribute, Event, Tag}
import org.thp.scalligraph.auth.{AuthContext, UserSrv}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.RichVertexGremlinScala
import org.thp.thehive.models._
import org.thp.thehive.services._

object MispActor {
  case object Synchro
}

class MispActor @Inject()(
    mispConfig: MispConfig,
    userSrv: UserSrv,
    alertSrv: AlertSrv,
    organisationSrv: OrganisationSrv,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseTemplateSrv: CaseTemplateSrv,
    attachmentSrv: AttachmentSrv,
    implicit val db: Database,
    implicit val mat: Materializer
) extends Actor {
  import MispActor._
  import context.dispatcher

  lazy val logger       = Logger(getClass)
  var synchroInProgress = false

  override def preStart(): Unit = {
    super.preStart()
    context.system.scheduler.schedule(0.minute, mispConfig.interval, self, Synchro)
    ()
  }

  def eventToAlert(source: String, event: Event): Alert = Alert(
    `type` = "misp",
    source = source,
    sourceRef = event.id,
    title = s"#${event.id} ${event.info.trim}",
    description = s"Imported from MISP Event #${event.id}, created at ${event.date}",
    severity = event.threatLevel.filter(l => 0 < l && l < 4).fold(2)(4 - _),
    date = event.date,
    lastSyncDate = event.publishDate,
    flag = false, // TODO need check
    tlp = event
      .tags
      .collectFirst {
        case Tag(_, "tlp:white", _, _) => 0
        case Tag(_, "tlp:green", _, _) => 1
        case Tag(_, "tlp:amber", _, _) => 2
        case Tag(_, "tlp:red", _, _)   => 3
      }
      .getOrElse(2),
    pap = 2,
    read = false,
    follow = true
  )

  def attributeToObservable(
      client: TheHiveMispClient,
      attribute: Attribute
  ): Seq[(Observable, ObservableType with Entity, Set[String], Either[String, Future[(String, String, Source[ByteString, _])]])] = {
    val tags = Seq(s"MISP:type=${attribute.`type`}", s"MISP:category=${attribute.category}")
    db.roTransaction { implicit graph =>
        observableTypeSrv
          .getOrFail(attribute.`type`) // FIXME add configurable conversion
      }
      .map {
        case observableType if observableType.isAttachment =>
          Seq(
            (
              Observable(attribute.comment, 0, ioc = false, sighted = false),
              observableType,
              attribute.tags.toSet,
              Right(client.downloadAttachment(attribute.id))
            )
          )
        case observableType =>
          Seq(
            (
              Observable(attribute.comment, 0, ioc = false, sighted = false),
              observableType,
              attribute.tags.toSet,
              Left(attribute.value)
            )
          )
      }
      .getOrElse {
        // TODO add log
        Nil
      }
  }

  def getLastSyncDate(client: TheHiveMispClient, organisations: Seq[Organisation with Entity]): Option[Date] = {
    val lastOrgSynchro = db
      .roTransaction { implicit graph =>
        client
          .organisationFilter(organisationSrv.initSteps)
          .groupBy(
            By(),
            By(
              __[Vertex]
                .inTo[AlertOrganisation]
                .has(Key[String]("source"), P.eq[String](client.name)) // TODO fetch org of MISP server and use it as reference (instead of configured name)
                .has(Key[String]("type"), P.eq[String]("misp"))
                .value[Date]("date")
                .max[Date]
            )
          )
          .head()
      }
      .values()
      .asScala
      .flatMap(_.asScala)
      .toSeq

    if (lastOrgSynchro.size == organisations.size) Some(lastOrgSynchro.min)
    else None
  }

  def importAttibutes(client: TheHiveMispClient, event: Event, alert: Alert with Entity, lastSynchro: Option[Date])(
      implicit authContext: AuthContext
  ): Future[Seq[RichObservable]] =
    client.searchAttributes(event.id, lastSynchro).flatMap { attributes =>
      Future.traverse(attributes.flatMap(attributeToObservable(client, _))) {
        case (observable, observableType, tags, Left(data)) =>
          Future.fromTry {
            db.tryTransaction { implicit graph =>
              observableSrv.create(observable, observableType, data, tags, Nil)
            }
          }
        case (observable, observableType, tags, Right(attachment)) =>
          val file = Files.createTempFile(s"misp-attachment-", "")
          for {
            (filename, contentType, src) <- attachment
            r                            <- src.runWith(FileIO.toPath(file))
            _                            <- Future.fromTry(r.status)
            fFile = FFile(filename, file, contentType)
            createdObservable <- Future.fromTry {
              db.tryTransaction { implicit graph =>
                for {
                  createdAttachment <- attachmentSrv.create(fFile)
                  richObservable    <- observableSrv.create(observable, observableType, createdAttachment, tags, Nil)
                  _                 <- alertSrv.addObservable(alert, richObservable.observable)
                } yield richObservable
              }
            }
            _ = Files.delete(file) // FIXME file won't be deleted if attachment creation fails
          } yield createdObservable
      }
    }

  override def receive: Receive = {
    case Synchro if synchroInProgress => logger.info("MISP synchronisation is already in progress")
    case Synchro if !synchroInProgress =>
      implicit val authContext: AuthContext = userSrv.getSystemAuthContext
      synchroInProgress = true

      Future
        .traverse(mispConfig.connections.values) { client =>
          val organisations = db.roTransaction { implicit graph =>
            client.organisationFilter(organisationSrv.initSteps).toList
          }

          lazy val caseTemplate = client.caseTemplate.flatMap { caseTemplateName =>
            db.roTransaction { implicit graph =>
              caseTemplateSrv.get(caseTemplateName).richCaseTemplate.headOption()
            }
          }
          val lastSynchro = getLastSyncDate(client, organisations)

          client.searchEvents(publishDate = lastSynchro).flatMap { events =>
            Future.traverse(events) { event =>
              Future
                .traverse(organisations) { organisation =>
                  Future
                    .fromTry {
                      db.tryTransaction { implicit graph =>
                        organisationSrv
                          .get(organisation)
                          .alerts
                          .getBySourceId("misp", client.name, event.id)
                          .headOption()
                          .fold { // if the related alert doesn't exist, create it
                            alertSrv
                              .create(eventToAlert(client.name, event), organisation, event.tags.map(_.name).toSet, Map.empty, caseTemplate)
                              .map(_.alert)
                          }(Success.apply)
                      }
                    }
                    .flatMap { alert =>
                      importAttibutes(client, event, alert, lastSynchro)
                    }
                    .recoverWith {
                      case error =>
                        logger.warn(s"Unable to create alert from MISP event ${client.name}#${event.id}", error)
                        Future.failed(error)
                    }
                }
                .map(_.flatten)
            }
          }
        }
        .onComplete(_ => synchroInProgress = false)
  }
}
