name := "footbalisto-api"

version := "1.0"

scalaVersion := "2.12.1"

val akkaVersion = "10.0.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-contrib" % "0.6",

  "org.reactivemongo" %% "reactivemongo" % "0.12.1",
  "org.reactivemongo" %% "reactivemongo-akkastream" % "0.12.2",
  "org.slf4j" % "slf4j-simple" % "1.7.25",

  "joda-time" % "joda-time" % "2.9.9",
  "ch.megard" %% "akka-http-cors" % "0.2.1"
)