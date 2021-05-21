package org.thp.thehive.services

import com.typesafe.config.ConfigFactory
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import play.api.libs.mailer.{MailerClient, SMTPConfiguration, SMTPMailer}

object MailerProvider {
  def apply(appConfig: ApplicationConfig): MailerClient = {
    val mailerHost: ConfigItem[String, String]                        = appConfig.item[String]("mailer.host", "")
    val mailerPort: ConfigItem[Int, Int]                              = appConfig.item[Int]("mailer.port", "")
    val mailerSsl: ConfigItem[Boolean, Boolean]                       = appConfig.item[Boolean]("mailer.ssl", "")
    val mailerTls: ConfigItem[Boolean, Boolean]                       = appConfig.item[Boolean]("mailer.tls", "")
    val mailerTlsRequired: ConfigItem[Boolean, Boolean]               = appConfig.item[Boolean]("mailer.tlsRequired", "")
    val mailerUser: ConfigItem[Option[String], Option[String]]        = appConfig.optionItem[String]("mailer.user", "")
    val mailerPassword: ConfigItem[Option[String], Option[String]]    = appConfig.optionItem[String]("mailer.password", "")
    val mailerDebugMode: ConfigItem[Boolean, Boolean]                 = appConfig.item[Boolean]("mailer.debugMode", "")
    val mailerTimeout: ConfigItem[Option[Int], Option[Int]]           = appConfig.optionItem[Int]("mailer.timeout", "")
    val mailerConnectionTimeout: ConfigItem[Option[Int], Option[Int]] = appConfig.optionItem[Int]("mailer.connectionTimeout", "")
    //    val mailerProps: ConfigItem[Config, Config] = appConfig.item[Config]("mailer.props", "")
    val mailerMock: ConfigItem[Boolean, Boolean] = appConfig.item[Boolean]("mailer.mock", "")

    val smtpConfiguration: SMTPConfiguration =
      SMTPConfiguration(
        mailerHost.get,
        mailerPort.get,
        mailerSsl.get,
        mailerTls.get,
        mailerTlsRequired.get,
        mailerUser.get,
        mailerPassword.get,
        mailerDebugMode.get,
        mailerTimeout.get,
        mailerConnectionTimeout.get,
        ConfigFactory.empty(),
        mailerMock.get
      )

    new SMTPMailer(smtpConfiguration)
  }
}
