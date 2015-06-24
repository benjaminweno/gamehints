package finalProject

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

class ArticleServiceActor extends Actor with ArticleService {
	def actorRefFactory = context
	def receive = runRoute(pingRoute ~ allArticlesRoute ~ getIdRoute ~ searchTitleRoute ~ searchContentRoute ~ dictionaryRoute)

}

trait ArticleService extends HttpService with PostGresArticleService{
	import ExecutionContext.Implicits.global
	val pingRoute = 
    	path("ping") {
      		get {
        		complete("pong")
      	}
    }
    val allArticlesRoute = 
    	path("articles") {
    		get {
    			respondWithMediaType(`application/json`) {
    				complete {
    					reciveFuture.map(x => updateJsonRepo(x))
    				}
    			}
    		}
    	}
    	val getIdRoute = 
    	path("article") {
    		get {
    			respondWithMediaType(`application/json`) {
	    			parameters('id) {
	    				id => complete(
	    					reciveFuture.map(x => findArticleById(x, id)).map( x => x match {
		    						case m: Some[Article] => pretty(render(articleToJson(m.get, false)))
		    						case _ => "Error: could not find " + id
		    					}
	    					)
	    				)
	    			}
	    		}
    		}
		}
		val searchTitleRoute =
		path("titles") {
			get {
				respondWithMediaType(`application/json`) {
					parameters('q) {
						q => complete(
							reciveFuture.map(x => searchTitles(x, q)).map(x => updateJsonRepo(x))
						)
					}
				}
			}
		}
		val searchContentRoute =
		path("content") {
			get {
				respondWithMediaType(`application/json`) {
					parameters('q) {
						q => complete(
							reciveFuture.map(x => searchContent(x, q)).map(x => updateJsonRepo(x))
						)
					}
				}
			}
		}
		val dictionaryRoute = 
		path("dictionary") {
			get {
				respondWithMediaType(`application/json`) {
					complete(
						reciveFuture.map(x => pretty(render("dictionary" -> findWordsNotInDictionary(articlesToWordList(x)))))
					)
				}
			}
		}
}

trait PostGresArticleService extends RepoService {
	import StartUp.system.dispatcher
	import StartUp.system
	Class.forName("org.postgresql.Driver")
	SessionFactory.concreteFactory = Some(()=>
    Session.create(
        //java.sql.DriverManager.getConnection("jdbc:postgresql://localhost/benjaminweno", "benjaminweno", ""),
       // new PostgreSqlAdapter()))
    //heroku sql
		java.sql.DriverManager.getConnection("jdbc:postgresql://ec2-54-83-18-87.compute-1.amazonaws.com/d4gtqgkqaps1o9", "udomazalzodxku", "K9m6S5pG4PyqLgSfLNYRkvVxEc"),
       	new PostgreSqlAdapter()))
	transaction {
		articleRepo = from(ArticleRepoSys.articleRepo)(e => select(e)).toList
	}
	def reciveFuture():Future[List[Article]] = {
		Future{
			val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
			val response: Future[HttpResponse] = pipeline(Get("http://www.theverge.com/rss/frontpage.xml"))
			val oldRepo = articleRepo
			articleRepo = Await.result(response.map((x:HttpResponse) => updatedRepo(XML.loadString(x.entity.asString))), 2.seconds)
			transaction {
				for(article <- articleRepo diff oldRepo) ArticleRepoSys.articleRepo.insert(article)
			}
			articleRepo
		}
	}
}

object ArticleRepoSys extends Schema {
	val articleRepo = table[Article]("articles")
}

trait NonCachedThreadedService extends RepoService {
	import ExecutionContext.Implicits.global
	def reciveFuture():Future[List[Article]] = {
		val javaFuture = asyncClient.prepareGet("http://www.theverge.com/rss/frontpage.xml").execute()
		val promise = Promise[List[Article]]()
		new Thread(new Runnable { 
			def run() { 
				promise.complete(scala.util.Try{ 
					val xml = javaFuture.get
					articleRepo = updatedRepo(XML.loadString(xml.getResponseBody("UTF-8")))
					articleRepo
				})
			}
		}).start
		promise.future
	}
}

trait NonUpdatingRepo extends RepoService {
	import ExecutionContext.Implicits.global
	def reciveFuture():Future[List[Article]] = {
		Future { 
			articleRepo
		}
	}
}

trait CachedRepoService extends RepoService {
	import ExecutionContext.Implicits.global
	updatePeriodically
	def reciveFuture():Future[List[Article]] = {
		Future { 
			articleRepo
		}
	}
}

trait NonCachRepoServiceSpray extends RepoService {
	import StartUp.system.dispatcher
	import StartUp.system
	def reciveFuture(): Future[List[Article]] = {
		val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
		val response: Future[HttpResponse] = pipeline(Get("http://www.theverge.com/rss/frontpage.xml"))
		response.map((x:HttpResponse) => updatedRepo(XML.loadString(x.entity.asString)))
	}
}

trait NonCachedRepoService extends RepoService {
	import ExecutionContext.Implicits.global
	def reciveFuture():Future[List[Article]] = {
		Future {
			val xml = asyncClient.prepareGet("http://www.theverge.com/rss/frontpage.xml").execute().get
			articleRepo = updatedRepo(XML.loadString(xml.getResponseBody("UTF-8")))
			articleRepo
		}
	}
}

abstract trait RepoService {
	import ExecutionContext.Implicits.global
	protected var articleRepo = List[Article]()
	protected var jsonRepo = updateJsonRepo(articleRepo)
	protected val dictionary = getDictionary
	protected val asyncClient = new AsyncHttpClient()
	def manUpdateRepo(list: List[Article]) = {
		articleRepo = list
	}
	def getRepo():List[Article] = articleRepo
	def getDictionary(): HashMap[String, Boolean] = {
		val path = "/usr/share/dict/words"
		Source.fromFile(path).getLines().foldLeft(HashMap[String, Boolean]())((acc, x) => acc + (x -> true))
	}
	def findWordsNotInDictionary(words: List[String]): List[String] = {
		def iterateWords(oldList:List[String], newList:List[String], checkedWords:List[String]): List[String] = oldList match {
			case head :: tail => if(checkedWords.contains(head.toLowerCase())) iterateWords(tail, newList, checkedWords)
								 else if(!dictionary.contains(head.toLowerCase())) iterateWords(tail, head.toLowerCase()::newList, head.toLowerCase()::checkedWords)
								 else iterateWords(tail, newList, head.toLowerCase()::checkedWords)
			case Nil => newList
		}
		iterateWords(words, List[String](), List[String]())
	}
	def articlesToWordList(articles: List[Article]): List[String] = {
		for(article <- articles; word <- article.content.split(' ')) yield word.replaceAll("[^\\p{L}\\p{Nd}]+", "");
	}
	def updatePeriodically() = {
		val executer = new java.util.concurrent.ScheduledThreadPoolExecutor(1)
		val fullUpdate = new Runnable {
			def run() = updateFunction
		}
		val f = executer.scheduleAtFixedRate(fullUpdate, 1, 60, java.util.concurrent.TimeUnit.SECONDS)
	}
	def updateFunction() = {
		val updatedFuture = Future{
			val xml = asyncClient.prepareGet("http://www.theverge.com/rss/frontpage.xml").execute().get
			updatedRepo(XML.loadString(xml.getResponseBody("UTF-8")))
		}
		updatedFuture onSuccess {
			case finalList => articleRepo = finalList
			jsonRepo = updateJsonRepo(finalList)
		}
	}
	def reciveFuture():Future[List[Article]] 
	def findArticleById(repo: List[Article], id: String):Option[Article] = {
		articleRepo.find((article:Article) => article.id == id)
	}
	def xmlToArticle(newXML: Node): Article = {
		new Article(
			(newXML \ "id").text,
			cleanHTML((newXML \ "title").text), 
			cleanHTML((newXML \ "author").text), 
			(newXML \ "published").text,
			(newXML \ "updated").text,
			cleanHTML((newXML \ "content").text)
		)
	}
	def updatedRepo(fullXML: Elem): List[Article] = {
		(fullXML \ "entry").map(xmlToArticle(_)).filter(x => (findArticleById(articleRepo, x.id) == None)).toList ::: articleRepo
	}
	def articleToJson(article: Article, shortened: Boolean = true): org.json4s.JValue = {
		val newContent = if(shortened) shortenContent(article.content) else article.content
		(("id" -> article.id) ~
		("title" -> article.title) ~
		("author" -> article.author) ~
		("published" -> article.published) ~
		("updated" -> article.updated) ~
		("content" -> newContent))
	}
	def updateJsonRepo(repo: List[Article]): spray.httpx.marshalling.ToResponseMarshallable = {
		pretty(render("articles:" -> repo.map(articleToJson(_))))
	}
	def shortenContent(string: String): String = {
		string.dropRight(string.size - string.lastIndexOf(' ', 50)) + "..."
	}
	def cleanHTML(string:String): String = {
		Jsoup.parse(string).text
	}
	def searchTitles(repo: List[Article], searchString: String): List[Article] = {
		articleRepo.filter((x) => x.title.toUpperCase.containsSlice(searchString.toUpperCase))
	}
	def searchContent(repo: List[Article], searchString: String): List[Article] = {
		articleRepo.filter((x) => x.content.toUpperCase.containsSlice(searchString.toUpperCase))
	}
}
case class Article(
	 @Column("id") id: String, 
	 @Column("title") title: String, 
	 @Column("author") author: String, 
	 @Column("published") published: String, 
	 @Column("updated") updated: String, 
	 @Column("content") content: String
)