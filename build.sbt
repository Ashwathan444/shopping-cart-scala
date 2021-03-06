import com.lightbend.lagom.core.LagomVersion.{ current => lagomVersion }

organization in ThisBuild := "com.example"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.5"



val postgresDriver             = "org.postgresql"                % "postgresql"                                    % "42.2.18"
val macwire                    = "com.softwaremill.macwire"     %% "macros"                                        % "2.3.7" % "provided"
val scalaTest                  = "org.scalatest"                %% "scalatest"                                     % "3.2.2" % Test
val akkaDiscoveryKubernetesApi = "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api"                 % "1.0.10"
val lagomScaladslAkkaDiscovery = "com.lightbend.lagom"          %% "lagom-scaladsl-akka-discovery-service-locator" % lagomVersion

ThisBuild / scalacOptions ++= List("-encoding", "utf8", "-deprecation", "-feature", "-unchecked", "-Xfatal-warnings")

def evictionSettings: Seq[Setting[_]] = Seq(
  // This avoids a lot of dependency resolution warnings to be showed.
  // They are not required in Lagom since we have a more strict whitelist
  // of which dependencies are allowed. So it should be safe to not have
  // the build logs polluted with evictions warnings.
  evictionWarningOptions in update := EvictionWarningOptions.default
    .withWarnTransitiveEvictions(false)
    .withWarnDirectEvictions(false)
)

def dockerSettings = evictionSettings++Seq(
  dockerUpdateLatest := true,
  dockerBaseImage := getDockerBaseImage(),
  dockerUsername := sys.props.get("docker.username"),
  dockerRepository := sys.props.get("docker.registry")
)

def getDockerBaseImage(): String = sys.props.get("java.version") match {
  case Some(v) if v.startsWith("11") => "adoptopenjdk/openjdk11"
  case _                             => "adoptopenjdk/openjdk8"
}

// Update the version generated by sbt-dynver to remove any + characters, since these are illegal in docker tags
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))

lazy val root = (project in file("."))
  .settings(name := "shopping-cart-scala")
  .aggregate(shoppingCartApi, shoppingCart,
    inventoryApi, inventory)

lazy val shoppingCartApi = (project in file("shopping-cart-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val inventoryApi = (project in file("inventory-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )


lazy val shoppingCart = (project in file("shopping-cart"))
  .enablePlugins(LagomScala)
  .dependsOn(shoppingCartApi,inventoryApi)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslKafkaClient,
      lagomScaladslPersistenceJdbc,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest,
      postgresDriver,
      lagomScaladslAkkaDiscovery,
      akkaDiscoveryKubernetesApi,
      "com.typesafe.akka" %% "akka-persistence-testkit" % "2.6.14" % Test
    )
  )
  .settings(dockerSettings)
  .settings(lagomForkedTestSettings)



lazy val inventory = (project in file("inventory"))
  .enablePlugins(LagomScala)
  .dependsOn(shoppingCartApi,inventoryApi)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslKafkaClient,
      lagomScaladslPersistenceJdbc,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest,
      postgresDriver,
      lagomScaladslAkkaDiscovery,
      akkaDiscoveryKubernetesApi,
    )
  )
  .settings(dockerSettings)

// The project uses PostgreSQL
lagomCassandraEnabled in ThisBuild := false


lagomKafkaCleanOnStart in ThisBuild := true
lagomKafkaEnabled in ThisBuild := true
lagomKafkaZookeeperPort in ThisBuild := 9999
lagomServicesPortRange in ThisBuild := PortRange(30000, 35000)


