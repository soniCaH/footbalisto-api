package footbalisto

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import footbalisto.Database.Person

object FootbalistoService extends App {

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()
  val logger = Logging(system, getClass)

  Database.createPerson(Person("pieter", "van geel", 30))

  val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
      }
    } ~
      path("world") {
        get {
          complete {
            Database.findPersonByAge(30).map(persons => persons.mkString(","))
          }
        }
      }

  Http().bindAndHandle(route, config.getString("http.interface"), config.getInt("http.port"))

}
