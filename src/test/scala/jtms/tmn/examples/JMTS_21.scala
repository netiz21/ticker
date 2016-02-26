package jtms.tmn.examples

import aspsamples.EvaluateBothImplementations
import core.{SingleModel, Evaluation, Rule, ContradictionAtom}
import org.scalatest.FlatSpec

/**
  * Created by FM on 11.02.16.
  */
trait JTMS_21Behavior extends JTMSSpec {
  this: FlatSpec =>
  val N_cont = ContradictionAtom("n_cont")

  val j7 = Rule.in(b).out(c).head(N_cont)

  def p = {
    val p = program + j7
    p
  }

  def example21(evaluation: Evaluation): Unit = {
    it should "contain A,C,D,F,E" in {
      val model = evaluation(p)

      assert(model contains SingleModel(Set(a, c, d, f, e)))
    }
  }
}

class JMTS_21 extends JTMSSpec with JTMS_21Behavior with EvaluateBothImplementations {
  "The example 21" should behave like theSame(example21)

}
