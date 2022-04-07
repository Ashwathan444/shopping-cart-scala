package com.example.inventory.impl

import com.example.inventory.api.InventoryService
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents
import akka.cluster.sharding.typed.scaladsl.Entity
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

import scala.concurrent.ExecutionContext

class InventoryLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new InventoryApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new InventoryApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[InventoryService])
}

trait InventoryComponents
  extends LagomServerComponents
    with SlickPersistenceComponents
    with HikariCPComponents
    with AhcWSComponents {

  implicit def executionContext: ExecutionContext

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer =
    serverFor[InventoryService](wire[InventoryServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry =
    InventorySerializerRegistry

  lazy val reportRepository: InventoryRepository =
    wire[InventoryRepository]
  readSide.register(wire[InventoryProcessor])

  clusterSharding.init(
    Entity(Inventory.typeKey) { entityContext =>
      Inventory(entityContext)
    }
  )
}

abstract class InventoryApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with InventoryComponents
    with LagomKafkaComponents {}
