package org.thp.thehive.connector.misp.services

import java.nio.file.Files
import java.util.Date

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import gremlin.scala.{__, By, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.misp.dto.{Attribute, Event, Tag => MispTag}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.RichVertexGremlinScala
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class MispImportSrv @Inject()(
    connector: Connector,
    alertSrv: AlertSrv,
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv,
    organisationSrv: OrganisationSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    caseTemplateSrv: CaseTemplateSrv,
    tagSrv: TagSrv,
    db: Database,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  lazy val logger = Logger(getClass)

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
          flag = false, // TODO need check
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
  ): List[(Observable, ObservableType with Entity, Set[String], Either[String, (String, String, Source[ByteString, _])])] =
    attribute
      .`type`
      .split('|')
      .toSeq
      .toTry(convertAttributeType(attribute.category, _))
      .map {
        case observables
            if observables.exists(
              o =>
                o._1.isAttachment != (attribute.`type` == "attachment" || attribute.`type` == "malware-sample") || o
                  ._1
                  .isAttachment == attribute.data.isEmpty
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
                .value[Date]("lastSyncDate")
                .max[Date]
            )
          )
          .head()
      }
      .values()
      .asScala
      .toSeq
      .asInstanceOf[Seq[Date]]

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
        .headOption() match {
        case None =>
          logger.debug(s"Observable ${observableType.name}:$data doesn't exist, create it")
          for {
            richObservable <- observableSrv.create(observable, observableType, data, tags, Nil)
            _              <- alertSrv.addObservable(alert, richObservable)
          } yield richObservable.observable
        case Some(richObservable) =>
          logger.debug(s"Observable ${observableType.name}:$data exists, update it")
          val updateFields = (if (richObservable.message != observable.message) Seq("message" -> observable.message) else Nil) ++
            (if (richObservable.tlp != observable.tlp) Seq("tlp"             -> observable.tlp) else Nil) ++
            (if (richObservable.ioc != observable.ioc) Seq("ioc"             -> observable.ioc) else Nil) ++
            (if (richObservable.sighted != observable.sighted) Seq("sighted" -> observable.sighted) else Nil)
          for { // update observable even if updateFields is empty in order to remove unupdated observables
            updatedObservable <- observableSrv.get(richObservable.observable).update(updateFields: _*)
            _                 <- observableSrv.updateTagNames(updatedObservable, tags)
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
        logger.debug(s"Observable ${observableType.name}:$filename:$contentType doesn't exist, create it")
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
                _                 <- alertSrv.addObservable(alert, richObservable)
              } yield richObservable
            }
          }
        } yield createdObservable.observable)
          .andThen { case _ => Files.delete(file) }
      case Some(richObservable) =>
        logger.debug(s"Observable ${observableType.name}:$filename:$contentType exists, update it")
        val updateFields = (if (richObservable.message != observable.message) Seq("message" -> observable.message) else Nil) ++
          (if (richObservable.tlp != observable.tlp) Seq("tlp"             -> observable.tlp) else Nil) ++
          (if (richObservable.ioc != observable.ioc) Seq("ioc"             -> observable.ioc) else Nil) ++
          (if (richObservable.sighted != observable.sighted) Seq("sighted" -> observable.sighted) else Nil)
        Future.fromTry {
          db.tryTransaction { implicit graph =>
            for { // update observable even if updateFields is empty in order to remove unupdated observables
              updatedObservable <- observableSrv.get(richObservable.observable).update(updateFields: _*)
              _                 <- observableSrv.updateTagNames(updatedObservable, tags)
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
      .mapConcat(attributeToObservable(client, _))
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

//  def convertTag(mispTag: MispTag): Tag = tagSrv.parseString(mispTag.name + mispTag.colour.fold("")(c => f"#$c%06X"))

  def updateOrCreateAlert(
      client: TheHiveMispClient,
      organisation: Organisation with Entity,
      mispOrganisation: String,
      event: Event,
      caseTemplate: Option[RichCaseTemplate]
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
          .headOption() match {
          case None => // if the related alert doesn't exist, create it
            logger.debug(s"Event ${client.name}#${event.id} has no related alert for organisation ${organisation.name}")
            alertSrv
              .create(alert, organisation, event.tags.map(_.name).toSet, Map.empty, caseTemplate)
              .map(_.alert)
          case Some(richAlert) =>
            logger.debug(s"Event ${client.name}#${event.id} have already been imported for organisation ${organisation.name}, updating the alert")
            val updateFields = (if (richAlert.title != alert.title) Seq("title" -> alert.title) else Nil) ++
              (if (richAlert.lastSyncDate != alert.lastSyncDate) Seq("lastSyncDate" -> alert.lastSyncDate) else Nil) ++
              (if (richAlert.description != alert.description) Seq("description"    -> alert.description) else Nil) ++
              (if (richAlert.severity != alert.severity) Seq("severity"             -> alert.severity) else Nil) ++
              (if (richAlert.date != alert.date) Seq("date"                         -> alert.date) else Nil) ++
              (if (richAlert.flag != alert.flag) Seq("flag"                         -> alert.flag) else Nil) ++
              (if (richAlert.tlp != alert.tlp) Seq("tlp"                            -> alert.tlp) else Nil) ++
              (if (richAlert.pap != alert.pap) Seq("pap"                            -> alert.pap) else Nil) ++
              (if (richAlert.externalLink != alert.externalLink) Seq("externalLink" -> alert.externalLink) else Nil)
            for {
              updatedAlert <- if (updateFields.nonEmpty) alertSrv.get(richAlert.alert).update(updateFields: _*) else Success(richAlert.alert)
              _            <- alertSrv.updateTagNames(updatedAlert, event.tags.map(_.name).toSet)
            } yield updatedAlert
        }
      }
    }
  }

  def syncMispEvents(client: TheHiveMispClient)(implicit authContext: AuthContext): Future[Unit] =
    Future.fromTry(client.currentOrganisationName).flatMap { mispOrganisation =>
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
          logger.debug(s"Importing event ${client.name}#${event.id}")
          Future
            .traverse(organisations) { organisation =>
              Future
                .fromTry(updateOrCreateAlert(client, organisation, mispOrganisation, event, caseTemplate))
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
