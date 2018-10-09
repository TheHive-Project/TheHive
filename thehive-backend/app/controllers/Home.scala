package controllers

import play.api.Configuration
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import javax.inject.{ Inject, Singleton }

@Singleton
class Home @Inject() (configuration: Configuration, components: ControllerComponents) extends AbstractController(components) {
  def redirect: Action[AnyContent] = Action {
    Redirect(configuration.get[String]("play.http.context").stripSuffix("/") + "/index.html")
  }
}