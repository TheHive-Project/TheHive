package org.thp.thehive.controllers.dav

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.{CaseSrv, OrganisationSrv}

import javax.inject.{Inject, Singleton}

@Singleton
class VFS @Inject() (caseSrv: CaseSrv, organisationSrv: OrganisationSrv) {

  def get(path: List[String])(implicit graph: Graph, authContext: AuthContext): Seq[Resource] =
    path match {
      case Nil | "" :: Nil                        => List(StaticResource(""))
      case "cases" :: Nil                         => List(StaticResource(""))
      case "cases" :: cid :: Nil                  => caseSrv.startTraversal.getByNumber(cid.toInt).toSeq.map(EntityResource(_, ""))
      case "cases" :: cid :: "observables" :: Nil => List(StaticResource(""))
      case "cases" :: cid :: "tasks" :: Nil       => List(StaticResource(""))
      case "cases" :: cid :: "observables" :: aid :: Nil =>
        caseSrv
          .startTraversal
          .getByNumber(cid.toInt)
          .observables
          .attachments
          .has(_.attachmentId, aid)
          .toSeq
          .map(AttachmentResource(_, emptyId = true))
      case "cases" :: cid :: "tasks" :: aid :: Nil =>
        caseSrv
          .startTraversal
          .getByNumber(cid.toInt)
          .tasks
          .logs
          .attachments
          .has(_.attachmentId, aid)
          .toSeq
          .map(AttachmentResource(_, emptyId = true))
      case _ => Nil
    }

  def list(path: List[String])(implicit graph: Graph, authContext: AuthContext): Seq[Resource] =
    path match {
      case Nil | "" :: Nil       => List(StaticResource("cases"))
      case "cases" :: Nil        => caseSrv.startTraversal.visible(organisationSrv).toSeq.map(c => EntityResource(c, c.number.toString))
      case "cases" :: cid :: Nil => List(StaticResource("observables"), StaticResource("tasks"))
      case "cases" :: cid :: "observables" :: Nil =>
        caseSrv
          .startTraversal
          .getByNumber(cid.toInt)
          .observables
          .attachments
          .domainMap(AttachmentResource(_, emptyId = false))
          .toSeq
      case "cases" :: cid :: "tasks" :: Nil =>
        caseSrv
          .startTraversal
          .getByNumber(cid.toInt)
          .tasks
          .logs
          .attachments
          .domainMap(AttachmentResource(_, emptyId = false))
          .toSeq
      case _ => Nil
    }
}
