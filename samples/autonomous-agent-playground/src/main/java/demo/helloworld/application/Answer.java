package demo.helloworld.application;

// tag::class[]
/** Typed result for question answering tasks. */
public record Answer(String answer, int confidence) {}
// end::class[]
