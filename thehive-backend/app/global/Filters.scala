package global

import javax.inject.{ Inject, Singleton }

import scala.collection.immutable

import play.api.http.HttpFilters
import play.api.mvc.Filter

@Singleton
class TheHiveFilters @Inject() (injectedFilters: immutable.Set[Filter]) extends HttpFilters {
  val filters = injectedFilters.toSeq
}