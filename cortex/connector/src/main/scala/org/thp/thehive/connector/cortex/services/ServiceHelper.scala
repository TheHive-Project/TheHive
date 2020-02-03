package org.thp.thehive.connector.cortex.services

import play.api.Logger

import gremlin.scala.P
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexClient
import org.thp.cortex.dto.v0.OutputWorker
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.services._

@Singleton
class ServiceHelper @Inject() (
    db: Database,
    organisationSrv: OrganisationSrv
) {

  lazy val logger: Logger = Logger(getClass)

  /**
    * Returns the filtered CortexClients according to config
    * @param clients cortex clients instances
    * @param organisationName the concerned organisation to get available clients for
    * @return
    */
  def availableCortexClients(clients: Seq[CortexClient], organisationName: String): Iterable[CortexClient] = db.roTransaction { implicit graph =>
    val l = clients
      .filter(c =>
        organisationFilter(
          organisationSrv.initSteps,
          c.includedTheHiveOrganisations,
          c.excludedTheHiveOrganisations
        ).toList
          .exists(_.name == organisationName)
      )

    if (l.isEmpty) {
      logger.warn(s"No CortexClient found for organisation $organisationName in list ${clients.map(_.name)}")
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
      else organisationSteps.has("name", P.within(includedTheHiveOrganisations))
    if (excludedTheHiveOrganisations.isEmpty) includedOrgs
    else includedOrgs.has("name", P.without(excludedTheHiveOrganisations))
  }

  /**
    * After querying several Cortex clients,
    * it is necessary to group worker results
    * @param l the list of workers list by client
    * @return
    */
  def flattenList(
      l: Iterable[Seq[(OutputWorker, String)]]
//      f: ((OutputWorker, Seq[String])) => Boolean
  ): Map[OutputWorker, Seq[String]] =
    l                     // Iterable[Seq[(worker, cortexId)]]
    .flatten              // Seq[(worker, cortexId)]
      .groupBy(_._1.name) // Map[workerName, Seq[(worker, cortexId)]]
      .values             // Seq[Seq[(worker, cortexId)]]
      .map(a => a.head._1 -> a.map(_._2).toSeq) // Map[worker, Seq[CortexId] ]
      //      .filter(w => f(w))
      .toMap
}
