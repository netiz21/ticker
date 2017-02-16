package clingo.reactive

import clingo.{ClingoAtom, ClingoConversion, ClingoExpression}
import core._
import core.asp.NormalRule
import engine.asp.LarsProgramEncoding

/**
  * Created by fm on 25/01/2017.
  *
  * Representation for Clingo Output
  */
object ClingoSignal {

  def fromAtom(atom: Atom): ClingoSignal = atom match {
    case GroundAtomWithArguments(p, args) => ClingoSignal(p, args)
    case NonGroundAtomWithArguments(p, args) => ClingoSignal(p, convert(p, args))
    case _ => ClingoSignal(atom.predicate, Seq())
  }

  def convert(predicate: Predicate, arguments: Seq[Argument]): Seq[Argument] = arguments.zipWithIndex.collect {
    case (v: Value, _) => v
    case (_: Variable, index) => TickParameter(deriveCaption(predicate, index))
  }

  private def deriveCaption(predicate: Predicate, index: Int) = lowerCasedPredicate(predicate) + "_" + index

  private def lowerCasedPredicate(predicate: Predicate) = predicate.caption.head.toLower + predicate.caption.tail
}

case class ClingoSignal(predicate: Predicate, arguments: Seq[Argument] = Seq()) {
  val extAtPredicate: ClingoAtom = f"${predicate}_ext_at"
  val atPredicate: ClingoAtom = f"${predicate}_at"
  //format: a(X,t) //TODO hb review: why is the type ClingoAtom, not ClingoPredicate, or simply Predicate?
  val cntPredicate: ClingoAtom = f"${predicate}_cnt"
  //format: a(X,c)
  val cntPinPredicate: ClingoAtom = f"${predicate}_at_cnt"
  //TODO hb review rename here and in python! --> "_pin" //format: a(X,t,c)
  val programPart = f"signals_${predicate}_${arguments.size}"
  //#program ...
  val parameters: Seq[TickParameter] = arguments collect {
    //TODO hb filter?o
    case t: TickParameter => t
  }
}

object TickParameter {
  def fromName(parameter: String): Either[String, TickParameter] = {
    if (parameter.exists(_.isWhitespace))
      Left("Parameter cannot contain whitespaces")
    else
      Right(TickParameter(parameter))
  }
}

case class TickParameter private[clingo](name: String) extends Variable {
  override def toString: ClingoExpression = name
}

case class TickDimension(predicate: Predicate, parameter: TickParameter) {
  override def toString: ClingoExpression = f"$predicate($parameter)"
}

object ReactiveClingoProgram {
  def fromMapped(program: LarsProgramEncoding) = {
    val rules: Set[NormalRule] = program.rules.toSet ++ program.windowAtomEncoders.flatMap(_.allWindowRules.toSet)

    val volatileRules = rules map (ClingoConversion(_))

    val signalAtoms = program.windowAtoms.
      map(_.atom).
      map(ClingoSignal.fromAtom).
      toSet

    ReactiveClingoProgram(volatileRules, signalAtoms)
  }
}

case class ReactiveClingoProgram(volatileRules: Set[ClingoExpression], signals: Set[ClingoSignal]) {

  val timeDimension: TickDimension = TickDimension(Predicate("now"), TickParameter("t"))
  val countDimension: TickDimension = TickDimension(Predicate("cnt"), TickParameter("c"))

  val tickDimensions = Seq(timeDimension, countDimension)
  val tickParameters: Seq[TickParameter] = tickDimensions map (_.parameter)

  val atExternalMappingRules = signals map (s => f"${s.atPredicate}(${argumentList(s.arguments :+ TickParameter("T"))}) :- ${s.extAtPredicate}(${argumentList(s.arguments :+ TickParameter("T"))}).")

  def externalKeyword(tickDimension: TickDimension): String = externalKeyword(tickDimension.predicate.toString, Seq(tickDimension.parameter))

  def externalKeyword(atom: ClingoAtom, arguments: Seq[Argument]): String = f"#external $atom(${argumentList(arguments)})."

  val externalDimensions: Seq[ClingoExpression] = tickDimensions map externalKeyword

  //TODO hb later cnt should be added only if tuple-window is used
  val signalPrograms: Set[ClingoExpression] = signals map { signal => //TODO hb review argumentList order swap
    f"""#program ${signal.programPart}(${argumentList(tickParameters ++ signal.parameters)}).
       |
       |${externalKeyword(signal.extAtPredicate, signal.arguments :+ timeDimension.parameter)}
       |${externalKeyword(signal.cntPredicate, signal.arguments :+ countDimension.parameter)}
       |${externalKeyword(signal.cntPinPredicate, signal.arguments ++ tickDimensions.map(_.parameter))}
       |
     """.stripMargin
  }

  private val newLine = System.lineSeparator()
  val program: ClingoExpression =
    f"""${signalPrograms.mkString(newLine)}
       |
       |#program volatile(${tickParameters.mkString(", ")}).

       |${externalDimensions.mkString(newLine)}
       |
       |${atExternalMappingRules.mkString(newLine)}
       |
       |${volatileRules.mkString(newLine)}
       |
  """.stripMargin

  private def argumentList(arguments: Seq[Argument]) = arguments.mkString(", ")
}