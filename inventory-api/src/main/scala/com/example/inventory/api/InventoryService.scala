package com.example.inventory.api

import akka.Done
import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.JsValueMessageSerializer
import play.api.libs.json.{Format, Json}
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.immutable.Seq

/**
 * The inventory service interface.
 *
 * This describes everything that Lagom needs to know about how to serve and
 * consume the inventory service.
 */
trait InventoryService extends Service {

  /**
   * Get the inventory level for the given item id.
   */
  def getItem(itemId: String): ServiceCall[NotUsed, Item]

  def getAllItems(): ServiceCall[NotUsed, List[Item]]
  /**
   * Add inventory to the given item id.
   */
  def updateStock(itemId: String): ServiceCall[Int, Done]

  def addItem(itemId: String): ServiceCall[Item, Done]


  final override def descriptor = {
    import Service._

    named("inventory")
      .withCalls(
        restCall(Method.GET, "/items/:itemId", getItem _),
        restCall(Method.GET, "/items", getAllItems _),
        restCall(Method.PUT, "/items/:itemId", updateStock _),
        restCall(Method.POST, "/items/:itemId", addItem _)
      )
      .withAutoAcl(true)
  }

  case class Item(name: String, quantity: Int)

  object Item {
    implicit val format: Format[Item] = Json.format
  }

  object ItemSerializerRegistry extends JsonSerializerRegistry {

    import Item._
    override def serializers: Seq[JsonSerializer[_]] = Seq(

      JsonSerializer[Item]
    )
  }
}





