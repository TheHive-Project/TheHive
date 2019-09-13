package org.thp.thehive.services.notification.email.templates

import com.github.jknack.handlebars.TypeSafeTemplate
import org.thp.thehive.models.User

abstract class UserTemplate extends TypeSafeTemplate[User] {
  def setFirstName(firstName: String): UserTemplate
  def setLastName(lastName: String): UserTemplate
}
