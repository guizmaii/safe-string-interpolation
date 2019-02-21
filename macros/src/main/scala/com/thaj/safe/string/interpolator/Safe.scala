package com.thaj.safe.string.interpolator

import scalaz.{@@, IList, NonEmptyList}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait Safe[A] {
  def value(a: A): String
}

object Safe {
  def apply[T](implicit ev: Safe[T]): Safe[T] = ev

  implicit def materializeSafee[T]: Safe[T] = macro materializeSafe[T]

  implicit val safeString: Safe[String] =
    identity[String]

  implicit val safeByte: Safe[Byte] =
    _.toString

  implicit val safeShort: Safe[Short] =
    _.toString

  implicit val safeInt: Safe[Int] =
    _.toString

  implicit val safeLong: Safe[Long] =
    _.toString

  implicit val safeDouble: Safe[Double] =
    _.toString

  implicit val safeFloat: Safe[Float] =
    _.toString

  implicit val safeChar: Safe[Char] =
    _.toString

  implicit val safeBoolean: Safe[Boolean] =
    _.toString

  implicit val safeBigInt: Safe[BigInt] =
    _.toString

  implicit val safeBigDecimal: Safe[BigDecimal] =
    _.toString

  implicit def safeNonEmptyList[A: Safe]: Safe[NonEmptyList[A]] =
    _.map(t => Safe[A].value(t)).list.toList.mkString(",")

  implicit def safeIList[A : Safe]: Safe[IList[A]] =
    l => Safe[List[A]].value(l.toList)

  implicit def safeTagged[A: Safe, T]: Safe[A @@ T] =
    a => Safe[A].value(scalaz.Tag.unwrap(a))

  implicit def safeList[A: Safe]: Safe[List[A]] =
    a => a.map(t => Safe[A].value(t)).mkString(",")

  implicit def safeSet[A: Safe]: Safe[Set[A]] =
    a => a.map(t => Safe[A].value(t)).mkString(",")

  implicit def safeMap[A: Safe, B: Safe]: Safe[Map[A, B]] =
    a => a.map { case (aa, bb) => (Safe[A].value(aa), Safe[B].value(bb)) }.mkString(",")

  def materializeSafe[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Safe[T]] = {
    import c.universe._
    val tpe = weakTypeOf[T]

    val tag = c.WeakTypeTag(tpe)
    val symbol = tag.tpe.typeSymbol

    if (symbol.isClass && symbol.asClass.isCaseClass) {
      val fields = tpe.decls.collectFirst {
        case m: MethodSymbol if m.isPrimaryConstructor ⇒ m
      }
        .getOrElse(
          c.abort(NoPosition, s"Unable to find a safe instance for ${tpe.typeSymbol}. Consider creating one manually.")
        )
        .paramLists.headOption
        .getOrElse(c.abort(NoPosition, s"Unable to find a safe instance for $tpe. Consider creating one manually."))

      if (fields.isEmpty)  {
        val res =
          q"""new com.thaj.safe.string.interpolator.Safe[$tpe] {
         override def value(a: $tpe): String = ${tpe.typeSymbol.name.toString}
      }"""
        c.Expr[Safe[T]] {
          res
        }
      } else {
        val str =
          fields.foldLeft(Set[(TermName, c.universe.Tree)]()) { (str, field) ⇒
            val tag = c.WeakTypeTag(field.typeSignature)

            val fieldName = field.name.toTermName
            str ++ Set((fieldName, q""" com.thaj.safe.string.interpolator.Safe[$tag]"""))
          }

        val res =
          q"""new com.thaj.safe.string.interpolator.Safe[$tpe] {
         override def value(a: $tpe): String = "{ " + ${
            str
              .map { case (x, y) => q""" ${x.toString} + " : " + $y.value(a.$x) """ }
          }.mkString(", ") + " }"
      }"""

        c.Expr[Safe[T]] {
          res
        }
      }
    } else if (symbol.isClass && symbol.asClass.isSealed) {
      val knownSubClasses = symbol.asClass.knownDirectSubclasses

      val cases = knownSubClasses.toList.foldLeft(Nil: List[Tree])(
        (acc, b) => {
          cq"x : ${b.asType} => com.thaj.safe.string.interpolator.Safe[$b].value(x)" :: acc
        }
      )

      val res =
        q"""new com.thaj.safe.string.interpolator.Safe[$tpe] {
         override def value(a: $tpe): String = a match {
           case ..$cases
         }
      }"""

      c.Expr[Safe[T]] {
        res
      }
    }
    else {
      c.abort(NoPosition, s"unable to find a safe instance for ${tpe.typeSymbol}. Make sure it is a case class or a type that has safe instance.")
    }
  }
}