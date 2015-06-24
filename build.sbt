import NativePackagerKeys._

packageArchetype.java_application

name := "Final-Project"
 
version := "1.0"
 
scalaVersion := "2.10.5"
 
scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
	"spray repo" at "http://repo.spray.io",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)
 
libraryDependencies ++= {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"
  Seq(
 		"com.ning" % "async-http-client" % "1.9.26",
 		"io.spray"            %%  "spray-can"     % sprayV,
   		"io.spray"            %%  "spray-routing" % sprayV,
      "io.spray"            %%  "spray-client" % sprayV,
   		"io.spray"            %%  "spray-testkit" % sprayV  % "test",
  		"com.typesafe.akka"   %%  "akka-actor"    % akkaV,
  		"com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
  		"org.slf4j" % "slf4j-simple" % "1.7.2",
      "org.json4s" %% "json4s-jackson" % "3.2.10",
      "org.jsoup" % "jsoup" % "1.7.2",
      "org.specs2" %% "specs2-core" % "3.6.1" % "test",
      "org.squeryl" %% "squeryl" % "0.9.5-6",
      "postgresql" % "postgresql" % "8.4-701.jdbc4"
	)
}

scalacOptions in Test ++= Seq("-Yrangepos")

Revolver.settings

parallelExecution in Test := false