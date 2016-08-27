package lars.transform

import core.asp.AspRule
import core.lars.{AtAtom, LarsFact, UserDefinedLarsRule}
import engine.asp.PlainLarsToAsp
import org.scalatest.Matchers._

/**
  * Created by FM on 07.05.16.
  */
class RuleOnlySpec extends TransformLarsSpec {

  "A fact" should "be translated in a fact with now(T) in the body" in {
    val f = LarsFact(a)

    PlainLarsToAsp.rule(f).body should contain(now(T))
  }

  it should "transform an at-atom in the head" in {
    val f = LarsFact(AtAtom(t1, a))
    assert(PlainLarsToAsp.rule(f) == AspRule(a(t1), Set(now(T))))
  }

  "A rule containing only positive elements" should "have the same amount of items in its transformed pos. body and now(T)" in {
    val r = UserDefinedLarsRule(a, Set(b, c))
    PlainLarsToAsp.rule(r).pos should have size 3
  }
  "A rule containing only negative elements" should "have the same amount of items in its transformed neg. body" in {
    val r = UserDefinedLarsRule(a, Set(), Set(b, c))
    PlainLarsToAsp.rule(r).neg should have size 2
  }
}

