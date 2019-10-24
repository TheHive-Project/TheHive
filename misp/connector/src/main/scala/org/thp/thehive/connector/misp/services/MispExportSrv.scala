package org.thp.thehive.connector.misp.services

import java.util.Date

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.Logger

import gremlin.scala.{Graph, Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.misp.dto.{Attribute, Tag}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{BadRequestError, NotFoundError}
import org.thp.thehive.models._
import org.thp.thehive.services.{AlertSrv, AttachmentSrv, CaseSrv, OrganisationSrv}

@Singleton
class MispExportSrv @Inject()(
    connector: Connector,
    caseSrv: CaseSrv,
    attachmentSrv: AttachmentSrv,
    alertSrv: AlertSrv,
    organisationSrv: OrganisationSrv,
    db: Database
) {

  lazy val logger = Logger(getClass)

  def observableToAttribute(observable: RichObservable): Option[Attribute] =
    connector
      .attributeConverter(observable.`type`)
      .map {
        case (cat, tpe) =>
          Attribute(
            id = "",
            `type` = tpe,
            category = cat,
            toIds = false,
            eventId = "",
            distribution = 0,
            date = observable.observable._createdAt,
            comment = observable.message,
            deleted = false,
            data = observable.attachment.map(a => (a.name, a.contentType, attachmentSrv.source(a))),
            value = observable.data.fold(observable.attachment.get.name)(_.data),
            firstSeen = None,
            lastSeen = None,
            tags = observable.tags.map(t => Tag(None, t.toString, Some(t.colour), None))
          )
      }
      .orElse {
        logger.warn(
          s"Observable type ${observable.`type`} can't be converted to MISP attribute. You should add a mapping in `misp.attribute.mapping`"
        )
        None
      }

  def getMispClient(mispId: String): Future[TheHiveMispClient] =
    connector
      .clients
      .find(_.name == mispId)
      .fold[Future[TheHiveMispClient]](Future.failed(NotFoundError(s"MISP server $mispId not found"))) {
        case client if client.canExport => Future.successful(client)
        case _                          => Future.failed(BadRequestError(s"Export on MISP connection $mispId is denied by configuration"))
      }

  def getAlert(`case`: Case with Entity, orgName: String)(implicit graph: Graph): Option[Alert with Entity] =
    caseSrv
      .get(`case`)
      .alert
      .has(Key("type"), P.eq("misp"))
      .has(Key("source"), P.eq(orgName))
      .headOption()

  def getAttributes(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Iterator[Attribute] =
    caseSrv.get(`case`).observables.has(Key("ioc"), P.eq(true)).richObservable.toIterator.flatMap(observableToAttribute)

  def removeDuplicateAttributes(attributes: Iterator[Attribute]): Seq[Attribute] = {
    var attrSet = Set.empty[(String, String, String)]
    val builder = Seq.newBuilder[Attribute]
    attributes.foreach { attr =>
      val tuple = (attr.category, attr.`type`, attr.value)
      if (!attrSet.contains(tuple)) {
        builder += attr
        attrSet += tuple
      }
    }
    builder.result()
  }

  def createEvent(client: TheHiveMispClient, `case`: Case, attributes: Seq[Attribute], extendsEvent: Option[String])(
      implicit ec: ExecutionContext
  ): Future[String] =
    client.createEvent(
      info = `case`.title,
      date = `case`.startDate,
      threatLevel = 4 - `case`.severity,
      published = false,
      analysis = 0,
      distribution = 0,
      attributes = attributes,
      extendsEvent = extendsEvent
    )

  def createAlert(client: TheHiveMispClient, `case`: Case with Entity, eventId: String)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichAlert] =
    for {
      alert <- client.currentOrganisationName.map { orgName =>
        Alert(
          `type` = "misp",
          source = orgName,
          sourceRef = eventId,
          externalLink = Some(s"${client.baseUrl}/events/$eventId"),
          title = `case`.title,
          description = `case`.description,
          severity = `case`.severity,
          date = `case`.startDate,
          lastSyncDate = new Date(0L),
          tlp = `case`.tlp,
          pap = `case`.pap,
          read = false,
          follow = true
        )
      }
      org          <- organisationSrv.getOrFail(authContext.organisation)
      createdAlert <- alertSrv.create(alert.copy(lastSyncDate = new Date(0L)), org, Set.empty, Map.empty, None)
      _            <- alertSrv.alertCaseSrv.create(AlertCase(), createdAlert.alert, `case`)
    } yield createdAlert

  def export(mispId: String, `case`: Case with Entity)(implicit authContext: AuthContext, ec: ExecutionContext): Future[String] = {
    logger.info(s"Exporting case ${`case`.number} to MISP $mispId")
    for {
      client  <- getMispClient(mispId)
      orgName <- Future.fromTry(client.currentOrganisationName)
      maybeAlert = db.roTransaction(implicit graph => getAlert(`case`, orgName))
      _          = logger.debug(maybeAlert.fold(s"Related MISP event doesn't exist")(a => s"Related MISP event found : ${a.sourceRef}"))
      attributes = db.roTransaction(implicit graph => removeDuplicateAttributes(getAttributes(`case`)))
      eventId <- createEvent(client, `case`, attributes, maybeAlert.map(_.sourceRef))
      _       <- Future.fromTry(db.tryTransaction(implicit graph => createAlert(client, `case`, eventId)))
    } yield eventId
  }
  /*


         attributes <- mispSrv.getAttributesFromCase(caze)
          uniqueAttributes = removeDuplicateAttributes(attributes)
          eventToUpdate <- maybeEventId.fold(Future.successful[Option[String]](None))(getUpdatableEvent(mispConnection, _))
          (eventId, initialExportesArtifacts, existingAttributes) <- eventToUpdate.fold {
            logger.debug(s"Creating a new MISP event that extends $maybeEventId")
            val simpleAttributes = uniqueAttributes.filter(_.value.isLeft)
            // if no event is associated to this case, create a new one
            createEvent(
              mispConnection,
              caze.title(),
              caze.severity(),
              caze.tlp(),
              caze.startDate(),
              simpleAttributes,
              maybeEventId,
              if (mispConnection.exportCaseTags) caze.tags() else Nil
            ).map {
              case (eventId, exportedAttributes) =>
                (eventId, exportedAttributes.map(a => Success(a.artifact)), exportedAttributes.map(_.value.map(_.name)))
            }
          } { eventId => // if an event already exists, retrieve its attributes in order to export only new one
            logger.debug(s"Updating MISP event $eventId")
            mispSrv.getAttributesFromMisp(mispConnection, eventId, None).map { attributes =>
              (eventId, Nil, attributes.map {
                case MispArtifact(SimpleArtifactData(data), _, _, _, _, _)                             => Left(data)
                case MispArtifact(RemoteAttachmentArtifact(filename, _, _), _, _, _, _, _)             => Right(filename)
                case MispArtifact(AttachmentArtifact(Attachment(filename, _, _, _, _)), _, _, _, _, _) => Right(filename)
              })
            }
          }

          newAttributes = uniqueAttributes.filterNot(attr => existingAttributes.contains(attr.value.map(_.name)))
          exportedArtifact <- Future.traverse(newAttributes)(attr => exportAttribute(mispConnection, eventId, attr).toTry)
          artifacts = uniqueAttributes.map { a =>
            Json.obj(
              "data"       -> a.artifact.data(),
              "dataType"   -> a.artifact.dataType(),
              "message"    -> a.artifact.message(),
              "startDate"  -> a.artifact.startDate(),
              "attachment" -> a.artifact.attachment(),
              "tlp"        -> a.artifact.tlp(),
              "tags"       -> a.artifact.tags(),
              "ioc"        -> a.artifact.ioc()
            )
          }
          alert <- maybeAlertId.fold {
            alertSrv.create(
              Fields(
                Json.obj(
                  "type"         -> "misp",
                  "source"       -> mipsId,
                  "sourceRef"    -> eventId,
                  "date"         -> caze.startDate(),
                  "lastSyncDate" -> new Date(0),
                  "case"         -> caze.id,
                  "title"        -> caze.title(),
                  "description"  -> "Case have been exported to MISP",
                  "severity"     -> caze.severity(),
                  "tags"         -> caze.tags(),
                  "tlp"          -> caze.tlp(),
                  "artifacts"    -> artifacts,
                  "status"       -> "Imported",
                  "follow"       -> true
                )
              )
            )
          } { alertId =>
            alertSrv.update(alertId, Fields(Json.obj("artifacts" -> artifacts, "status" -> "Imported")))
          }
        } yield alert.id -> (initialExportesArtifacts ++ exportedArtifact)
      }
  }

}
 */

}
