package ui.json

import reflect.ClassDescriptor
import reflect.macros.ReflectMacros

import scala.collection.mutable
import scala.quoted.*
import scala.scalajs.reflect.Reflect

object JsonSchemaDerivation {

  def derive[T: Type](using Quotes): Expr[JsonSchema[T]] = {
    import quotes.reflect.*

    val models = mutable.LinkedHashMap.empty[String, TypeRepr]
    val subtypeNames = mutable.LinkedHashMap.empty[String, List[String]]

    def normalized(tpe: TypeRepr): TypeRepr = tpe.widenTermRefByName.dealias

    def isModel(tpe: TypeRepr): Boolean = {
      val name = normalized(tpe).typeSymbol.fullName
      name.nonEmpty &&
      !name.startsWith("scala.") &&
      !name.startsWith("java.") &&
      !name.startsWith("javax.") &&
      !name.startsWith("org.scalajs.") &&
      !name.startsWith("ui.core.")
    }

    def classArguments(annotations: List[Term], annotationName: String): List[TypeRepr] =
      annotations.filter(_.tpe.typeSymbol.fullName == annotationName).flatMap { ann =>
        new TreeAccumulator[List[TypeRepr]] {
          def foldTree(found: List[TypeRepr], tree: Tree)(owner: Symbol): List[TypeRepr] =
            tree match {
              case TypeApply(fun, List(tpt)) if fun.symbol.name == "classOf" => tpt.tpe :: found
              case Literal(ClassOfConstant(subtype)) => subtype :: found
              case _ => foldOverTree(found, tree)(owner)
            }
        }.foldTree(Nil, ann)(Symbol.spliceOwner).reverse
      }

    def subtypeTypes(tpe: TypeRepr): List[TypeRepr] = {
      val annotations = tpe.typeSymbol.annotations
      val subtypes = classArguments(annotations, "ui.json.JsonSubTypes")
      if (annotations.exists(_.tpe.typeSymbol.fullName == "ui.json.JsonSubTypes") && subtypes.isEmpty)
        report.errorAndAbort(s"JsonSubTypes on ${tpe.show} must list concrete classes")
      subtypes
    }

    def fieldModels(tpe: TypeRepr): List[TypeRepr] = normalized(tpe) match {
      case AppliedType(raw, args) if !isModel(raw) => args.flatMap(fieldModels)
      case current if isModel(current) => List(current)
      case _ => Nil
    }

    def visit(tpe: TypeRepr): Unit = {
      val current = normalized(tpe)
      current match {
        case AppliedType(raw, args) if !isModel(raw) => args.foreach(visit)
        case _ if isModel(current) =>
          val name = current.typeSymbol.fullName
          if (!models.contains(name)) {
            models(name) = current
            val subtypes = subtypeTypes(current)
            subtypes.foreach { subtype =>
              if (!(subtype <:< current))
                report.errorAndAbort(s"${subtype.show} is not a subtype of ${current.show}")
            }
            subtypeNames(name) = subtypes.map(_.typeSymbol.fullName)
            subtypes.foreach(visit)
            current.typeSymbol.fieldMembers
              .filterNot(symbol => symbol.flags.is(Flags.Private) || symbol.flags.is(Flags.Protected))
              .foreach { symbol =>
                val fieldType = current.memberType(symbol)
                visit(fieldType)
                classArguments(symbol.annotations, "ui.json.JsonProperty").foreach { hint =>
                  val bases = fieldModels(fieldType)
                  val matching = bases.filter(base => hint <:< base)
                  if (matching.isEmpty)
                    report.errorAndAbort(s"${hint.show} is not compatible with ${current.show}.${symbol.name}")
                  matching.foreach { base =>
                    if (!base.typeSymbol.flags.is(Flags.Abstract) && !(hint =:= base))
                      report.errorAndAbort(s"${current.show}.${symbol.name} must declare an abstract type for ${hint.show}")
                  }
                  visit(hint)
                  matching.foreach { base =>
                    val baseName = base.typeSymbol.fullName
                    subtypeNames(baseName) = (subtypeNames.getOrElse(baseName, Nil) :+ hint.typeSymbol.fullName).distinct
                  }
                }
              }
          }
        case AppliedType(_, args) => args.foreach(visit)
        case _ =>
      }
    }

    if (!isModel(TypeRepr.of[T]))
      report.errorAndAbort(s"JsonSchema cannot be derived for ${TypeRepr.of[T].show}")
    visit(TypeRepr.of[T])

    val descriptors = models.values.toList.map { model =>
      model.asType match {
        case '[m] =>
          val factory = defaultFactory[m]
          val runtimeClass = Literal(ClassOfConstant(model)).asExprOf[Class[?]]
          if (model.typeSymbol.flags.is(Flags.Abstract))
            '{
              val descriptor = ReflectMacros.reflectWithAccessors[m]
              descriptor.bindRuntimeClass($runtimeClass)
            }
          else
            '{
              val descriptor = ReflectMacros.reflectWithAccessors[m]
              descriptor.bindRuntimeClass($runtimeClass)
              descriptor.bindFactory(() =>
                Reflect.lookupInstantiatableClass(descriptor.typeName)
                  .flatMap(_.getConstructor())
                  .map(_.newInstance())
                  .getOrElse($factory())
              )
            }
      }
    }

    val subtypeEntries = subtypeNames.toList.map { case (name, children) =>
      '{ (${Expr(name)}, Seq(${Varargs(children.map(Expr(_)))}*)) }
    }

    '{
      JsonSchema.fromDerivedDescriptors[T](
        Seq(${Varargs(descriptors)}*),
        Map(${Varargs(subtypeEntries)}*)
      )
    }
  }

  private def defaultFactory[T: Type](using Quotes): Expr[() => Any] = {
    import quotes.reflect.*

    val symbol = TypeRepr.of[T].typeSymbol
    if (symbol.flags.is(Flags.Abstract))
      return '{ () => throw new IllegalArgumentException("Cannot instantiate abstract type") }

    val constructor = symbol.primaryConstructor
    if (constructor == Symbol.noSymbol)
      report.errorAndAbort(s"No constructor for ${symbol.fullName}")

    val parameters = constructor.paramSymss.flatten
    if (parameters.exists(p => !p.flags.is(Flags.HasDefault)))
      report.errorAndAbort(s"${symbol.fullName} needs an explicit JsonSchema factory")

    val arguments = parameters.zipWithIndex.map { case (_, index) =>
      val getter = symbol.companionModule.methodMember(s"$$lessinit$$greater$$default$$${index + 1}").headOption
        .getOrElse(report.errorAndAbort(s"Missing default constructor argument for ${symbol.fullName}"))
      Select(Ref(symbol.companionModule), getter)
    }
    val invocation = Apply(Select(New(TypeTree.of[T]), constructor), arguments)
    '{ () => ${invocation.asExprOf[T]} }
  }
}
