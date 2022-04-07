package com.example.inventory.api

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.JsValueMessageSerializer
import com.lightbend.lagom.scaladsl.api.transport.Method
import play.api.libs.json.{Format, Json}

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
  def getItem(itemId: String): ServiceCall[NotUsed, Option[Quantity]]

  def getAllItems(): ServiceCall[NotUsed, List[(String,Int)]]
  /**
   * Add inventory to the given item id.
   */
  def updateStock(itemId: String): ServiceCall[Quantity, Done]

  def addItem(): ServiceCall[InventoryItem, Done]


  final override def descriptor = {
    import Service._

    named("inventory")
      .withCalls(
     //   restCall(Method.GET, "/inventory/:itemId", getItem _),
      //  restCall(Method.GET, "/inventory", getAllItems _),
       // restCall(Method.PUT, "/inventory/:itemId", updateStock _),
        restCall(Method.POST, "/inventory", addItem _)
      )
      .withAutoAcl(true)
  }

  case class Quantity(quantity: Int)

  object Quantity{
    implicit val format: Format[Quantity] = Json.format
  }

}

final case class InventoryItem(itemId: String, quantity: Int)

object InventoryItem {
  implicit val format: Format[InventoryItem] = Json.format

  // For case classes with hand-written companion objects, .tupled only works if
  // you manually extend the correct Scala function type. See SI-3664 and SI-4808.
  def tupled(t: (String, Int)) = InventoryItem(t._1, t._2)
}





