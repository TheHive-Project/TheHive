package org.thp.thehive.connector.cortex.services.notification.notifiers

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{BadConfigurationError, NotFoundError, RichOption}
import org.thp.thehive.connector.cortex.services.{AnalyzerSrv, JobSrv}
import org.thp.thehive.controllers.v0.AuditRenderer
import org.thp.thehive.models._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services._
import org.thp.thehive.services.notification.notifiers.{Notifier, NotifierProvider}
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class RunAnalyzerProvider(
    analyzerSrv: AnalyzerSrv,
    jobSrv: JobSrv,
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv,
    ec: ExecutionContext
) extends NotifierProvider {
  override val name: String = "RunAnalyzer"

  override def apply(config: Configuration): Try[Notifier] =
    config.getOrFail[String]("analyzerName").map { responderName =>
      new RunAnalyzer(
        responderName,
        analyzerSrv,
        jobSrv,
        caseSrv,
        observableSrv,
        ec
      )
    }
}

class RunAnalyzer(
    analyzerName: String,
    analyzerSrv: AnalyzerSrv,
    jobSrv: JobSrv,
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv,
    implicit val ec: ExecutionContext
) extends Notifier
    with AuditRenderer {
  override val name: String = "RunAnalyzer"

  def getObservable(`object`: Option[Entity])(implicit graph: Graph): Future[RichObservable] =
    `object` match {
      case Some(o) if o._label == "Observable" => Future.fromTry(observableSrv.get(o._id).richObservable.getOrFail("Observable"))
      case _                                   => Future.failed(NotFoundError("Audit object is not an observable"))
    }

  def getCase(context: Option[Entity])(implicit graph: Graph): Future[Case with Entity] =
    context match {
      case Some(c) if c._label == "Case" => Future.fromTry(caseSrv.getOrFail(c._id))
      case _                             => Future.failed(NotFoundError("Audit context is not a case"))
    }

  override def execute(
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      organisation: Organisation with Entity,
      user: Option[User with Entity]
  )(implicit graph: Graph): Future[Unit] =
    if (user.isDefined)
      Future.failed(BadConfigurationError("The notification runAnalyzer must not be applied on user"))
    else
      for {
        observable          <- getObservable(`object`)
        case0               <- getCase(context)
        workers             <- analyzerSrv.getAnalyzerByName(analyzerName, organisation._id)
        (worker, cortexIds) <- Future.fromTry(workers.headOption.toTry(Failure(NotFoundError(s"Analyzer $analyzerName not found"))))
        authContext = LocalUserSrv.getSystemAuthContext.changeOrganisation(organisation._id, Permissions.all)
        _ <- jobSrv.submit(cortexIds.head, worker.id, observable, case0)(authContext)
      } yield ()
}
