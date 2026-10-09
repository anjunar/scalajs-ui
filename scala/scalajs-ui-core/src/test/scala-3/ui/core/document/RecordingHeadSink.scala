package ui.core.document

import scala.collection.mutable

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
private final class RecordingHeadSink extends HeadSink {
  private val entriesSeen    = mutable.ArrayBuffer.empty[Seq[HeadEntry]]
  private val attributesSeen = mutable.ArrayBuffer.empty[Seq[(String, String)]]

  def update(entries: Seq[HeadEntry], htmlAttributes: Seq[(String, String)]): Unit = {
    entriesSeen += entries
    attributesSeen += htmlAttributes
  }

  def updates: Int = entriesSeen.length

  def lastEntries: Seq[HeadEntry] = entriesSeen.last

  def lastHtmlAttributes: Seq[(String, String)] = attributesSeen.last
}
