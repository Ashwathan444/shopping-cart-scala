package com.example.inventory.impl

import java.util.concurrent.atomic.AtomicInteger
import akka.stream.scaladsl.Flow
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.example.inventory.api.InventoryService
import com.example.shoppingcart.api.ShoppingCartView
import com.example.shoppingcart.api.ShoppingCartService
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import play.api.libs.json.{Format, Json}

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.Seq
import scala.concurrent.Future

/**
 * Implementation of the inventory service.
 *
 * This just stores the inventory in memory, so it will be lost on restart, and also won't work
 * with more than one replicas, but it's enough to demonstrate things working.
 */
class InventoryServiceImpl(shoppingCartService: ShoppingCartService) extends InventoryService {


  private val inventory = TrieMap.empty[String, Item]
  val x = inventory.values.toList
  private def getInventory(itemId: String) = inventory.getOrElseUpdate(itemId,Item("SampleItem",10))

  shoppingCartService.shoppingCartTopic.subscribe.atLeastOnce(Flow[ShoppingCartView].map { cart =>
    // Since this is at least once event handling, we really should track by shopping cart, and
    // not update inventory if we've already seen this shopping cart. But this is an in memory
    // inventory tracker anyway, so no need to be that careful.
    cart.items.foreach { item =>
      getInventory(item.itemId)
      inventory.replace(item.itemId,Item(getInventory(item.itemId).name,getInventory(item.itemId).quantity+item.quantity))
      getInventory(item.itemId).quantity
    }
    Done
  })
  override def getItem(itemId: String): ServiceCall[NotUsed, Item] = ServiceCall { _ =>
    Future.successful(getInventory(itemId))
  }

  override def getAllItems(): ServiceCall[NotUsed, List[Item]] = ServiceCall { _ =>
    Future.successful(inventory.values.toList)
  }

  override def updateStock(itemId: String): ServiceCall[Int, Done] = ServiceCall { q =>
    getInventory(itemId)
    inventory.replace(itemId,Item(getInventory(itemId).name,getInventory(itemId).quantity+q))
    Future.successful(Done)
  }

  override def addItem(itemId: String): ServiceCall[Item, Done] = ServiceCall { item =>
    getInventory(itemId)
    inventory.replace(itemId,item)
    Future.successful(Done)
  }

}

