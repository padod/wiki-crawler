import coursier.core.Configuration.provided

ThisBuild / version := "1.0"

lazy val root = (project in file("."))
  .settings(
    name := "wiki-crawler"
  ).aggregate(crawler, spark)

lazy val crawler = project
  .settings(
    name := "crawler",
    libraryDependencies ++= crawlerDeps,
    scalaVersion := "2.13.8"
    )

lazy val spark = project
  .settings(
    name := "spark",
    libraryDependencies ++= sparkDeps,
    scalaVersion := "2.12.4"
  )
//ThisBuild / assemblyMergeStrategy := {
//}

lazy val akkaVersion = "2.6.19"

lazy val crawlerDeps = Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % "10.2.9",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.9",
  "org.jsoup" % "jsoup" % "1.14.3",
  "com.github.scopt" %% "scopt" % "4.0.1",
  //  for logging
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "org.slf4j" % "slf4j-api" % "1.7.36",
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion
)

lazy val sparkDeps = Seq(
  "org.apache.spark" %% "spark-core" % "3.1.2" % provided,
  "org.apache.spark" %% "spark-sql" % "3.1.2" % provided,
  "com.arangodb" %% "arangodb-spark-connector" % "2.0.0"
)