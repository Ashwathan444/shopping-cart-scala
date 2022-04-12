package com.example.inventory.impl

import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import slick.dbio.DBIOAction
import akka.Done
import com.example.inventory.impl.Inventory._
import org.slf4j.LoggerFactory


class InventoryProcessor(readSide: SlickReadSide, repository: InventoryRepository) extends ReadSideProcessor[Event]{

  private val logger = LoggerFactory.getLogger(this.getClass)
  override def buildHandler(): ReadSideProcessor.ReadSideHandler[Event] =
    readSide
      .builder[Event]("inventory-offset")
      .setGlobalPrepare(repository.createTable())
      .setEventHandler[ItemAdded] { envelope =>
        logger.info(s"$envelope added ")
        repository.addStock(envelope.entityId,envelope.event.name, envelope.event.quantity)
      }
      .setEventHandler[StockUpdated] { envelope =>
        logger.info(s"Updated quantity is ${envelope.event.newQuantity}")
        repository.updateStock(envelope.entityId,envelope.event.name,envelope.event.newQuantity)
      }
      .setEventHandler[ItemRemoved] { envelope =>
        logger.info(s"$envelope added ")
        repository.remove(envelope.entityId)
      }
      .build()

  override def aggregateTags: Set[AggregateEventTag[Event]] = Event.Tag.allTags

}
