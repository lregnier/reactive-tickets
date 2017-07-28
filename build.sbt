name := "reactive-tickets"

version := "1.0"

scalaVersion := "2.11.11"

libraryDependencies ++= {
  val akkaVersion = "2.5.3"
  val akkaHttpVersion = "10.0.7"
  val akkaHttpJson4sVersion = "1.11.0"
  val json4sVersion = "3.5.0"
  val reactiveMongoVersion = "0.12.5"
  val logBackVersion = "1.2.3"
  Seq(
    "com.typesafe.akka"  %%  "akka-actor"                  %  akkaVersion,
    "com.typesafe.akka"  %%  "akka-http"                   %  akkaHttpVersion,
    "com.typesafe.akka"  %%  "akka-stream"                 %  akkaVersion,
    "de.heikoseeberger"  %%  "akka-http-json4s"            %  akkaHttpJson4sVersion,
    "org.json4s"         %%  "json4s-jackson"              %  json4sVersion,
    "org.json4s"         %%  "json4s-ext"                  %  json4sVersion,
    "org.reactivemongo"  %%  "reactivemongo"               %  reactiveMongoVersion,
    "ch.qos.logback"     %   "logback-classic"             %  logBackVersion
  )
}