package models


case class Id($oid: String)

case class User(_id: Option[Id], username: String, roles: List[String], token: String)