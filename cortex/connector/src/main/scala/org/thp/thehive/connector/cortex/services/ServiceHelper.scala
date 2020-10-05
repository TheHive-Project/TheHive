package org.thp.thehive.connector.cortex.services

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.cortex.client.CortexClient
import org.thp.cortex.dto.v0.OutputWorker
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.models.Organisation
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services._
import play.api.Logger

@Singleton
class ServiceHelper @Inject() (
    @Named("with-thehive-cortex-schema") db: Database,
    organisationSrv: OrganisationSrv
) {

  lazy val logger: Logger = Logger(getClass)

  /**
    * Returns the filtered CortexClients according to config
    * @param clients cortex clients instances
    * @param organisationName the concerned organisation to get available clients for
    * @return
    */
  def availableCortexClients(clients: Seq[CortexClient], organisationName: EntityIdOrName): Iterable[CortexClient] =
    db.roTransaction { implicit graph =>
      val l = clients
        .filter(c =>
          organisationFilter(
            organisationSrv.startTraversal,
            c.includedTheHiveOrganisations,
            c.excludedTheHiveOrganisations
          ).get(organisationName).exists
        )

      if (l.isEmpty)
        logger.warn(s"No CortexClient found for organisation $organisationName in list ${clients.map(_.name)}")

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
      organisationSteps: Traversal.V[Organisation],
      includedTheHiveOrganisations: Seq[String],
      excludedTheHiveOrganisations: Seq[String]
  ): Traversal.V[Organisation] = {
    val includedOrgs =
      if (includedTheHiveOrganisations.contains("*") || includedTheHiveOrganisations.isEmpty) organisationSteps
      else organisationSteps.has(_.name, P.within(includedTheHiveOrganisations: _*))
    if (excludedTheHiveOrganisations.isEmpty) includedOrgs
    else includedOrgs.has(_.name, P.without(excludedTheHiveOrganisations: _*))
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
      .flatten            // Seq[(worker, cortexId)]
      .groupBy(_._1.name) // Map[workerName, Seq[(worker, cortexId)]]
      .values             // Seq[Seq[(worker, cortexId)]]
      .map(a => a.head._1 -> a.map(_._2).toSeq) // Map[worker, Seq[CortexId] ]
      //      .filter(w => f(w))
      .toMap
}
