package simulacrum.jvmexamples

import simulacrum._
import shapeless.test.illTyped

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Examples extends AnyWordSpec with Matchers {

  "the @typeclass annotation" should {

    "Return the correct error message for missing implicit" in {
      @typeclass trait Semigroup[A] {
        @op("|+|") def append(x: A, y: A): A
      }

      illTyped("Semigroup[Float]", "Could not find an instance of Semigroup for Float")
    }
  }
}

