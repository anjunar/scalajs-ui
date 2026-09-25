package ui.core.dsl

import ui.core.state.ReadOnlyProperty

trait ClassDsl {

  def addClass(name: String): Unit

  def getClasses: Seq[String]

  def setClasses(values: Seq[String]): Unit

  def classCondition(name: String, condition: ReadOnlyProperty[Boolean]): Unit

}

object ClassDsl {

  // Compiled once: String.split compiles its regex on every call, and nearly every element sets classes.
  private val WhitespacePattern = "\\s+".r

  def addClass(name: String)(using component: ClassDsl): Unit =
    component.addClass(name)

  def classes(using component: ClassDsl): Seq[String] =
    component.getClasses

  def classes_=(value: Seq[String])(using component: ClassDsl): Unit =
    component.setClasses(value)

  def classes_=(value: String)(using component: ClassDsl): Unit =
    component.setClasses(WhitespacePattern.split(value).filter(_.nonEmpty).toSeq)

  def classIf(name: String, condition: ReadOnlyProperty[Boolean])(using component: ClassDsl): Unit =
    component.classCondition(name, condition)

}
