package com.example.inventory.impl

import com.example.inventory.api.InventoryService
import com.example.shoppingcart.api.ShoppingCartService
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.db.{DBComponents, HikariCPComponents}
import play.api.libs.ws.ahc.AhcWSComponents
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.api.LagomConfigComponent
import com.lightbend.lagom.scaladsl.client.LagomServiceClientComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import play.api.Environment

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
      with DBComponents
      with LagomConfigComponent
      with HikariCPComponents
      with AhcWSComponents
      with LagomServiceClientComponents {

  implicit def executionContext: ExecutionContext

  def environment: Environment

  implicit def materializer: Materializer

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[InventoryService](wire[InventoryServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = InventorySerializerRegistry

  lazy val reportRepository: InventoryRepository = wire[InventoryRepository]

  readSide.register(wire[InventoryProcessor])
  lazy val shoppingCartService = serviceClient.implement[ShoppingCartService]

  clusterSharding.init(
    Entity(Inventory.typeKey) { entityContext =>
      Inventory(entityContext)
    }
  )
}

abstract class InventoryApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with InventoryComponents
    with LagomKafkaComponents {
}
