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
        repository.createItem(envelope.event.itemId, envelope.event.quantity)
      }
      .setEventHandler[StockUpdated] { envelope =>
        DBIOAction.successful(Done)
      }
      .build()

  override def aggregateTags: Set[AggregateEventTag[Event]] = Event.Tag.allTags

}
