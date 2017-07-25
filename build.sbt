name := "reactive-tickets"

version := "1.0"

scalaVersion := "2.12.2"

libraryDependencies ++= {
  val akkaVersion = "2.5.3"
  val akkaHttpVersion = "10.0.7"
  val akkaHttpJson4sVersion = "1.11.0"
  val json4sVersion = "3.5.0"
  val levelDbVersion = "0.7"
  val levelDbJniVersion = "1.8"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "org.iq80.leveldb" % "leveldb" % levelDbVersion,
    "org.fusesource.leveldbjni" % "leveldbjni-all" % levelDbJniVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "de.heikoseeberger" %% "akka-http-json4s" % akkaHttpJson4sVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "org.json4s" %% "json4s-ext" % json4sVersion
  )
}