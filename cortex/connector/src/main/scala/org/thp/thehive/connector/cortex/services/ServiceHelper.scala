package org.thp.thehive.connector.cortex.services

import gremlin.scala.{Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.{CortexClient, CortexConfig}
import org.thp.scalligraph.models.Database
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger

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
    * @param cortexConfig cortex config containing all clients instances
    * @param organisation the concerned organisation to get available clients for
    * @return
    */
  def availableCortexClients(cortexConfig: CortexConfig, organisation: Organisation): Iterable[CortexClient] = db.roTransaction { implicit graph =>
    val l = cortexConfig
      .clients
      .values
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
      logger.warn(s"No CortexClient found for Organisation ${organisation.name} in list ${cortexConfig.clients.keys}")
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
}
