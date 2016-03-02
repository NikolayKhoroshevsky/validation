package jto.validation
package free

import cats.{ Cartesian, Functor }
import cats.functor.{ Contravariant, Invariant }

@annotation.implicitNotFound("""
Cannot find an implicit Interpreter 😓
  from: ${I}
  to:   ${G}
""")
trait Interpreter[I, G[_]] {
  type T
  type Out = G[T]
  def apply(is: I): Out
}

object Interpreter {
  type Aux[I0, G0[_], T0] = Interpreter[I0, G0]{ type T = T0 }
  type Of[G[_]] = { type T[α] = Interpreter[α, G] }

  def apply[I0, G0[_], T0](f: I0 => G0[T0]): Interpreter.Aux[I0, G0, T0] =
    new Interpreter[I0, G0]{
      type T = T0
      def apply(is: I0): G0[T0] = f(is)
    }
}

object Grammar {
  case object NotEmpty
  case class Min[T](value: T)
  case class Max[T](value: T)
}

final case class At[F](path: Path, as: F)

trait Is {
  type T
  type V
  val v: V
  override def toString = s"Is($v)"
}

object Is {
  type Aux[T0, V0] = Is {
    type T = T0
    type V = V0
  }

  final class Deferred[T0] {
    def apply[V0](v0: V0): Aux[T0, V0] =
      new Is {
        type T = T0
        type V = V0
        val v = v0
      }
  }

  def apply[T] = new Deferred[T]
  def unapply[T, V](i: Aux[T, V]): Option[V] = Option(i.v)
}

object Interpreters {
  import Grammar._
  type R[T] = Rule[T, T]
  implicit def minI[T: Ordering]: Interpreter.Aux[Is.Aux[T, Min[T]], R, T] =
    Interpreter{ case Is(Min(v)) => GenericRules.min(v) }
  implicit def maxI[T: Ordering]: Interpreter.Aux[Is.Aux[T, Max[T]], R, T] =
    Interpreter{ case Is(Max(v)) => GenericRules.max(v) }
  implicit def notEmptyI: Interpreter.Aux[Is.Aux[String, NotEmpty.type], R, String] =
    Interpreter{ case _ => GenericRules.notEmpty }

  implicit def liftI[I, V, O](implicit pick: Path => Rule[I, O], it: Interpreter.Aux[Is.Aux[O, V], R, O]): Interpreter.Aux[Is.Aux[O, V], λ[α => Path => Rule[I, α]], O] =
    Interpreter[Is.Aux[O, V], λ[α => (Path => Rule[I, α])], O] { is =>
      pick(_).compose(it(is))
    }
}

import shapeless.Poly1
trait Inter[C[_]] extends Poly1 {
  implicit def inter[T, V](implicit int: Interpreter[Is.Aux[T, V], C]) = at[Is.Aux[T, V]] { is =>
    int(is)
  }
}

trait FromI[I] extends Poly1 {
  implicit def ruleFrom[O](implicit r0: Rule[I, O]) = at[Rule[O, O]] { r1 =>
    r0 compose r1
  }
}

trait PickIn[I] extends Poly1 {
  implicit def pick[O](implicit p: Path => Rule[I, I]) = at[Rule[I, O]] { r =>
    (path: Path) => p(path) compose r
  }
}

object FreeVersion {
  import Grammar._
  import cats.free.FreeApplicative
  import FreeApplicative._

  val a1 = At(Path \ "foo", Is[String](NotEmpty))
  val a2 = At(Path \ "bar", Is[Int](Min(3)))
  val a3 = At(Path \ "int", Is[Int](Max(10)))

  import cats.arrow.NaturalTransformation
  import cats.Id

  val toList =
    new NaturalTransformation[At, List] {
      def apply[A](fa: At[A]): List[A] = List(fa.as)
    }

  val free =
    (
      lift(a1) ~
      lift(a2) ~
      lift(a3)
    )

  type R[T] = Rule[T, T]

  type RuleFrom[I] = {
    type T[O] = Path => Rule[I, O]
  }

  type In = Map[String, String]

  /*
  import play.api.libs.json._
  import jto.validation.Path
  import jto.validation.free._, FreeVersion._
  import Interpreters.{ R => _, _ }
  import jto.validation.playjson.Rules._

  object interpretRule extends Inter[R]
  object fromJsValue extends FromI[JsValue]
  object pickInJsValue extends PickIn[JsValue]

  val interpreted =
    free
      .mapEach(interpretRule)
      .mapEach(fromJsValue)
      .mapEach(pickInJsValue)

    interpreted.as[({ type λ[α] = At[Path => α] })#λ]
  */

  // import cats.std.list._
  // val debug = interpreted.tupled.compile(toList).fold

  // import jto.validation._, free._, FreeVersion._, Interpreters._, play.api.libs.json._, jto.validation.json.Rules._
  // free.withImplicits[Interpreter.Of[RuleFrom[JsValue]#T]#T](interpret[RuleFrom[JsValue]#T])
}


