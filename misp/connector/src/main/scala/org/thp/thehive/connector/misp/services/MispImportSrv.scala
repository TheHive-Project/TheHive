package org.thp.thehive.connector.misp.services

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.misp.dto.{Attribute, Event, Tag => MTag}
import org.thp.scalligraph.auth.{AuthContext, UserSrv}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.utils.FunctionalCondition._
import org.thp.scalligraph.{CreateError, EntityId, EntityName, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.{UserSrv => _, _}
import play.api.Logger
import play.api.libs.json._

import java.nio.file.Files
import java.util.Date
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

class MispImportSrv(
    alertSrv: AlertSrv,
    observableSrv: ObservableSrv,
    organisationSrv: OrganisationSrv,
    observableTypeSrv: ObservableTypeSrv,
    caseTemplateSrv: CaseTemplateSrv,
    db: Database,
    auditSrv: AuditSrv,
    userSrv: UserSrv,
    attributeConvertersConfig: ConfigItem[Seq[AttributeConverter], Seq[AttributeConverter]],
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) extends TheHiveOpsNoDeps {

  lazy val logger: Logger = Logger(getClass)

  def attributeConverter(attributeCategory: String, attributeType: String): Option[AttributeConverter] =
    attributeConvertersConfig.get.reverseIterator.find(a => a.mispCategory == attributeCategory && a.mispType == attributeType)

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
              case MTag(_, "tlp:white", _, _) => 0
              case MTag(_, "tlp:green", _, _) => 1
              case MTag(_, "tlp:amber", _, _) => 2
              case MTag(_, "tlp:red", _, _)   => 3
            }
            .getOrElse(2),
          pap = 2,
          read = false,
          follow = true,
          organisationId = organisationId,
          tags = event.tags.map(_.name),
          caseId = EntityId.empty
        )
      }

  def convertAttributeType(attributeCategory: String, attributeType: String)(implicit
      graph: Graph
  ): Try[(ObservableType with Entity, Seq[String])] = {
    val obsTypeFromConfig = attributeConverter(attributeCategory, attributeType)
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
  )(implicit graph: Graph): List[(Observable, Either[String, (String, String, Source[ByteString, _])])] =
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
              Observable(
                message = attribute.comment,
                tlp = 0,
                ioc = false,
                sighted = false,
                ignoreSimilarity = None,
                dataType = observableType.name,
                tags = additionalTags ++ attribute.tags.map(_.name)
              ),
              Right(attribute.data.get)
            )
          )
        case Seq((observableType, additionalTags)) if !observableType.isAttachment =>
          logger.debug(
            s"attribute ${attribute.category}:${attribute.`type`} (${attribute.tags}) is converted to observable $observableType with tags $additionalTags"
          )
          List(
            (
              Observable(
                message = attribute.comment,
                tlp = 0,
                ioc = false,
                sighted = false,
                ignoreSimilarity = None,
                dataType = observableType.name,
                tags = additionalTags ++ attribute.tags.map(_.name)
              ),
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
                  Observable(
                    message = attribute.comment,
                    tlp = 0,
                    ioc = false,
                    sighted = false,
                    ignoreSimilarity = None,
                    dataType = observableType.name,
                    tags = additionalTags ++ attribute.tags.map(_.name)
                  ),
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
      .notAdmin
      ._id
      .toIterator
      .flatMap { orgId =>
        alertSrv
          .startTraversal
          .filterBySource(mispOrganisation)
          .filterByType("misp")
          .has(_.organisationId, orgId)
          .value(a => a.lastSyncDate)
          .max
          .headOption
      }
      .toSeq

    if (lastOrgSynchro.size == organisations.size && organisations.nonEmpty) Some(lastOrgSynchro.min)
    else None
  }

  def updateOrCreateSimpleObservable(
      alert: Alert with Entity,
      observable: Observable,
      data: String
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    alertSrv
      .createObservable(alert, observable, data)
      .map(_ => ())
      .recoverWith {
        case _: CreateError =>
          for {
            richObservable <-
              observableSrv
                .startTraversal
                .has(_.organisationIds, organisationSrv.currentId)
                .has(_.relatedId, alert._id)
                .has(_.data, data)
                .richObservable
                .getOrFail("Observable")
            _ <-
              observableSrv
                .get(richObservable.observable)
                .when(richObservable.message != observable.message)(_.update(_.message, observable.message))
                .when(richObservable.tlp != observable.tlp)(_.update(_.tlp, observable.tlp))
                .when(richObservable.ioc != observable.ioc)(_.update(_.ioc, observable.ioc))
                .when(richObservable.sighted != observable.sighted)(_.update(_.sighted, observable.sighted))
                .when(richObservable.tags.toSet != observable.tags.toSet)(_.update(_.tags, observable.tags))
                .getOrFail("Observable")
          } yield ()
      }

  def updateOrCreateAttachmentObservable(
      alert: Alert with Entity,
      observable: Observable,
      filename: String,
      contentType: String,
      src: Source[ByteString, _],
      creation: Boolean
  )(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val existingObservable =
      if (creation) None
      else
        alertSrv
          .get(alert)
          .observables
          .filterOnType(observable.dataType)
          .filterOnAttachmentName(filename)
          .filterOnAttachmentName(contentType)
          .richObservable
          .headOption
    existingObservable match {
      case None =>
        logger.debug(s"Observable ${observable.dataType}:$filename:$contentType doesn't exist, create it")
        val file = Files.createTempFile("misp-attachment-", "")
        Await.result(src.runWith(FileIO.toPath(file)), 1.hour)
        val fFile = FFile(filename, file, contentType)
        val res   = alertSrv.createObservable(alert, observable, fFile).map(_ => ())
        Files.delete(file)
        res
      case Some(richObservable) =>
        logger.debug(s"Observable ${observable.dataType}:$filename:$contentType exists, update it")
        for {
          _ <-
            observableSrv
              .get(richObservable.observable)
              .when(richObservable.message != observable.message)(_.update(_.message, observable.message))
              .when(richObservable.tlp != observable.tlp)(_.update(_.tlp, observable.tlp))
              .when(richObservable.ioc != observable.ioc)(_.update(_.ioc, observable.ioc))
              .when(richObservable.sighted != observable.sighted)(_.update(_.sighted, observable.sighted))
              .when(richObservable.tags.toSet != observable.tags.toSet)(_.update(_.tags, observable.tags))
              .getOrFail("Observable")
        } yield ()
    }
  }

  def importAttributes(client: TheHiveMispClient, event: Event, alert: Alert with Entity, lastSynchro: Option[Date])(implicit
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
            (Observable, Either[String, (String, String, Source[ByteString, _])])
          ]
        ) {
          case (distinctMap, data @ (obs, Left(d)))          => distinctMap + ((obs.dataType, d) -> data)
          case (distinctMap, data @ (obs, Right((n, _, _)))) => distinctMap + ((obs.dataType, n) -> data)
        }
        .mapConcat { m =>
          m.values.toList
        }
        .runWith(Sink.queue[(Observable, Either[String, (String, String, Source[ByteString, _])])]())
    QueueIterator(queue).foreach {
      case (observable, Left(data)) =>
        updateOrCreateSimpleObservable(alert, observable, data)
          .recover {
            case error =>
              logger.error(s"Unable to create observable $observable", error)
          }
      case (observable, Right((filename, contentType, src))) =>
        updateOrCreateAttachmentObservable(
          alert,
          observable,
          filename,
          contentType,
          src,
          lastSynchro.isEmpty
        )
          .recover {
            case error =>
              logger.error(s"Unable to create observable $observable: $filename", error)
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
        observableSrv.delete(obs).recover {
          case error => logger.error(s"Fail to delete observable $obs", error)
        }
      }
  }

//  def convertTag(mispTag: MTag): Tag = tagSrv.parseString(mispTag.name + mispTag.colour.fold("")(c => f"#$c%06X"))

  def updateOrCreateAlert(
      client: TheHiveMispClient,
      organisation: Organisation with Entity,
      mispOrganisation: String,
      event: Event,
      caseTemplate: Option[CaseTemplate with Entity]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Alert with Entity, JsObject)] = {
    logger.debug(s"updateOrCreateAlert ${client.name}#${event.id} for organisation ${organisation.name}")
    eventToAlert(client, event, organisation._id).flatMap { alert =>
      alertSrv
        .startTraversal
        .getBySourceId("misp", mispOrganisation, event.id)
        .has(_.organisationId, organisation._id)
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
            (addedTags, removedTags) <- alertSrv.updateTags(richAlert.alert, tags.toSet)
            updatedAlert             <- updatedAlertTraversal.getOrFail("Alert")
            updatedFieldWithTags =
              if (addedTags.nonEmpty || removedTags.nonEmpty) updatedFields + ("tags" -> JsArray(tags.map(JsString))) else updatedFields
          } yield (updatedAlert, updatedFieldWithTags)
      }
    }
  }

  def syncMispEvents(client: TheHiveMispClient): Unit =
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
            client.organisationFilter(organisationSrv.startTraversal).notAdmin.toSeq
          }
          val lastSynchro = db.roTransaction { implicit graph =>
            getLastSyncDate(client, mispOrganisation, organisations)
          }

          logger.debug(s"Last synchronisation is $lastSynchro")
          val queue = client
            .searchEvents(publishDate = lastSynchro)
            .runWith(Sink.queue[Event]())
          QueueIterator(queue).foreach { event =>
            logger.debug(s"Importing event ${client.name}#${event.id} in organisation(s): ${organisations.mkString(",")}")
            organisations.foreach { organisation =>
              implicit val authContext: AuthContext = userSrv.getSystemAuthContext.changeOrganisation(organisation._id, Profile.admin.permissions)
              db.tryTransaction { implicit graph =>
                auditSrv.mergeAudits {
                  updateOrCreateAlert(client, organisation, mispOrganisation, event, caseTemplate)
                    .map {
                      case (alert, updatedFields) =>
                        importAttributes(client, event, alert, if (alert._updatedBy.isEmpty) None else lastSynchro)
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
