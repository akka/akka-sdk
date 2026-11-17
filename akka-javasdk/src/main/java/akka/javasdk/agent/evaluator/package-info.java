/**
 * Built-in LLM-as-judge agents ({@link akka.javasdk.agent.evaluator.ToxicityEvaluator}, {@link
 * akka.javasdk.agent.evaluator.SummarizationEvaluator}, {@link
 * akka.javasdk.agent.evaluator.HallucinationEvaluator}) that call a model to judge a piece of text
 * or an answer against a reference. Deprecated: evaluate interactions with an {@link
 * akka.javasdk.evaluation.Evaluator} component that calls a judge agent of your own.
 */
package akka.javasdk.agent.evaluator;
