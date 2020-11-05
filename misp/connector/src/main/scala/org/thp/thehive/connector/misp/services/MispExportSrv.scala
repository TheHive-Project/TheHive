package org.thp.thehive.connector.misp.services

import java.util.Date

import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.misp.dto.{Attribute, Tag => MispTag}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{AuthorizationError, BadRequestError, NotFoundError}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.{AlertSrv, AttachmentSrv, CaseSrv, OrganisationSrv}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class MispExportSrv @Inject() (
    connector: Connector,
    caseSrv: CaseSrv,
    attachmentSrv: AttachmentSrv,
    alertSrv: AlertSrv,
    organisationSrv: OrganisationSrv,
    @Named("with-thehive-schema") db: Database
) {

  lazy val logger: Logger = Logger(getClass)

  def observableToAttribute(observable: RichObservable, exportTags: Boolean): Option[Attribute] = {
    lazy val mispTags =
      if (exportTags)
        observable.tags.map(t => MispTag(None, t.toString, Some(t.colour), None)) ++ tlpTags.get(observable.tlp)
      else
        tlpTags.get(observable.tlp).toSeq
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
            tags = mispTags
          )
      }
      .orElse {
        logger.warn(
          s"Observable type ${observable.`type`} can't be converted to MISP attribute. You should add a mapping in `misp.attribute.mapping`"
        )
        None
      }
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
      .filterBySource(orgName)
      .filterByType("misp")
      .headOption

  def getAttributes(`case`: Case with Entity, exportTags: Boolean)(implicit graph: Graph, authContext: AuthContext): Iterator[Attribute] =
    caseSrv.get(`case`).observables.isIoc.richObservable.toIterator.flatMap(observableToAttribute(_, exportTags))

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

  val tlpTags = Map(
    0 -> MispTag(None, "tlp:white", None, None),
    1 -> MispTag(None, "tlp:green", None, None),
    2 -> MispTag(None, "tlp:amber", None, None),
    3 -> MispTag(None, "tlp:red", None, None)
  )
  def createEvent(client: TheHiveMispClient, `case`: Case with Entity, attributes: Seq[Attribute], extendsEvent: Option[String])(implicit
      ec: ExecutionContext
  ): Future[String] = {
    val mispTags =
      if (client.exportCaseTags)
        db.roTransaction { implicit graph =>
          caseSrv.get(`case`._id).tags.toSeq.map(t => MispTag(None, t.toString, Some(t.colour), None)) ++ tlpTags.get(`case`.tlp)
        }
      else tlpTags.get(`case`.tlp).toSeq
    client.createEvent(
      info = `case`.title,
      date = `case`.startDate,
      threatLevel = math.min(4, math.max(1, 4 - `case`.severity)),
      published = false,
      analysis = 0,
      distribution = 0,
      attributes = attributes,
      tags = mispTags,
      extendsEvent = extendsEvent
    )
  }

  def createAlert(client: TheHiveMispClient, `case`: Case with Entity, eventId: String)(implicit
      graph: Graph,
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
      createdAlert <- alertSrv.create(alert.copy(lastSyncDate = new Date(0L)), org, Seq.empty[Tag with Entity], Seq(), None)
      _            <- alertSrv.alertCaseSrv.create(AlertCase(), createdAlert.alert, `case`)
    } yield createdAlert

  def canExport(client: TheHiveMispClient)(implicit authContext: AuthContext): Boolean =
    client.canExport && db.roTransaction { implicit graph =>
      client.organisationFilter(organisationSrv.current).exists
    }

  def export(mispId: String, `case`: Case with Entity)(implicit authContext: AuthContext, ec: ExecutionContext): Future[String] = {
    logger.info(s"Exporting case ${`case`.number} to MISP $mispId")
    for {
      client  <- getMispClient(mispId)
      _       <- if (canExport(client)) Future.successful(()) else Future.failed(AuthorizationError(s"You cannot export case to MISP $mispId"))
      orgName <- Future.fromTry(client.currentOrganisationName)
      maybeAlert = db.roTransaction(implicit graph => getAlert(`case`, orgName))
      _          = logger.debug(maybeAlert.fold("Related MISP event doesn't exist")(a => s"Related MISP event found : ${a.sourceRef}"))
      attributes = db.roTransaction(implicit graph => removeDuplicateAttributes(getAttributes(`case`, client.exportObservableTags)))
      eventId <- createEvent(client, `case`, attributes, maybeAlert.map(_.sourceRef))
      _       <- Future.fromTry(db.tryTransaction(implicit graph => createAlert(client, `case`, eventId)))
    } yield eventId
  }
}
