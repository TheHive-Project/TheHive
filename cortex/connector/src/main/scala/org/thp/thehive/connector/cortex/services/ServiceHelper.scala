package org.thp.thehive.connector.cortex.services

import gremlin.scala.{Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.{CortexClient, CortexConfig}
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

  /**
    * Returns the filtered CortexClients according to config
    * @param cortexConfig cortex config containing all clients instances
    * @param organisation the concerned organisation to get available clients for
    * @return
    */
  def availableCortexClients(cortexConfig: CortexConfig, organisation: Organisation): Iterable[CortexClient] = db.roTransaction { implicit graph =>
    cortexConfig
      .instances
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
