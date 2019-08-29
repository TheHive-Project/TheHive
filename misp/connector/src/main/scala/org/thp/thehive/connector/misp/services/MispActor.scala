package org.thp.thehive.connector.misp.services

import java.nio.file.Files
import java.util.Date

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import play.api.Logger

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import gremlin.scala.{__, By, Key, P, Vertex}
import javax.inject.{Inject, Named, Provider}
import org.thp.misp.dto.{Attribute, Event, Tag}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.auth.{AuthContext, UserSrv}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.RichVertexGremlinScala
import org.thp.thehive.models._
import org.thp.thehive.services._

object MispActor {
  case object Synchro
  case class EndOfSynchro(status: Try[Unit])
}

class MispActor @Inject()(
    connector: Connector,
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

  lazy val logger = Logger(getClass)
  logger.info(s"[$self] Initialising actor MISP")

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"[$self] Starting actor MISP")
    context.become(waiting(context.system.scheduler.scheduleOnce(connector.syncInitialDelay, self, Synchro)))
  }

  override def receive: Receive = {
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
  }

  def running: Receive = {
    case Synchro => logger.info("MISP synchronisation is already in progress")
    case EndOfSynchro(Success(_)) =>
      logger.info(s"MISP synchronisation is complete")
      context.become(waiting(context.system.scheduler.scheduleOnce(connector.syncInterval, self, Synchro)))
    case EndOfSynchro(Failure(error)) =>
      logger.error(s"MISP synchronisation fails", error)
      context.become(waiting(context.system.scheduler.scheduleOnce(connector.syncInterval, self, Synchro)))
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
  }

  def waiting(scheduledSynchronisation: Cancellable): Receive = {
    case Synchro =>
      scheduledSynchronisation.cancel()
      context.become(running)
      logger.info(s"Synchronising MISP events for ${connector.clients.map(_.name).mkString(",")}")
      Future
        .traverse(connector.clients)(syncMispEvents(_)(userSrv.getSystemAuthContext))
        .map(_ => ())
        .onComplete(status => self ! EndOfSynchro(status))
    case other => logger.warn(s"Unknown message $other (${other.getClass})")
  }

  def eventToAlert(mispBaseUrl: String, mispOrganisation: String, event: Event): Alert = Alert(
    `type` = "misp",
    source = mispOrganisation,
    sourceRef = event.id,
    externalLink = Some(s"$mispBaseUrl/events/${event.id}"),
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

  def convertAttributeType(attributeCategory: String, attributeType: String): Try[(ObservableType with Entity, Seq[String])] = {
    val obsTypeFromConfig = connector
      .attributeConverter(attributeCategory, attributeType)
      .flatMap { attrConv =>
        db.roTransaction { implicit graph =>
          observableTypeSrv
            .get(attrConv.`type`)
            .headOption()
            .map(_ -> attrConv.tags)
        }
      }
    db.roTransaction { implicit graph =>
      obsTypeFromConfig
        .orElse(observableTypeSrv.get(attributeType).headOption().map(_ -> Nil))
        .fold(observableTypeSrv.getOrFail("other").map(_ -> Seq.empty[String]))(Success(_))
    }
  }

  def attributeToObservable(
      client: TheHiveMispClient,
      attribute: Attribute
  ): List[(Observable, ObservableType with Entity, Set[String], Either[String, Future[(String, String, Source[ByteString, _])]])] =
    attribute
      .`type`
      .split('|')
      .toSeq
      .toTry(convertAttributeType(attribute.category, _))
      .map {
        case observables if observables.exists(_._1.isAttachment != (attribute.`type` == "attachment" || attribute.`type` == "malware-sample")) =>
          logger.error(s"Attribute conversion return incompatible types (${attribute.`type`} / ${observables.map(_._1.name).mkString(",")}")
          Nil
        case Seq((observableType, additionalTags)) if observableType.isAttachment =>
          List(
            (
              Observable(attribute.comment, 0, ioc = false, sighted = false),
              observableType,
              attribute.tags.toSet ++ additionalTags,
              Right(client.downloadAttachment(attribute.id))
            )
          )
        case Seq((observableType, additionalTags)) if !observableType.isAttachment =>
          List(
            (
              Observable(attribute.comment, 0, ioc = false, sighted = false),
              observableType,
              attribute.tags.toSet ++ additionalTags,
              Left(attribute.value)
            )
          )
        case multipleObservables =>
          attribute
            .value
            .split('|')
            .toList
            .zip(multipleObservables)
            .map {
              case (value, (observableType, additionalTags)) =>
                (
                  Observable(attribute.comment, 0, ioc = false, sighted = false),
                  observableType,
                  attribute.tags.toSet ++ additionalTags,
                  Left(value)
                )
            }
      }
      .getOrElse {
        logger.warn(s"Don't know how to convert attribute $attribute")
        Nil
      }

  def getLastSyncDate(client: TheHiveMispClient, mispOrganisation: String, organisations: Seq[Organisation with Entity]): Option[Date] = {
    val lastOrgSynchro = db
      .roTransaction { implicit graph =>
        client
          .organisationFilter(organisationSrv.initSteps)
          .groupBy(
            By(),
            By(
              __[Vertex]
                .inTo[AlertOrganisation]
                .has(Key[String]("source"), P.eq[String](mispOrganisation))
                .has(Key[String]("type"), P.eq[String]("misp"))
                .value[Date]("date")
                .max[Date]
            )
          )
          .head()
      }
      .values()
      .asScala
      .toSeq
      .asInstanceOf[Seq[Date]]

    if (lastOrgSynchro.size == organisations.size) Some(lastOrgSynchro.min)
    else None
  }

  def updateOrCreateObservable(
      alert: Alert with Entity,
      observable: Observable,
      observableType: ObservableType with Entity,
      data: String,
      tags: Set[String]
  )(implicit authContext: AuthContext): Try[Observable with Entity] =
    db.tryTransaction { implicit graph =>
      alertSrv
        .get(alert)
        .observables
        .filterOnType(observableType.name)
        .filterOnData(data)
        .richObservable
        .headOption() match {
        case None =>
          for {
            richObservable <- observableSrv.create(observable, observableType, data, tags, Nil)
            _              <- alertSrv.addObservable(alert, richObservable.observable)
          } yield richObservable.observable
        case Some(richObservable) =>
          val updateFields = (if (richObservable.message != observable.message) Seq("message" -> observable.message) else Nil) ++
            (if (richObservable.tlp != observable.tlp) Seq("tlp"             -> observable.tlp) else Nil) ++
            (if (richObservable.ioc != observable.ioc) Seq("ioc"             -> observable.ioc) else Nil) ++
            (if (richObservable.sighted != observable.sighted) Seq("sighted" -> observable.sighted) else Nil)
          for { // update observable even if updateFields is empty in order to remove unupdated observables
            updatedObservable <- observableSrv.get(richObservable.observable).update(updateFields: _*)
            _                 <- observableSrv.updateTags(updatedObservable, tags)
          } yield updatedObservable
      }
    }

  def updateOrCreateObservable(
      alert: Alert with Entity,
      observable: Observable,
      observableType: ObservableType with Entity,
      filename: String,
      contentType: String,
      src: Source[ByteString, _],
      tags: Set[String]
  )(implicit authContext: AuthContext): Future[Observable with Entity] =
    db.roTransaction { implicit graph =>
      alertSrv
        .get(alert)
        .observables
        .filterOnType(observableType.name)
        .filterOnAttachmentName(filename)
        .filterOnAttachmentName(contentType)
        .richObservable
        .headOption()
    } match {
      case None =>
        val file = Files.createTempFile(s"misp-attachment-", "")
        (for {
          r <- src.runWith(FileIO.toPath(file))
          _ <- Future.fromTry(r.status)
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
        } yield createdObservable.observable)
          .andThen { case _ => Files.delete(file) }
      case Some(richObservable) =>
        val updateFields = (if (richObservable.message != observable.message) Seq("message" -> observable.message) else Nil) ++
          (if (richObservable.tlp != observable.tlp) Seq("tlp"             -> observable.tlp) else Nil) ++
          (if (richObservable.ioc != observable.ioc) Seq("ioc"             -> observable.ioc) else Nil) ++
          (if (richObservable.sighted != observable.sighted) Seq("sighted" -> observable.sighted) else Nil)
        Future.fromTry {
          db.tryTransaction { implicit graph =>
            for { // update observable even if updateFields is empty in order to remove unupdated observables
              updatedObservable <- observableSrv.get(richObservable.observable).update(updateFields: _*)
              _                 <- observableSrv.updateTags(updatedObservable, tags)
            } yield updatedObservable
          }
        }
    }

  def importAttibutes(client: TheHiveMispClient, event: Event, alert: Alert with Entity, lastSynchro: Option[Date])(
      implicit authContext: AuthContext
  ): Future[Unit] = {
    val startSyncDate = new Date
    client
      .searchAttributes(event.id, lastSynchro)
      .mapConcat(attributeToObservable(client, _))
      .runWith(Sink.foreachAsync(2) {
        case (observable, observableType, tags, Left(data)) =>
          updateOrCreateObservable(alert, observable, observableType, data, tags)
            .fold(error => {
              logger.error(s"Unable to create observable $observable ${observableType.name}:$data", error)
              Future.failed(error)
            }, _ => Future.successful(()))
        case (observable, observableType, tags, Right(attachment)) =>
          attachment
            .flatMap {
              case (filename, contentType, src) => updateOrCreateObservable(alert, observable, observableType, filename, contentType, src, tags)
            }
            .transform {
              case Success(_) => Success(())
              case Failure(error) =>
                logger.error(
                  s"Unable to create observable $observable ${observableType.name}:${attachment.value.flatMap(_.toOption).fold("???")(_._1)}",
                  error
                )
                Success(())
            }
      })
      .flatMap { _ =>
        Future.fromTry {
          logger.info("Removing old observables")
          db.tryTransaction { implicit graph =>
              alertSrv
                .get(alert)
                .observables
                .filter(
                  _.or(
                    _.has(Key[Date]("_updatedAt"), P.lt(startSyncDate)),
                    _.and(_.hasNot(Key("_updatedAt")), _.has(Key[Date]("_createdAt"), P.lt(startSyncDate)))
                  )
                )
                .toIterator
                .toTry { obs =>
                  logger.info(s"Remove $obs")
                  observableSrv.cascadeRemove(obs)
                }
            }
            .map(_ => ())
        }
      }
  }

  def updateOrCreateAlert(
      mispBaseUrl: String,
      organisation: Organisation with Entity,
      mispOrganisation: String,
      event: Event,
      caseTemplate: Option[RichCaseTemplate]
  )(
      implicit authContext: AuthContext
  ): Try[Alert with Entity] = {
    val alert = eventToAlert(mispBaseUrl, mispOrganisation, event)
    db.tryTransaction { implicit graph =>
      organisationSrv
        .get(organisation)
        .alerts
        .getBySourceId("misp", mispOrganisation, event.id)
        .richAlert
        .headOption() match {
        case None => // if the related alert doesn't exist, create it
          alertSrv
            .create(alert, organisation, event.tags.map(_.name).toSet, Map.empty, caseTemplate)
            .map(_.alert)
        case Some(richAlert) =>
          val updateFields = (if (richAlert.title != alert.title) Seq("title" -> alert.title) else Nil) ++
            (if (richAlert.description != alert.description) Seq("description" -> alert.description) else Nil) ++
            (if (richAlert.severity != alert.severity) Seq("severity"          -> alert.severity) else Nil) ++
            (if (richAlert.date != alert.date) Seq("date"                      -> alert.date) else Nil) ++
            (if (richAlert.flag != alert.flag) Seq("flag"                      -> alert.flag) else Nil) ++
            (if (richAlert.tlp != alert.tlp) Seq("tlp"                         -> alert.tlp) else Nil) ++
            (if (richAlert.pap != alert.pap) Seq("pap"                         -> alert.pap) else Nil)
          for {
            updatedAlert <- if (updateFields.nonEmpty) alertSrv.get(richAlert.alert).update(updateFields: _*) else Success(richAlert.alert)
            _            <- alertSrv.updateTags(updatedAlert, event.tags.map(_.name).toSet)
          } yield updatedAlert
      }
    }
  }

  def syncMispEvents(client: TheHiveMispClient)(implicit authContext: AuthContext): Future[Unit] =
    client.getCurrentOrganisationName.flatMap { mispOrganisation =>
      lazy val caseTemplate = client.caseTemplate.flatMap { caseTemplateName =>
        db.roTransaction { implicit graph =>
          caseTemplateSrv.get(caseTemplateName).richCaseTemplate.headOption()
        }
      }
      val organisations = db.roTransaction { implicit graph =>
        client.organisationFilter(organisationSrv.initSteps).toList
      }
      val lastSynchro = getLastSyncDate(client, mispOrganisation, organisations)
      logger.debug(s"Last synchronisation is $lastSynchro")
      client
        .searchEvents(publishDate = lastSynchro)
        .runWith(Sink.foreachAsync(2) { event =>
          Future
            .traverse(organisations) { organisation =>
              Future
                .fromTry(updateOrCreateAlert(client.baseUrl, organisation, mispOrganisation, event, caseTemplate))
                .flatMap(alert => importAttibutes(client, event, alert, lastSynchro))
                .recoverWith {
                  case error =>
                    logger.warn(s"Unable to create alert from MISP event ${client.name}#${event.id}", error)
                    Future.successful(())
                }
            }
            .map(_ => ())
        })
        .map(_ => ())
    }
}

class MispActorProvider @Inject()(system: ActorSystem, @Named("misp-actor-singleton") mispActorSingleton: ActorRef) extends Provider[ActorRef] {
  lazy val logger = Logger(getClass)
  override def get(): ActorRef = {
    val cluster = Cluster(system)
    logger.info("Initialising cluster")
    cluster.join(mispActorSingleton.path.address)
    logger.info(s"cluster members are ${cluster.state.members}")
    logger.info(s"Creating actor proxy for singleton $mispActorSingleton")
    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = mispActorSingleton.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(system)
      )
    )
  }
}
