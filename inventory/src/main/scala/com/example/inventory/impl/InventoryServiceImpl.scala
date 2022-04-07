package com.example.inventory.impl

import java.util.concurrent.atomic.AtomicInteger
import akka.stream.scaladsl.Flow
import akka.Done
import akka.NotUsed
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.example.inventory.api.{InventoryItem, InventoryService}
import com.example.shoppingcart.api.ShoppingCartView
import com.example.shoppingcart.api.ShoppingCartService
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.example.inventory.impl.Inventory._
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import play.api.libs.json.{Format, Json}

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.Seq
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

/**
 * Implementation of the inventory service.
 *
 */
class InventoryServiceImpl(
                            clusterSharding: ClusterSharding,
                            persistentEntityRegistry: PersistentEntityRegistry,
                            inventoryRepository: InventoryRepository,
                            shoppingCartService: ShoppingCartService,
                            inventoryService: InventoryService
                          )(implicit ec: ExecutionContext) extends InventoryService {


  private def entityRef(itemId: String): EntityRef[InventoryCommand] =
  clusterSharding.entityRefFor(Inventory.typeKey, itemId)

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def getItem(itemId: String): ServiceCall[NotUsed, Option[Quantity]] = ServiceCall { _ =>
    entityRef(itemId)
      .ask(reply => GetItem(reply))
      .map(inventory => convertInventory(itemId,inventory))
  }

  override def getAllItems(): ServiceCall[NotUsed, List[(String,Int)]] = ServiceCall { _ =>

  }


  override def updateStock(itemId: String): ServiceCall[Int, Done] = ServiceCall { q =>

  }

  override def addItem(itemId: String): ServiceCall[Item, Done] = ServiceCall { item =>

  }

  private def convertInventory(itemId: String, items: Items) = {

    Quantity(items.items.get(itemId))
  }


}

