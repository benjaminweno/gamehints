package hintservice

import akka.actor.Actor

import com.ning.http.client._
import scala.concurrent._


import spray.routing._
import spray.http._
import MediaTypes._
import scala.xml._
import scala.concurrent.duration._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.jsoup.Jsoup
import scala.io.Source
import scala.collection.immutable.HashMap
import java.util.concurrent.{Future => JFuture}
import spray.client.pipelining._
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
import org.squeryl.annotations.Column
import org.squeryl.Session
import org.squeryl.SessionFactory
import org.squeryl.adapters.PostgreSqlAdapter

class HintServiceActor extends Actor with HintService {
  def actorRefFactory = context
  def receive = runRoute(pingRoute ~ getHintRoute)

}

trait HintService extends HttpService with HintServiceUtil{
  import ExecutionContext.Implicits.global
  val pingRoute = 
      path("ping") {
          get {
            complete("pong")
        }
    }
    val getHintRoute = 
      pathPrefix("hints") {
        pathEnd {
          get{ 
            complete(getAllHints.map(x => x.map(y => renderJson(y.toJson))))
          }
        } ~
        path(IntNumber) { int =>
            get{
              complete("hints for level " + int)
            }
        }
    }
}

object HintRepoSys extends Schema {
  val hintRepo = table[Hint]("hints")
}

trait HintServiceUtil {
  import StartUp.system.dispatcher
  import StartUp.system
  Class.forName("org.postgresql.Driver")
  SessionFactory.concreteFactory = Some(()=>
    Session.create(
        java.sql.DriverManager.getConnection("jdbc:postgresql://localhost/benjaminweno", "benjaminweno", ""),
        new PostgreSqlAdapter()))
    //heroku sql
    //java.sql.DriverManager.getConnection("jdbc:postgresql://ec2-54-83-18-87.compute-1.amazonaws.com/d4gtqgkqaps1o9", "udomazalzodxku", "K9m6S5pG4PyqLgSfLNYRkvVxEc"),
        //new PostgreSqlAdapter()))
  def getAllHints():Future[List[Hint]] = {
      Future {
          transaction {
            from(HintRepoSys.hintRepo)(e => select(e)).toList
          }
      }
  }
  def renderJson(json: org.json4s.JValue):spray.httpx.marshalling.ToResponseMarshallable = {
    pretty(render(json))
  }
}

case class Hint(
  @Column("content") text:String, 
  @Column("level") level:Int,
  @Column("x") x: Double,
  @Column("y") y: Double,
  @Column("z") z: Double
) {
  def toJson():org.json4s.JValue = {
    (("content" -> text) ~
    ("level" -> level) ~
    ("x" -> x) ~
    ("y" -> y) ~
    ("z" -> z))
  }
}




