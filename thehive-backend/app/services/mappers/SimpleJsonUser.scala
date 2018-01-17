package services.mappers

case class SimpleJsonUser(
    name: String,
    username: String)

case class SimpleJsonGroups(groups: List[String])