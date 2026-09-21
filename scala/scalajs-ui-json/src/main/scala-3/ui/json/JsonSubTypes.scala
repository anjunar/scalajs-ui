package ui.json

import scala.annotation.StaticAnnotation

final class JsonSubType(val value: Class[?]) extends StaticAnnotation

final class JsonSubTypes(val value: JsonSubType*) extends StaticAnnotation
