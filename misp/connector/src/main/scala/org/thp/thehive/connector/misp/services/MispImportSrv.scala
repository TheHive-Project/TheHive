package org.thp.thehive.connector.misp.services

import java.nio.file.Files
import java.util.Date

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.misp.dto.{Attribute, Event, Tag => MispTag}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services._
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class MispImportSrv @Inject() (
    connector: Connector,
    alertSrv: AlertSrv,
    observableSrv: ObservableSrv,
    organisationSrv: OrganisationSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    caseTemplateSrv: CaseTemplateSrv,
    @Named("with-thehive-schema") db: Database,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  lazy val logger: Logger = Logger(getClass)

  def eventToAlert(client: TheHiveMispClient, event: Event): Try[Alert] =
    client
      .currentOrganisationName
      .map { mispOrganisation =>
        Alert(
          `type` = "misp",
          source = mispOrganisation,
          sourceRef = event.id,
          externalLink = Some(s"${client.strippedUrl}/events/${event.id}"),
          title = s"#${event.id} ${event.info.trim}",
          description = s"Imported from MISP Event #${event.id}, created at ${event.date}",
          severity = event.threatLevel.filter(l => 0 < l && l < 4).fold(2)(4 - _),
          date = event.date,
          lastSyncDate = event.publishDate,
          tlp = event
            .tags
            .collectFirst {
              case MispTag(_, "tlp:white", _, _) => 0
              case MispTag(_, "tlp:green", _, _) => 1
              case MispTag(_, "tlp:amber", _, _) => 2
              case MispTag(_, "tlp:red", _, _)   => 3
            }
            .getOrElse(2),
          pap = 2,
          read = false,
          follow = true
        )
      }

  def convertAttributeType(attributeCategory: String, attributeType: String): Try[(ObservableType with Entity, Seq[String])] = {
    val obsTypeFromConfig = connector
      .attributeConverter(attributeCategory, attributeType)
      .flatMap { attrConv =>
        db.roTransaction { implicit graph =>
          observableTypeSrv
            .get(attrConv.`type`)
            .headOption
            .map(_ -> attrConv.tags)
        }
      }
    db.roTransaction { implicit graph =>
      obsTypeFromConfig
        .orElse(observableTypeSrv.get(attributeType).headOption.map(_ -> Nil))
        .fold(observableTypeSrv.getOrFail("other").map(_ -> Seq.empty[String]))(Success(_))
    }
  }

  def attributeToObservable(
      attribute: Attribute
  ): List[(Observable, ObservableType with Entity, Set[String], Either[String, (String, String, Source[ByteString, _])])] =
    attribute
      .`type`
      .split('|')
      .toSeq
      .toTry(convertAttributeType(attribute.category, _))
      .map {
        case observables
            if observables.exists(o =>
              o._1.isAttachment != (attribute.`type` == "attachment" ||
                attribute.`type` == "malware-sample") ||
                o._1.isAttachment == attribute.data.isEmpty
            ) =>
          logger.error(s"Attribute conversion return incompatible types (${attribute.`type`} / ${observables.map(_._1.name).mkString(",")}")
          Nil
        case Seq((observableType, additionalTags)) if observableType.isAttachment =>
          logger.debug(
            s"attribute ${attribute.category}:${attribute.`type`} (${attribute.tags}) is converted to observable $observableType with tags $additionalTags"
          )
          List(
            (
              Observable(attribute.comment, 0, ioc = false, sighted = false),
              observableType,
              attribute.tags.map(_.name).toSet ++ additionalTags,
              Right(attribute.data.get)
            )
          )
        case Seq((observableType, additionalTags)) if !observableType.isAttachment =>
          logger.debug(
            s"attribute ${attribute.category}:${attribute.`type`} (${attribute.tags}) is converted to observable $observableType with tags $additionalTags"
          )
          List(
            (
              Observable(attribute.comment, 0, ioc = false, sighted = false),
              observableType,
              attribute.tags.map(_.name).toSet ++ additionalTags,
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
                logger.debug(
                  s"attribute ${attribute.category}:${attribute.`type`} (${attribute.tags}) is converted to observable $observableType with tags $additionalTags"
                )
                (
                  Observable(attribute.comment, 0, ioc = false, sighted = false),
                  observableType,
                  attribute.tags.map(_.name).toSet ++ additionalTags,
                  Left(value)
                )
            }
      }
      .getOrElse {
        logger.warn(s"Don't know how to convert attribute $attribute")
        Nil
      }

  def getLastSyncDate(client: TheHiveMispClient, mispOrganisation: String, organisations: Seq[Organisation with Entity]): Option[Date] = {
    val lastOrgSynchro = db.roTransaction { implicit graph =>
      client
        .organisationFilter(organisationSrv.startTraversal)
        .group(
          _.by,
          _.by(
            _.in[AlertOrganisation]
              .v[Alert]
              .has("source", mispOrganisation)
              .has("type", "misp")
              .value(_.lastSyncDate)
              .max
          )
        )
        .head
    }.values
//      .asInstanceOf[Seq[Date]]

    if (lastOrgSynchro.size == organisations.size && organisations.nonEmpty) Some(lastOrgSynchro.min)
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
        .headOption match {
        case None =>
          logger.debug(s"Observable ${observableType.name}:$data doesn't exist, create it")
          for {
            richObservable <- observableSrv.create(observable, observableType, data, tags, Nil)
            _              <- alertSrv.addObservable(alert, richObservable)
          } yield richObservable.observable
        case Some(richObservable) =>
          logger.debug(s"Observable ${observableType.name}:$data exists, update it")
          for {
            updatedObservable <- Some(observableSrv.get(richObservable.observable))
              .map(t => if (richObservable.message != observable.message) t.update(_.message, observable.message) else t)
              .map(t => if (richObservable.tlp != observable.tlp) t.update(_.tlp, observable.tlp) else t)
              .map(t => if (richObservable.ioc != observable.ioc) t.update(_.ioc, observable.ioc) else t)
              .map(t => if (richObservable.sighted != observable.sighted) t.update(_.sighted, observable.sighted) else t)
              .get
              .getOrFail("Observable")
            _ <- observableSrv.updateTagNames(updatedObservable, tags)
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
        .headOption
    } match {
      case None =>
        logger.debug(s"Observable ${observableType.name}:$filename:$contentType doesn't exist, create it")
        val file = Files.createTempFile("misp-attachment-", "")
        (for {
          _ <- src.runWith(FileIO.toPath(file))
          fFile = FFile(filename, file, contentType)
          createdObservable <- Future.fromTry {
            db.tryTransaction { implicit graph =>
              for {
                createdAttachment <- attachmentSrv.create(fFile)
                richObservable    <- observableSrv.create(observable, observableType, createdAttachment, tags, Nil)
                _                 <- alertSrv.addObservable(alert, richObservable)
              } yield richObservable
            }
          }
        } yield createdObservable.observable)
          .andThen { case _ => Files.delete(file) }
      case Some(richObservable) =>
        logger.debug(s"Observable ${observableType.name}:$filename:$contentType exists, update it")
        Future.fromTry {
          db.tryTransaction { implicit graph =>
            for {
              updatedObservable <- Some(observableSrv.get(richObservable.observable))
                .map(t => if (richObservable.message != observable.message) t.update(_.message, observable.message) else t)
                .map(t => if (richObservable.tlp != observable.tlp) t.update(_.tlp, observable.tlp) else t)
                .map(t => if (richObservable.ioc != observable.ioc) t.update(_.ioc, observable.ioc) else t)
                .map(t => if (richObservable.sighted != observable.sighted) t.update(_.sighted, observable.sighted) else t)
                .get
                .getOrFail("Observable")
              _ <- observableSrv.updateTagNames(updatedObservable, tags)
            } yield updatedObservable
          }
        }
    }

  def importAttibutes(client: TheHiveMispClient, event: Event, alert: Alert with Entity, lastSynchro: Option[Date])(
      implicit authContext: AuthContext
  ): Future[Unit] = {
    logger.debug(s"importAttibutes ${client.name}#${event.id}")
    val startSyncDate = new Date
    client
      .searchAttributes(event.id, lastSynchro)
      .mapConcat(attributeToObservable)
      .fold(
        Map.empty[
          (String, String),
          (Observable, ObservableType with Entity, Set[String], Either[String, (String, String, Source[ByteString, _])])
        ]
      ) {
        case (distinctMap, data @ (_, t, _, Left(d)))          => distinctMap + ((t.name, d) -> data)
        case (distinctMap, data @ (_, t, _, Right((n, _, _)))) => distinctMap + ((t.name, n) -> data)
      }
      .mapConcat { m =>
        m.values.toList
      }
      .runWith(Sink.foreachAsync(1) {
        case (observable, observableType, tags, Left(data)) =>
          updateOrCreateObservable(alert, observable, observableType, data, tags)
            .fold(error => {
              logger.error(s"Unable to create observable $observable ${observableType.name}:$data", error)
              Future.failed(error)
            }, _ => Future.successful(()))
        case (observable, observableType, tags, Right((filename, contentType, src))) =>
          updateOrCreateObservable(alert, observable, observableType, filename, contentType, src, tags)
            .transform {
              case Success(_) => Success(())
              case Failure(error) =>
                logger.error(
                  s"Unable to create observable $observable ${observableType.name}:$filename",
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
                    _.has("_updatedAt", P.lt(startSyncDate)),
                    _.and(_.hasNot("_updatedAt"), _.has("_createdAt", P.lt(startSyncDate)))
                  )
                )
                .toIterator
                .toTry { obs =>
                  logger.info(s"Remove $obs")
                  observableSrv.remove(obs)
                }
            }
            .map(_ => ())
        }
      }
  }

//  def convertTag(mispTag: MispTag): Tag = tagSrv.parseString(mispTag.name + mispTag.colour.fold("")(c => f"#$c%06X"))

  def updateOrCreateAlert(
      client: TheHiveMispClient,
      organisation: Organisation with Entity,
      mispOrganisation: String,
      event: Event,
      caseTemplate: Option[CaseTemplate with Entity]
  )(
      implicit authContext: AuthContext
  ): Try[Alert with Entity] = {
    logger.debug(s"updateOrCreateAlert ${client.name}#${event.id} for organisation ${organisation.name}")
    eventToAlert(client, event).flatMap { alert =>
      db.tryTransaction { implicit graph =>
        organisationSrv
          .get(organisation)
          .alerts
          .getBySourceId("misp", mispOrganisation, event.id)
          .richAlert
          .headOption match {
          case None => // if the related alert doesn't exist, create it
            logger.debug(s"Event ${client.name}#${event.id} has no related alert for organisation ${organisation.name}")
            alertSrv
              .create(alert, organisation, event.tags.map(_.name).toSet, Map.empty[String, Option[Any]], caseTemplate)
              .map(_.alert)
          case someAlert @ Some(richAlert) =>
            logger.debug(s"Event ${client.name}#${event.id} have already been imported for organisation ${organisation.name}, updating the alert")
            for {
              updatedAlert <- Some(alertSrv.get(richAlert.alert))
                .map(t => if (richAlert.title != alert.title) t.update(_.title, alert.title) else t)
                .map(t => if (richAlert.lastSyncDate != alert.lastSyncDate) t.update(_.lastSyncDate, alert.lastSyncDate) else t)
                .map(t => if (richAlert.description != alert.description) t.update(_.description, alert.description) else t)
                .map(t => if (richAlert.severity != alert.severity) t.update(_.severity, alert.severity) else t)
                .map(t => if (richAlert.date != alert.date) t.update(_.date, alert.date) else t)
                .map(t => if (richAlert.tlp != alert.tlp) t.update(_.tlp, alert.tlp) else t)
                .map(t => if (richAlert.pap != alert.pap) t.update(_.pap, alert.pap) else t)
                .map(t => if (richAlert.externalLink != alert.externalLink) t.update(_.externalLink, alert.externalLink) else t)
                .get
                .getOrFail("Alert")
              _ <- alertSrv.updateTagNames(updatedAlert, event.tags.map(_.name).toSet)
            } yield updatedAlert
        }
      }
    }
  }

  def syncMispEvents(client: TheHiveMispClient)(implicit authContext: AuthContext): Future[Unit] =
    Future.fromTry(client.currentOrganisationName).flatMap { mispOrganisation =>
      lazy val caseTemplate = client.caseTemplate.flatMap { caseTemplateName =>
        db.roTransaction { implicit graph =>
          caseTemplateSrv.get(caseTemplateName).headOption
        }
      }
      logger.debug(s"Get eligible organisations")
      val organisations = db.roTransaction { implicit graph =>
        client.organisationFilter(organisationSrv.startTraversal).toSeq
      }
      val lastSynchro = getLastSyncDate(client, mispOrganisation, organisations)
      logger.debug(s"Last synchronisation is $lastSynchro")
      client
        .searchEvents(publishDate = lastSynchro)
        .runWith(Sink.foreachAsync(1) { event =>
          logger.debug(s"Importing event ${client.name}#${event.id} in organisation(s): ${organisations.mkString(",")}")
          Future
            .traverse(organisations) { organisation =>
              Future
                .fromTry(updateOrCreateAlert(client, organisation, mispOrganisation, event, caseTemplate))
                .flatMap(alert => importAttibutes(client, event, alert, lastSynchro))
                .recover {
                  case error =>
                    logger.warn(s"Unable to create alert from MISP event ${client.name}#${event.id}", error)
                }
            }
            .map(_ => ())
            .recover {
              case error =>
                logger.warn(s"Unable to create alert from MISP event ${client.name}#${event.id}", error)
            }
        })
        .map(_ => ())
    }
}
