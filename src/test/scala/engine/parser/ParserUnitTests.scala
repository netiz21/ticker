package engine.parser

import engine.parser.wrapper.ParamWrapper
import org.scalatest.FlatSpec

/**
  * Created by et on 18.04.17.
  */
class ParserUnitTests extends FlatSpec {

  private val parser = LarsParser
  private val arith = List("+","-","/","*","%","^")
  private val comp = List("=",">=","<=","!=","<",">")

  //  "newline" should "be accepted" in {}
  //  "newline" should "be rejected" in {}
  "optSpace" should "accept multiple space characters" in {
    assert(parser.parseAll(parser.optSpace,"       ").successful)
  }
  "optSpace" should "accept an empty string" in {
    assert(parser.parseAll(parser.optSpace,"").successful)
  }
  "optSpace" should "reject a string with characters other than space" in {
    assert(!parser.parseAll(parser.optSpace,"this is a string with non space characters").successful)
  }
  "Any non space character" should "be rejected" in {
    assert(!parser.parseAll(parser.optSpace,"this is a string with non space characters").successful)
  }
  "A single or multiple space characters" should "be accepted" in {
    assert(parser.parseAll(parser.space,"       ").successful)
  }
  "Empty string, i.e. zero space characters" should "be rejected" in {
    assert(!parser.parseAll(parser.space,"").successful)
  }


  "lowChar" should "be accepted" in {
    assert(parser.parseAll(parser.lowChar,"u").successful)
  }
  "lowChar" should "be rejected" in {
    assert(!parser.parseAll(parser.lowChar,"U").successful)
  }
  "upperChar" should "be accepted" in {
    assert(parser.parseAll(parser.upperChar,"U").successful)
  }
  "upperChar" should "be rejected" in {
    assert(!parser.parseAll(parser.upperChar,"u").successful)
  }
  "char" should "be accepted" in {
    assert(parser.parseAll(parser.char,"u").successful)
    assert(parser.parseAll(parser.char,"U").successful)
  }
  "char" should "be rejected" in {
    assert(!parser.parseAll(parser.char,"IUIUIU").successful)
    assert(!parser.parseAll(parser.char,"iuiuiu").successful)
  }

  "str" should "be accepted" in {
    assert(parser.parseAll(parser.str,"A_regular_string").successful)
    assert(parser.parseAll(parser.str,"4_str1ng_w1th_numb3rs").successful)
  }
  //  "digit" should "be accepted" in {}
  //  "digit" should "be rejected" in {}
  //  "number" should "be accepted" in {}
  //  "number" should "be rejected" in {}
  "str" should "be rejected" in {
    assert(!parser.parseAll(parser.str,"Strings do not contain whitespaces").successful)
  }


  "lineComment" should "accept c++-style comments" in {
    assert(parser.parse(parser.lineComment,"// foo and bar are words\n comment ends with line").get ==
      "// foo and bar are words\n")
  }
  "lineComment" should "accept latex-style comments" in {
    assert(parser.parse(parser.lineComment,"% foo and bar are words\n comment ends with line").get ==
      "% foo and bar are words\n")
  }
  //  "lineComment" should "be rejected" in {}
  "newBlockComment" should "accept anything that is between /* */ or %* *% or any combination of the two" in {
    assert(parser.parseAll(parser.blockComment,"/* foo\n * bar\n * next line\n *%").successful)
  }
  "blockComment" should "be accepted with c-style block comment" in {
    assert(parser.parseAll(parser.blockComment,"/* random text line one \n\r\r\r\n and in line n */").successful)
  }
  "blockComment" should "be accepted with %* ... *%" in {
    assert(parser.parseAll(parser.blockComment,"%* random text line one \n\r\r\r\n and in line n *%").successful)
  }
  //  "blockComment" should "be rejected" in {}
  "comment" should "be accepted" in {
    assert(parser.parseAll(parser.comment,"/*block comment\n * foo \n */").successful)
    assert(parser.parseAll(parser.comment,"// and a line comment\n").successful)
  }
  //  "comment" should "be rejected" in {}


  "A variable starting with an upper case character" should "be accepted" in {
    assert(parser.parseAll(parser.variable,"V").successful)
    assert(parser.parseAll(parser.variable,"V4ri4_bl3").successful)
  }
  "A variable starting with lower case character" should "be rejected" in {
    assert(!parser.parseAll(parser.variable,"v").successful)
    assert(!parser.parseAll(parser.variable,"1").successful)
    assert(!parser.parseAll(parser.variable,"_").successful)
  }

  "operand" should "be accepted" in {
    assert(parser.parseAll(parser.operand,"Strings").successful)
    assert(parser.parseAll(parser.operand,"10.0").successful)
  }
  "operand" should "be rejected" in {
    val otherSymbols = comp ++ arith ++ List("\n\r ")
    otherSymbols.foreach { c => assert(!parser.parseAll(parser.operand,"String_with_"+c).successful) }
  }
  "arithmetic" should "be accepted" in {
    arith.foreach { c => assert(parser.parseAll(parser.arithmetic,c).successful) }
  }
  //  "arithmetic" should "be rejected" in {}
  "compare" should "be accepted" in {
    comp.foreach { c => assert(parser.parseAll(parser.compare,c).successful) }
  }
  //  "compare" should "be rejected" in {}
  "arithOperation" should "be accepted" in {
    arith.foreach { c => assert(parser.parseAll(parser.arithOperation,"1"+c+"1").successful) }
  }
  "arithOperation" should "be rejected" in {
    comp.foreach { c => assert(!parser.parseAll(parser.arithOperation,"1"+c+"1").successful) }
  }
  "leftOperation" should "be accepted" in {
    assert(parser.parseAll(parser.operation,"1+0=1").successful)
  }
  "leftOperation" should "be rejected" in {
    assert(!parser.parseAll(parser.leftOperation,"1=1+0").successful)
  }
  "rightOperation" should "be accepted" in {
    assert(parser.parseAll(parser.operation,"1=1+0").successful)
  }
  "rightOperation" should "be rejected" in {
    assert(!parser.parseAll(parser.rightOperation,"1+0=1").successful)
  }
  "operation" should "be accepted" in {
    assert(parser.parseAll(parser.operation,"1=1+0").successful)
    assert(parser.parseAll(parser.operation,"1+0=1").successful)
    assert(parser.parseAll(parser.operation,"A=B+C").successful)
    assert(parser.parseAll(parser.operation,"A+B=C").successful)
  }
  "operation" should "be rejected" in {
    assert(!parser.parseAll(parser.operation,"0+1=1+0").successful)
  }

  //  "neg" should "be accepted" in {}
  //  "neg" should "be rejected" in {}

  "param" should "be accepted" in {
    assert(parser.parseAll(parser.param,"1").successful)
    assert(parser.parseAll(parser.param,"1.0").successful)
    assert(parser.parseAll(parser.param,"1.0 Einheit").successful)
  }
  "param" should "be rejected" in {
    assert(!parser.parseAll(parser.param,"random words").successful)
  }
  "predicate" should "be accepted" in {
    assert(parser.parseAll(parser.predicate,"pRediCate").successful)
    assert(parser.parseAll(parser.predicate,"pR3_d1_C4t3").successful)
  }
  "predicate" should "be rejected" in {
    assert(!parser.parseAll(parser.predicate,"No_pRediCate").successful)
    assert(!parser.parseAll(parser.predicate,"4o_pRediCate").successful)
    assert(!parser.parseAll(parser.predicate,"no pRe_di_Cate").successful)
  }
  "atom" should "be accepted" in {
    assert(parser.parseAll(parser.atom,"a").successful)
    assert(parser.parseAll(parser.atom,"not a").successful)
    assert(parser.parseAll(parser.atom,"a(A)").successful)
    assert(parser.parseAll(parser.atom,"not a(A)").successful)
    assert(parser.parseAll(parser.atom,"a(A,B,10)").successful)
    assert(parser.parseAll(parser.atom,"not a(A,B,10)").successful)
  }
  "atom" should "be rejected" in {
    assert(!parser.parseAll(parser.atom,"A").successful)
    assert(!parser.parseAll(parser.atom,"not A").successful)
    assert(!parser.parseAll(parser.atom,"A(A)").successful)
    assert(!parser.parseAll(parser.atom,"not A(A)").successful)
  }
  "atAtom" should "be accepted" in {
    assert(parser.parseAll(parser.atAtom,"a at T").successful)
    assert(parser.parseAll(parser.atAtom,"not a at T").successful)
    assert(parser.parseAll(parser.atAtom,"a at 10").successful)
    assert(parser.parseAll(parser.atAtom,"a(A) at T").successful)
    assert(parser.parseAll(parser.atAtom,"not a(10,A,C) at T").successful)
  }
  "atAtom" should "be rejected" in {
    assert(!parser.parseAll(parser.atAtom,"a at").successful)
    assert(!parser.parseAll(parser.atAtom,"a T").successful)
    assert(!parser.parseAll(parser.atAtom,"not a T").successful)
    assert(!parser.parseAll(parser.atAtom,"a 10").successful)
  }
  "window" should "be accepted" in {
    val slidingTimeWithUnit = parser.parseAll(parser.window," [5 sec]")
    assert(slidingTimeWithUnit.get.w == "t")
    assert(slidingTimeWithUnit.get.params == List(ParamWrapper("5","sec")))

    val slidingTuple = parser.parseAll(parser.window," [5 #]")
    assert(slidingTuple.get.w == "#")
    assert(slidingTuple.get.params == List(ParamWrapper("5")))

    val slidingTimeWithoutUnit = parser.parseAll(parser.window," [5]")
    assert(slidingTimeWithoutUnit.get.w == "t")
    assert(slidingTimeWithoutUnit.get.params == List(ParamWrapper("5")))

    assert(parser.parseAll(parser.window," [5 sec]").successful)
    assert(parser.parseAll(parser.window," [5 #]").successful)
    assert(parser.parseAll(parser.window," [5]").successful)
    assert(parser.parseAll(parser.window," [t]").successful)
    assert(parser.parseAll(parser.window," [t 5]").successful)
    assert(parser.parseAll(parser.window," [t 5 sec]").successful)
    assert(parser.parseAll(parser.window," [t 5 sec,10 min,4]").successful)
    assert(parser.parseAll(parser.window," [t 5, 10 ,4]").successful)
  }
  "window" should "be rejected" in {
    assert(!parser.parseAll(parser.window," []").successful)
    assert(!parser.parseAll(parser.window," [5.0]").successful)
    assert(!parser.parseAll(parser.window," [-5]").successful)
  }
  "optIn" should "be rejected" in {
    assert(!parser.parseAll(parser.optNotIn,"in ").successful)
    assert(!parser.parseAll(parser.optNotIn," in").successful)
    assert(!parser.parseAll(parser.optNotIn,"in").successful)
    assert(!parser.parseAll(parser.optNotIn,"not in").successful)
  }
  "optNotIn" should "be accepted" in {
    assert(parser.parseAll(parser.optNotIn," in ").successful)
    assert(parser.parseAll(parser.optNotIn,"                       in ").successful)
    assert(parser.parseAll(parser.optNotIn," in                       ").successful)
    assert(parser.parseAll(parser.optNotIn," not in ").successful)
    assert(parser.parseAll(parser.optNotIn," not         in                       ").successful)
    assert(parser.parseAll(parser.optNotIn," not ").successful)
  }
  "boxWAtom" should "be accepted" in {
    assert(parser.parseAll(parser.boxWAtom,"a always in [t]").successful)
    assert(parser.parseAll(parser.boxWAtom,"a always not in [t]").successful)
    assert(parser.parseAll(parser.boxWAtom,"not a always in [t]").successful)
    assert(parser.parseAll(parser.boxWAtom,"not a always not in [t]").successful)
    assert(parser.parseAll(parser.boxWAtom,"a always [t]").successful)
    assert(parser.parseAll(parser.boxWAtom,"a always [t 5]").successful)
    assert(parser.parseAll(parser.boxWAtom,"a always [t 5]").successful)
    assert(parser.parseAll(parser.boxWAtom,"a always[t 5]").successful)
  }
  //  "boxWAtom" should "be rejected" in {}
  "diamWAtom" should "be accepted" in {
    assert(parser.parseAll(parser.diamWAtom,"a in [t]").successful)
    assert(parser.parseAll(parser.diamWAtom,"a not in [t]").successful)
    assert(parser.parseAll(parser.diamWAtom,"not a in [t]").successful)
    assert(parser.parseAll(parser.diamWAtom,"not a not in [t]").successful)
    assert(parser.parseAll(parser.diamWAtom,"a [t]").successful)
    assert(parser.parseAll(parser.diamWAtom,"a [t 5]").successful)
    assert(parser.parseAll(parser.diamWAtom,"a [t 5]").successful)
    assert(parser.parseAll(parser.diamWAtom,"a[t 5]").successful)
    assert(parser.parseAll(parser.diamWAtom,"a(A)[t 5]").successful)
  }
  "diamWAtom" should "be rejected" in {
    assert(!parser.parseAll(parser.diamWAtom,"a []").successful)
  }
  "atWAtom" should "be accepted" in {
    assert(parser.parseAll(parser.atWAtom,"a at T in [t]").successful)
    assert(parser.parseAll(parser.atWAtom,"a at T not in [t]").successful)
    assert(parser.parseAll(parser.atWAtom,"not a at T in [t]").successful)
    assert(parser.parseAll(parser.atWAtom,"not a at T not in [t]").successful)
    assert(parser.parseAll(parser.atWAtom,"a at T [t]").successful)
    assert(parser.parseAll(parser.atWAtom,"a at T [t 5]").successful)
    assert(parser.parseAll(parser.atWAtom,"a at 10 [t 5]").successful)
  }
  "atWAtom" should "be rejected" in {
    assert(!parser.parseAll(parser.atWAtom,"a at [t]").successful)
  }

  //  "bodyElement" should "be accepted" in {}
  //  "bodyElement" should "be rejected" in {}
  "body" should "be accepted" in {
    assert(parser.parseAll(parser.body,"a,not b,c at T,d always in [t], e at T in [t 5], f in [t], g [t], " +
      "h not in [t], not i always not [t], 10 = 5+5, A<B, C = 1+2, C > D").successful)
  }
  //  "body" should "be rejected" in {}
  //  "head" should "be accepted" in {}
  //  "head" should "be rejected" in {}
  //  "ruleBase" should "be accepted" in {}
  //  "ruleBase" should "be rejected" in {}
  "rule" should "be accepted" in {
    assert(parser.parseAll(parser.rule,"a :- b.").successful)
    assert(parser.parseAll(parser.rule,"/*comment?*/a :- b.").successful)
    assert(parser.parseAll(parser.rule,"a :- not b.").successful)
    assert(parser.parseAll(parser.rule,"a.").successful)
    assert(parser.parseAll(parser.rule,"a.//fooo\n").successful)
  }
  "rule" should "be rejected" in {
    assert(!parser.parseAll(parser.rule,":- b.").successful)
    assert(!parser.parseAll(parser.rule,"a :- b").successful)
    assert(!parser.parseAll(parser.rule,"a").successful)
    assert(!parser.parseAll(parser.rule,"a :- /* comment */ b.").successful)
  }

  "fqdn" should "be accepted" in {
    assert(parser.parseAll(parser.fqdn,"head.string1.string2.last").successful)
    assert(parser.parseAll(parser.fqdn,"head").successful)
  }
  "fqdn" should "be rejected" in {
    assert(!parser.parseAll(parser.fqdn,"head.string1 . string2.last").successful)
  }
  "importN" should "be accepted" in {
    assert(parser.parseAll(parser.importN,"import engine.parser.factories.slidingWindowFunctionFactory.SlidingTimeWindowFactory as stw\n").successful)
  }

  "importN" should "be rejected" in {
    assert(!parser.parseAll(parser.importN,"import engine.parser.factories.slidingWindowFunctionFactory.SlidingTimeWindowFactory as stw").successful)
    assert(!parser.parseAll(parser.importN,"engine.parser.factories.slidingWindowFunctionFactory.SlidingTimeWindowFactory as stw\n").successful)
    assert(!parser.parseAll(parser.importN,"import engine.parser.factories.slidingWindowFunctionFactory.SlidingTimeWindowFactory stw\n").successful)
    assert(!parser.parseAll(parser.importN,"import engine.parser.factories.slidingWindowFunctionFactory.SlidingTimeWindowFactory as\n").successful)
    assert(!parser.parseAll(parser.importN,"import engine.parser.factories.slidingWindowFunctionFactory.SlidingTimeWindowFactory as \n").successful)
  }

  "program" should "be accepted" in {
    assert(parser.parseAll(parser.program,"import engine.parser.factories.slidingWindowFunctionFactory.SlidingTimeWindowFactory as stw\n" +
      "import engine.parser.factories.slidingWindowFunctionFactory.SlidingTupleWindowFactory as tup\n" +
      "/* random comment\n" +
      " * over multiple\n" +
      " * lines\n" +
      " */\n" +
      "\n" +
      "a :- b.\n" +
      "c at 2399 :- a in [t 5], d, e at 25000, f always in [tup 30].//foo\n").successful)
  }
//  "program" should "be rejected" in {}
}