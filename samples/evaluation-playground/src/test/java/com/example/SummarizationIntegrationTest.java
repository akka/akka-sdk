package com.example;

import akka.javasdk.agent.evaluator.SummarizationEvaluator;
import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * There is no guarantee that these tests will always succeed, because of the non-determinism in AI answers.
 */
public class SummarizationIntegrationTest extends TestKitSupport {

  @Test
  public void testBadSummarization1() {
    var result =
        componentClient.forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(SummarizationEvaluator::evaluate)
            .invoke(new SummarizationEvaluator.EvaluationRequest(
                """
                **Omega Corp and Sigma Solutions Announce Definitive Merger Agreement**

                **NEW YORK, NY – October 26, 2023** – Omega Corp (NYSE: OMEGA), a global leader in industrial manufacturing, and Sigma Solutions Inc. (NASDAQ: SGMA), a pioneering technology firm specializing in automation, today announced they have entered into a definitive merger agreement. Under the terms of the agreement, Omega Corp will acquire all outstanding shares of Sigma Solutions for $75.00 per share in cash, representing a total equity value of approximately $3.5 billion. The transaction is expected to close in the first quarter of 2024, pending regulatory approvals and customary closing conditions.
    
                This strategic merger is anticipated to create a synergistic powerhouse, combining Omega Corp's robust production capabilities with Sigma Solutions' cutting-edge automation technologies. The combined entity aims to deliver integrated, smart manufacturing solutions across diverse sectors, enhancing operational efficiency and fostering innovation.
    
                In related news, Omega Corp also recently unveiled a significant investment in renewable energy for its primary manufacturing plants, aiming to reduce its carbon footprint by 30% by 2030. This initiative, part of a broader corporate social responsibility push, includes the installation of solar panels at three key facilities and a transition to electric vehicle fleets for logistics by 2028. This long-term commitment underscores the company's dedication to sustainable practices and environmental stewardship.
                """.stripIndent(),
                """
                    Omega Corp recently unveiled a significant investment in renewable energy for its primary manufacturing plants, aiming to reduce its carbon footprint by 30% by 2030. This initiative includes the installation of solar panels at three key facilities and a transition to electric vehicle fleets for logistics by 2028, showing the company's dedication to sustainable practices.
                    """));


    assertThat(result.passed()).withFailMessage(result.explanation()).isFalse();
  }

  @Test
  public void testGoodSummarization1() {
    var result =
        componentClient.forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(SummarizationEvaluator::evaluate)
            .invoke(new SummarizationEvaluator.EvaluationRequest(
                """
                **Omega Corp and Sigma Solutions Announce Definitive Merger Agreement**

                **NEW YORK, NY – October 26, 2023** – Omega Corp (NYSE: OMEGA), a global leader in industrial manufacturing, and Sigma Solutions Inc. (NASDAQ: SGMA), a pioneering technology firm specializing in automation, today announced they have entered into a definitive merger agreement. Under the terms of the agreement, Omega Corp will acquire all outstanding shares of Sigma Solutions for $75.00 per share in cash, representing a total equity value of approximately $3.5 billion. The transaction is expected to close in the first quarter of 2024, pending regulatory approvals and customary closing conditions.

                This strategic merger is anticipated to create a synergistic powerhouse, combining Omega Corp's robust production capabilities with Sigma Solutions' cutting-edge automation technologies. The combined entity aims to deliver integrated, smart manufacturing solutions across diverse sectors, enhancing operational efficiency and fostering innovation.

                In related news, Omega Corp also recently unveiled a significant investment in renewable energy for its primary manufacturing plants, aiming to reduce its carbon footprint by 30% by 2030. This initiative, part of a broader corporate social responsibility push, includes the installation of solar panels at three key facilities and a transition to electric vehicle fleets for logistics by 2028. This long-term commitment underscores the company's dedication to sustainable practices and environmental stewardship.
                """.stripIndent(),
                """
                    Omega Corp is set to acquire Sigma Solutions for approximately $3.5 billion in cash, at a price of $75.00 per share. The goal of the merger, expected to close in the first quarter of 2024, is to combine Omega's manufacturing with Sigma's automation technology to deliver smart manufacturing solutions. This news follows a separate announcement about Omega Corp's major investment in sustainability.
                    """));


    assertThat(result.passed()).withFailMessage(result.explanation()).isTrue();
  }

  @Test
  public void testBadSummarization2() {
    var result =
    componentClient.forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(SummarizationEvaluator::evaluate)
        .invoke(new SummarizationEvaluator.EvaluationRequest(
            """
            QuantumLeap AI, a leader in computational intelligence, today announced the strategic discontinuation of its flagship 'Odyssey' platform, effective immediately for new clients. The move is part of a significant corporate restructuring designed to reallocate resources towards its next-generation initiative, 'Project Chimera,' which has shown breakthrough performance in internal testing.
            
            The decision was not based on the performance of Odyssey, which remains a robust tool, but on a forward-looking strategy to address a market shift towards fully integrated AI ecosystems. "Our clients are no longer seeking standalone solutions; they need a unified framework that grows with them," said CEO Dr. Aris Thorne. 'Project Chimera' is a unified framework that merges the company's advances in neural synthesis, quantum-inspired optimization, and decentralized data processing into a single, cohesive platform.
            
            Existing Odyssey clients will receive dedicated support and full platform access for the next 12 months. During this period, QuantumLeap AI will offer personalized migration pathways and incentives to transition to Project Chimera, ensuring a seamless and value-additive upgrade for its loyal customer base. The full-scale launch of Project Chimera is slated for the fourth quarter.
            """.stripIndent(),
            """
                QuantumLeap AI announced the strategic discontinuation of its flagship 'Odyssey' platform to reallocate resources towards its next-generation initiative, 'Project Chimera.' This decision was based on a forward-looking strategy to address a market shift towards fully integrated AI ecosystems. 'Project Chimera' is a unified framework that merges the company's advances in neural synthesis, quantum-inspired optimization, and decentralized data processing.
                """));


    assertThat(result.passed()).withFailMessage(result.explanation()).isFalse();
  }

  @Test
  public void testGoodSummarization2() {
    var result =
        componentClient.forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(SummarizationEvaluator::evaluate)
            .invoke(new SummarizationEvaluator.EvaluationRequest(
                """
                QuantumLeap AI, a leader in computational intelligence, today announced the strategic discontinuation of its flagship 'Odyssey' platform, effective immediately for new clients. The move is part of a significant corporate restructuring designed to reallocate resources towards its next-generation initiative, 'Project Chimera,' which has shown breakthrough performance in internal testing.

                The decision was not based on the performance of Odyssey, which remains a robust tool, but on a forward-looking strategy to address a market shift towards fully integrated AI ecosystems. "Our clients are no longer seeking standalone solutions; they need a unified framework that grows with them," said CEO Dr. Aris Thorne. 'Project Chimera' is a unified framework that merges the company's advances in neural synthesis, quantum-inspired optimization, and decentralized data processing into a single, cohesive platform.

                Existing Odyssey clients will receive dedicated support and full platform access for the next 12 months. During this period, QuantumLeap AI will offer personalized migration pathways and incentives to transition to Project Chimera, ensuring a seamless and value-additive upgrade for its loyal customer base. The full-scale launch of Project Chimera is slated for the fourth quarter.
                """.stripIndent(),
                """
                    QuantumLeap AI is discontinuing its 'Odyssey' platform for new clients to strategically focus resources on a new integrated platform called 'Project Chimera'. While the Odyssey platform was performing well, this move addresses a market shift towards unified AI ecosystems. Existing Odyssey clients will be supported for 12 months with migration options to the new platform.
                    """));


    assertThat(result.passed()).withFailMessage(result.explanation()).isTrue();
  }

}
