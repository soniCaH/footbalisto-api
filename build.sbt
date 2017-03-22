name := "footbalisto-api"

version := "1.0"

scalaVersion := "2.12.1"

val akkaVersion = "10.0.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaVersion
)