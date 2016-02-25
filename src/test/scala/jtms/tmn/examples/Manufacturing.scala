package jtms.tmn.examples

import core.{Program, Rule, Premise, Atom}
import jtms._
import org.scalatest.FlatSpec

/**
  * Created by FM on 11.02.16.
  */
class Manufacturing extends FlatSpec {

  val C = Atom("Product")
  val B = Atom("troubles")
  val A1 = Atom("Resource 1")
  val A2 = Atom("Resource 2")
  val L1 = Atom("supply problems A1")

  val j0 = Rule.in(C).out(B).head(A1)
  val j1 = Rule.in(C, B).head(A2)
  val j2 = Rule.in(L1).head(B)
  val j3 = Rule.premise(C)

  val program = Program(j0, j1, j2, j3)

  def Tmn = {
    val tmn = TMN(program)

    tmn
  }

  "When manufacturing without troubles" should "use resource A1" in {
    val tmn = Tmn

    assert(tmn.getModel() == Set(C, A1))
  }

  "When there are supply problems with A1" should "mark as troubles and use resource A2" in {
    val tmn = Tmn

    tmn.add(Premise(L1))

    assert(tmn.getModel() == Set(C, L1, B, A2))
  }

}
