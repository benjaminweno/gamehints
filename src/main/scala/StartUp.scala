package finalProject

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
	implicit val system = ActorSystem("RSSService")
	val service = system.actorOf(Props[ArticleServiceActor], "CachedArticleService")
	implicit val timeout = Timeout(1.seconds)
	val myPort = Properties.envOrElse("PORT", "8080").toInt // for Heroku compatibility
	IO(Http) ? Http.Bind(service, interface = "localhost", port = myPort)
}
/*object StartUpAkka extends App {
	implicit val system = ActorSystem("RSSServiceAkka")
	//val service = system.actorOf(Props[AkkaArticleServiceActor], "AkkaArticleService")
	val service = system.actorOf(Props(new AkkaArticleServiceActor()).withRouter(RoundRobinPool(5)), name = "AkkaArticleService")
	implicit val timeout = Timeout(2.seconds)
	IO(Http) ? Http.Bind(service, interface = "localhost", port = 1986)
}*/
