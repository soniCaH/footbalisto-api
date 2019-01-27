name := """footbalisto-api"""
organization := "footbalisto"

version := "1.3-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.2"


libraryDependencies ++= Seq(
  guice, ws,
  "org.reactivemongo" %% "play2-reactivemongo" % "0.13.0-play26",
//  "commons-io" % "commons-io" % "2.5",
  "com.typesafe.akka" %% "akka-stream-contrib" % "0.6",
  "org.mnode.ical4j" % "ical4j" % "2.0.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.0" % Test
)

dockerUsername := Option("pvangeel")
dockerEntrypoint := Seq(s"bin/${executableScriptName.value}", "-J-Xms64M", "-J-Xmx64M")

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "footbalisto.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "footbalisto.binders._"

routesGenerator := InjectedRoutesGenerator
