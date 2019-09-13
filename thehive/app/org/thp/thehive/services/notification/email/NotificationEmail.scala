package org.thp.thehive.services.notification.email

import play.api.libs.mailer.Email

case class NotificationEmail(subject: Option[String], from: Option[String], template: String) {
  def toEmail = Email(subject = subject.getOrElse("no subject"), from = from.getOrElse("unknown sender"))
}
