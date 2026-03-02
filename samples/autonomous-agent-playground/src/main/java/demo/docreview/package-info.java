/**
 * Document Review — autonomous agent with multi-modal content (PDF attachment).
 *
 * <p>Demonstrates attaching a PDF document to a task so the LLM can review it. The agent receives
 * both the text instructions and the PDF content in a single multi-modal user message.
 *
 * <p><b>Capabilities demonstrated:</b> Task attachments ({@code Task.attach()}), ContentRef
 * serialization through event sourcing, multi-modal UserMessage construction.
 */
package demo.docreview;
