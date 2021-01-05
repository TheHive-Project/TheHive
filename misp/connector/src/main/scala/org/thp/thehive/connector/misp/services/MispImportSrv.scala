package org.thp.thehive.connector.misp.services

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.misp.dto.{Attribute, Event, Tag => MispTag}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.FunctionalCondition._
import org.thp.scalligraph.{EntityId, EntityName, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json._

import java.nio.file.Files
import java.util.Date
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
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
    db: Database,
    auditSrv: AuditSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  lazy val logger: Logger = Logger(getClass)

  def eventToAlert(client: TheHiveMispClient, event: Event, organisationId: EntityId): Try[Alert] =
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
          follow = true,
          organisationId = organisationId
        )
      }

  def convertAttributeType(attributeCategory: String, attributeType: String)(implicit
      graph: Graph
  ): Try[(ObservableType with Entity, Seq[String])] = {
    val obsTypeFromConfig = connector
      .attributeConverter(attributeCategory, attributeType)
      .flatMap { attrConv =>
        observableTypeSrv
          .get(attrConv.`type`)
          .headOption
          .map(_ -> attrConv.tags)
      }
    obsTypeFromConfig
      .orElse(observableTypeSrv.get(EntityName(attributeType)).headOption.map(_ -> Nil))
      .fold(observableTypeSrv.getOrFail(EntityName("other")).map(_ -> Seq.empty[String]))(Success(_))
  }

  def attributeToObservable(
      attribute: Attribute
  )(implicit graph: Graph): List[(Observable, ObservableType with Entity, Set[String], Either[String, (String, String, Source[ByteString, _])])] =
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
              Observable(attribute.comment, 0, ioc = false, sighted = false, ignoreSimilarity = None),
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
              Observable(attribute.comment, 0, ioc = false, sighted = false, ignoreSimilarity = None),
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
                  Observable(attribute.comment, 0, ioc = false, sighted = false, ignoreSimilarity = None),
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

  def getLastSyncDate(client: TheHiveMispClient, mispOrganisation: String, organisations: Seq[Organisation with Entity])(implicit
      graph: Graph
  ): Option[Date] = {
    val lastOrgSynchro = client
      .organisationFilter(organisationSrv.startTraversal)
      .group(
        _.by,
        _.by(
          _.alerts
            .filterBySource(mispOrganisation)
            .filterByType("misp")
            .value(a => a.lastSyncDate)
            .max
        )
      )
      .head
      .values

    if (lastOrgSynchro.size == organisations.size && organisations.nonEmpty) Some(lastOrgSynchro.min)
    else None
  }

  def updateOrCreateObservable(
      alert: Alert with Entity,
      observable: Observable,
      observableType: ObservableType with Entity,
      data: String,
      tags: Set[String],
      creation: Boolean
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {

    val existingObservable =
      if (creation) None
      else
        alertSrv
          .get(alert)
          .observables
          .filterOnType(observableType.name)
          .filterOnData(data)
          .richObservable
          .headOption
    existingObservable match {
      case None =>
        logger.debug(s"Observable ${observableType.name}:$data doesn't exist, create it")
        for {
          richObservable <- observableSrv.create(observable, observableType, data, tags, Nil)
          _              <- alertSrv.addObservable(alert, richObservable)
        } yield ()
      case Some(richObservable) =>
        logger.debug(s"Observable ${observableType.name}:$data exists, update it")
        for {
          updatedObservable <-
            observableSrv
              .get(richObservable.observable)
              .when(richObservable.message != observable.message)(_.update(_.message, observable.message))
              .when(richObservable.tlp != observable.tlp)(_.update(_.tlp, observable.tlp))
              .when(richObservable.ioc != observable.ioc)(_.update(_.ioc, observable.ioc))
              .when(richObservable.sighted != observable.sighted)(_.update(_.sighted, observable.sighted))
              .getOrFail("Observable")
          _ <- observableSrv.updateTagNames(updatedObservable, tags)
        } yield ()
    }
  }

  def updateOrCreateObservable(
      alert: Alert with Entity,
      observable: Observable,
      observableType: ObservableType with Entity,
      filename: String,
      contentType: String,
      src: Source[ByteString, _],
      tags: Set[String],
      creation: Boolean
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val existingObservable =
      if (creation) None
      else
        alertSrv
          .get(alert)
          .observables
          .filterOnType(observableType.name)
          .filterOnAttachmentName(filename)
          .filterOnAttachmentName(contentType)
          .richObservable
          .headOption
    existingObservable match {
      case None =>
        logger.debug(s"Observable ${observableType.name}:$filename:$contentType doesn't exist, create it")
        val file = Files.createTempFile("misp-attachment-", "")
        Await.result(src.runWith(FileIO.toPath(file)), 1.hour)
        val fFile = FFile(filename, file, contentType)
        for {
          createdAttachment <- attachmentSrv.create(fFile)
          richObservable    <- observableSrv.create(observable, observableType, createdAttachment, tags, Nil)
          _                 <- alertSrv.addObservable(alert, richObservable)
          _ = Files.delete(file)
        } yield ()
      case Some(richObservable) =>
        logger.debug(s"Observable ${observableType.name}:$filename:$contentType exists, update it")
        for {
          updatedObservable <-
            observableSrv
              .get(richObservable.observable)
              .when(richObservable.message != observable.message)(_.update(_.message, observable.message))
              .when(richObservable.tlp != observable.tlp)(_.update(_.tlp, observable.tlp))
              .when(richObservable.ioc != observable.ioc)(_.update(_.ioc, observable.ioc))
              .when(richObservable.sighted != observable.sighted)(_.update(_.sighted, observable.sighted))
              .getOrFail("Observable")
          _ <- observableSrv.updateTagNames(updatedObservable, tags)
        } yield ()
    }
  }

  def importAttibutes(client: TheHiveMispClient, event: Event, alert: Alert with Entity, lastSynchro: Option[Date])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Unit = {
    logger.debug(s"importAttributes ${client.name}#${event.id}")
    val startSyncDate = new Date
    val queue =
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
        .runWith(Sink.queue[(Observable, ObservableType with Entity, Set[String], Either[String, (String, String, Source[ByteString, _])])])
    QueueIterator(queue).foreach {
      case (observable, observableType, tags, Left(data)) =>
        updateOrCreateObservable(alert, observable, observableType, data, tags ++ client.observableTags, lastSynchro.isEmpty)
          .recover {
            case error =>
              logger.error(s"Unable to create observable $observable ${observableType.name}:$data", error)
          }
      case (observable, observableType, tags, Right((filename, contentType, src))) =>
        updateOrCreateObservable(alert, observable, observableType, filename, contentType, src, tags ++ client.observableTags, lastSynchro.isEmpty)
          .recover {
            case error =>
              logger.error(s"Unable to create observable $observable ${observableType.name}:$filename", error)
          }
    }

    logger.info("Removing old observables")
    alertSrv
      .get(alert)
      .observables
      .filter(
        _.or(
          _.has(_._updatedAt, P.lt(startSyncDate)),
          _.and(_.hasNot(_._updatedAt), _.has(_._createdAt, P.lt(startSyncDate)))
        )
      )
      .toIterator
      .foreach { obs =>
        logger.debug(s"Delete  $obs")
        observableSrv.remove(obs).recover {
          case error => logger.error(s"Fail to delete observable $obs", error)
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
  )(implicit graph: Graph, authContext: AuthContext): Try[(Alert with Entity, JsObject)] = {
    logger.debug(s"updateOrCreateAlert ${client.name}#${event.id} for organisation ${organisation.name}")
    eventToAlert(client, event, organisation._id).flatMap { alert =>
      organisationSrv
        .get(organisation)
        .alerts
        .getBySourceId("misp", mispOrganisation, event.id)
        .richAlert
        .headOption match {
        case None => // if the related alert doesn't exist, create it
          logger.debug(s"Event ${client.name}#${event.id} has no related alert for organisation ${organisation.name}")
          alertSrv
            .create(alert, organisation, event.tags.map(_.name).toSet, Seq(), caseTemplate)
            .map(ra => ra.alert -> ra.toJson.asInstanceOf[JsObject])
        case Some(richAlert) =>
          logger.debug(s"Event ${client.name}#${event.id} have already been imported for organisation ${organisation.name}, updating the alert")
          val (updatedAlertTraversal, updatedFields) = (alertSrv.get(richAlert.alert), JsObject.empty)
            .when(richAlert.title != alert.title)(_.update(_.title, alert.title), _ + ("title" -> JsString(alert.title)))
            .when(richAlert.lastSyncDate != alert.lastSyncDate)(
              _.update(_.lastSyncDate, alert.lastSyncDate),
              _ + ("lastSyncDate" -> JsNumber(alert.lastSyncDate.getTime))
            )
            .when(richAlert.description != alert.description)(
              _.update(_.description, alert.description),
              _ + ("description" -> JsString(alert.description))
            )
            .when(richAlert.severity != alert.severity)(_.update(_.severity, alert.severity), _ + ("severity" -> JsNumber(alert.severity)))
            .when(richAlert.date != alert.date)(_.update(_.date, alert.date), _ + ("date" -> JsNumber(alert.date.getTime)))
            .when(richAlert.tlp != alert.tlp)(_.update(_.tlp, alert.tlp), _ + ("tlp" -> JsNumber(alert.tlp)))
            .when(richAlert.pap != alert.pap)(_.update(_.pap, alert.pap), _ + ("pap" -> JsNumber(alert.pap)))
            .when(richAlert.externalLink != alert.externalLink)(
              _.update(_.externalLink, alert.externalLink),
              _ + ("externalLink" -> alert.externalLink.fold[JsValue](JsNull)(JsString.apply))
            )
          val tags = event.tags.map(_.name)
          for {
            (addedTags, removedTags) <- alertSrv.updateTagNames(richAlert.alert, tags.toSet)
            updatedAlert             <- updatedAlertTraversal.getOrFail("Alert")
            updatedFieldWithTags =
              if (addedTags.nonEmpty || removedTags.nonEmpty) updatedFields + ("tags" -> JsArray(tags.map(JsString))) else updatedFields
          } yield (updatedAlert, updatedFieldWithTags)
      }
    }
  }

  def syncMispEvents(client: TheHiveMispClient)(implicit authContext: AuthContext): Unit =
    client
      .currentOrganisationName
      .fold(
        error => logger.error("Unable to get MISP organisation", error),
        mispOrganisation => {

          val caseTemplate = client.caseTemplate.flatMap { caseTemplateName =>
            db.roTransaction { implicit graph =>
              caseTemplateSrv.get(EntityName(caseTemplateName)).headOption
            }
          }

          logger.debug(s"Get eligible organisations")
          val organisations = db.roTransaction { implicit graph =>
            client.organisationFilter(organisationSrv.startTraversal).toSeq
          }
          val lastSynchro = db.roTransaction { implicit graph =>
            getLastSyncDate(client, mispOrganisation, organisations)
          }

          logger.debug(s"Last synchronisation is $lastSynchro")
          val queue = client
            .searchEvents(publishDate = lastSynchro)
            .runWith(Sink.queue[Event])
          QueueIterator(queue).foreach { event =>
            logger.debug(s"Importing event ${client.name}#${event.id} in organisation(s): ${organisations.mkString(",")}")
            organisations.foreach { organisation =>
              db.tryTransaction { implicit graph =>
                auditSrv.mergeAudits {
                  updateOrCreateAlert(client, organisation, mispOrganisation, event, caseTemplate)
                    .map {
                      case (alert, updatedFields) =>
                        importAttibutes(client, event, alert, if (alert._updatedBy.isEmpty) None else lastSynchro)
                        (alert, updatedFields)
                    }
                    .recoverWith {
                      case error =>
                        logger.warn(s"Unable to create alert from MISP event ${client.name}#${event.id}", error)
                        Failure(error)
                    }
                } {
                  case (alert, updatedFields) if alert._updatedBy.isDefined => auditSrv.alert.update(alert, updatedFields)
                  case (alert, updatedFields)                               => auditSrv.alert.create(alert, updatedFields)
                }
              }
            }
          }
        }
      )
}
