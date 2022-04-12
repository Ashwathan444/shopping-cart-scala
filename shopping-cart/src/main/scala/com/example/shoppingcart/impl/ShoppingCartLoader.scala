package com.example.shoppingcart.impl

import com.example.shoppingcart.api.ShoppingCartService
import com.example.inventory.api.InventoryService
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

class ShoppingCartLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new ShoppingCartApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new ShoppingCartApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[ShoppingCartService])
}

trait ShoppingCartComponents
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
  override lazy val lagomServer: LagomServer =
    serverFor[ShoppingCartService](wire[ShoppingCartServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry =
    ShoppingCartSerializerRegistry

  lazy val reportRepository: ShoppingCartReportRepository =
    wire[ShoppingCartReportRepository]
  readSide.register(wire[ShoppingCartReportProcessor])

  lazy val inventoryService = serviceClient.implement[InventoryService]

  // Initialize the sharding for the ShoppingCart aggregate.
  // See https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html
  clusterSharding.init(
    Entity(ShoppingCart.typeKey) { entityContext =>
      ShoppingCart(entityContext)
    }
  )
}

abstract class ShoppingCartApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with ShoppingCartComponents
    with LagomKafkaComponents {

}
