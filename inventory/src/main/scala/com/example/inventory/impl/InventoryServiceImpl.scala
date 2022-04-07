package com.example.inventory.impl

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.example.inventory.api.{InventoryItem, InventoryService}
import com.example.inventory.impl.Inventory._
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * Implementation of the inventory service.
 */
class InventoryServiceImpl(
    clusterSharding: ClusterSharding,
    persistentEntityRegistry: PersistentEntityRegistry,
    inventoryRepository: InventoryRepository,
)(implicit ec: ExecutionContext) extends InventoryService {



  private def entityRef(itemId: String): EntityRef[InventoryCommand] =
    clusterSharding.entityRefFor(Inventory.typeKey, itemId)

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def getItem(itemId: String): ServiceCall[NotUsed, Option[Quantity]] = ServiceCall { _ =>
    entityRef(itemId)
      .ask(reply => GetItem(reply))
      .map(inventory => convertInventory(itemId, inventory))
  }

  private def convertInventory(itemId: String, items: Items) = {
    ???
  }

  override def getAllItems(): ServiceCall[NotUsed, List[(String, Int)]] = ???

  /**
   * Add inventory to the given item id.
   */
  override def updateStock(itemId: String): ServiceCall[Quantity, Done] = ???

  override def addItem(): ServiceCall[InventoryItem, Done] = ServiceCall { item =>
    inventoryRepository.createItem(item.itemId,item.quantity)
    Future.successful(Done)
  }
}
