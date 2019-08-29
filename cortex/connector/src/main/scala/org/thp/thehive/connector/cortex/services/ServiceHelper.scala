package org.thp.thehive.connector.cortex.services

import play.api.Logger

import gremlin.scala.{Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexClient
import org.thp.cortex.dto.v0.OutputCortexWorker
import org.thp.scalligraph.models.Database
import org.thp.thehive.models._
import org.thp.thehive.services._

@Singleton
class ServiceHelper @Inject()(
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    alertSrv: AlertSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    db: Database,
    schema: TheHiveSchema,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv
) {

  lazy val logger = Logger(getClass)

  /**
    * Returns the filtered CortexClients according to config
    * @param clients cortex clients instances
    * @param organisation the concerned organisation to get available clients for
    * @return
    */
  def availableCortexClients(clients: Seq[CortexClient], organisation: Organisation): Iterable[CortexClient] = db.roTransaction { implicit graph =>
    val l = clients
      .filter(
        c =>
          organisationFilter(
            organisationSrv.initSteps,
            c.includedTheHiveOrganisations,
            c.excludedTheHiveOrganisations
          ).toList
            .contains(organisation)
      )

    if (l.isEmpty) {
      logger.warn(s"No CortexClient found for Organisation ${organisation.name} in list ${clients.map(_.name)}")
    }

    l
  }

  /**
    * Returns the filtered organisations according to the supplied lists (mainly conf based)
    *
    * @param organisationSteps the organisation steps graph instance
    * @param includedTheHiveOrganisations the allowed organisation
    * @param excludedTheHiveOrganisations the excluded ones
    * @return
    */
  def organisationFilter(
      organisationSteps: OrganisationSteps,
      includedTheHiveOrganisations: Seq[String],
      excludedTheHiveOrganisations: Seq[String]
  ): OrganisationSteps = {
    val includedOrgs =
      if (includedTheHiveOrganisations.contains("*") || includedTheHiveOrganisations.isEmpty) organisationSteps
      else organisationSteps.has(Key[String]("name"), P.within(includedTheHiveOrganisations))
    if (excludedTheHiveOrganisations.isEmpty) includedOrgs
    else includedOrgs.has(Key[String]("name"), P.without(excludedTheHiveOrganisations))
  }

  /**
    * After querying several Cortex clients,
    * it is necessary to group worker results
    * @param l the list of workers list by client
    * @return
    */
  def flattenList(
      l: Iterable[Seq[(OutputCortexWorker, String)]],
      f: ((OutputCortexWorker, Seq[String])) => Boolean
  ): Map[OutputCortexWorker, Seq[String]] =
    l                     // Iterable[Seq[(worker, cortexId)]]
    .flatten              // Seq[(worker, cortexId)]
      .groupBy(_._1.name) // Map[workerName, Seq[(worker, cortexId)]]
      .values             // Seq[Seq[(worker, cortexId)]]
      .map(a => a.head._1 -> a.map(_._2).toSeq) // Map[worker, Seq[CortexId] ]
      .filter(w => f(w))
      .toMap
}
