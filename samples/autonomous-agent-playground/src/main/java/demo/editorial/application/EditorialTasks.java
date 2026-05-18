package demo.editorial.application;

import akka.javasdk.agent.task.Task;

public class EditorialTasks {

  // prettier-ignore
  public static final Task<Article> ARTICLE = Task
    .name("Article")
    .description("Produce a deep-dive article on a technology topic")
    .resultConformsTo(Article.class);

  // prettier-ignore
  public static final Task<ResearchDigest> RESEARCH = Task
    .name("Research")
    .description(
      """
      Commission research on a technology topic from multiple angles and return a \
      consolidated digest with document references.\
      """
    )
    .resultConformsTo(ResearchDigest.class);

  // prettier-ignore
  public static final Task<ResearchFindings> FINDINGS = Task
    .name("Findings")
    .description(
      """
      Research a specific angle of a technology topic. \
      Save findings to the shared workspace and return the document ID.\
      """
    )
    .resultConformsTo(ResearchFindings.class);

  // prettier-ignore
  public static final Task<ArticleDraft> DRAFT = Task
    .name("Draft")
    .description(
      """
      Write a structured article draft from research findings. \
      Look up any referenced documents for source material. Return the assembled draft.\
      """
    )
    .resultConformsTo(ArticleDraft.class);

  // prettier-ignore
  public static final Task<SectionDraft> SECTION = Task
    .name("Section")
    .description(
      """
      Write or edit a section of an article. \
      Look up any document IDs in the instructions for source material. \
      Save the completed section to the shared workspace and return the document ID.\
      """
    )
    .resultConformsTo(SectionDraft.class);

  // prettier-ignore
  public static final Task<ReviewReport> REVIEW = Task
    .name("Review")
    .description(
      """
      Review an article draft for technical accuracy and style, \
      and return consolidated review notes.\
      """
    )
    .resultConformsTo(ReviewReport.class);
}
