ThisBuild / version := "1.0"

lazy val root = (project in file("."))
  .settings(
    name := "wiki-crawler"
  ).aggregate(crawler, sparkapp)

lazy val crawler = project
  .settings(
    name := "crawler",
    libraryDependencies ++= crawlerDeps,
    scalaVersion := "2.13.8"
    )

lazy val assemblySettings = Seq(
  assembly / assemblyMergeStrategy := {
    case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
    case "module-info.class"           => MergeStrategy.discard
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList("META-INF", "services", xs @ _*) => MergeStrategy.first
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)

lazy val sparkapp = project
  .settings(
    name := "sparkapp",
    libraryDependencies ++= sparkDeps,
    scalaVersion := "2.12.13",
    assemblySettings
  )

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
  "org.apache.spark" %% "spark-core" % "3.1.2" % "provided",
  "org.apache.spark" %% "spark-sql" % "3.1.2" % "provided",
//  "com.arangodb" %% "arangodb-spark-datasource-3.1" % "1.3.0"
)