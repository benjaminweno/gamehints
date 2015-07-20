package hintservice

import com.ning.http.client._
import scala.concurrent._
import ExecutionContext.Implicits.global
import akka.actor._
import akka.io.IO
import akka.routing.RoundRobinPool
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import akka.event.Logging
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.xml._
import scala.util.Properties

object StartUp extends App {
	implicit val system = ActorSystem("MainActorSystem")
	val service = system.actorOf(Props[HintServiceActor], "HintService")
	implicit val timeout = Timeout(1.seconds)
	val myPort = Properties.envOrElse("PORT", "8080").toInt // for Heroku compatibility
	//IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = myPort)
	IO(Http) ? Http.Bind(service, interface = "localhost", port = 1985)
}

