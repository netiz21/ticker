package engine.config

import clingo.ClingoConversion
import core.lars.LarsProgram
import engine.EvaluationEngine
import engine.asp._
import engine.asp.evaluation._
import engine.asp.evaluation.policies.{DirectAddRemovePolicy, TmsPolicy}
import jtms.JtmsExtended

import scala.concurrent.duration.Duration
import scala.util.Random

/**
  * Created by FM on 14.05.16.
  */
object BuildEngine {
  def withProgram(program: LarsProgram) = EngineEvaluationConfiguration(program)
}

case class EngineEvaluationConfiguration(program: LarsProgram) {
  def useAsp() = AspEvaluationEngineConfiguration(PlainLarsToAsp(program))

  def useIncremental() = {
    //TODO
  }
}


case class AspEvaluationEngineConfiguration(aspProgram: MappedProgram) {

  def withClingo() = EvaluationModeConfiguration(StreamingClingoInterpreter(ClingoConversion(aspProgram)))

  def withTms() = AspBasedTmsConfiguration(aspProgram)

}

case class AspBasedTmsConfiguration(program: MappedProgram, policy: TmsPolicy = DirectAddRemovePolicy(JtmsExtended(new Random))) {
  def withRandom(random: Random) = AspBasedTmsConfiguration(program, DirectAddRemovePolicy(JtmsExtended(random)))

  def withTms(jtms: JtmsExtended) = AspBasedTmsConfiguration(program, DirectAddRemovePolicy(jtms))

  def usingPolicy(tmsPolicy: TmsPolicy) = AspBasedTmsConfiguration(program, tmsPolicy)
}

object AspBasedTmsConfiguration {
  implicit def toEvaluationModeConfig(config: AspBasedTmsConfiguration): EvaluationModeConfiguration = EvaluationModeConfiguration(TmsEvaluation(config.program, config.policy))
}

case class EvaluationModeConfiguration(streamingAspInterpreter: StreamingAspInterpreter) {

  def use(evaluationMode: EvaluationMode = Direct) = {
    val aspEvaluation = buildEvaluationMode(AspEvaluationEngine(streamingAspInterpreter), evaluationMode)
    EvaluationStrategyConfiguration(aspEvaluation)
  }

  private def buildEvaluationMode(aspEvaluation: AspEvaluation, evaluationMode: EvaluationMode) = evaluationMode match {
    case UseFuture(waitingAtMost: Duration) => FutureStreamingAspInterpreter(aspEvaluation, waitingAtMost)
    case _ => aspEvaluation
  }
}

case class EvaluationStrategyConfiguration(aspEvaluation: AspEvaluation) {
  def usePull() = StartableEngineConfiguration(AspPullEvaluationEngine(aspEvaluation))

  def usePush() = StartableEngineConfiguration(AspPushEvaluationEngine(aspEvaluation))
}

case class StartableEngineConfiguration(evaluationEngine: EvaluationEngine) {
  def start() = evaluationEngine
}
