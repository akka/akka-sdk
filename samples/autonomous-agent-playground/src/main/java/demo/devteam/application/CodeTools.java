package demo.devteam.application;

import akka.javasdk.annotations.FunctionTool;

public class CodeTools {

  @FunctionTool(
      description =
          "Write code for a given file path and description. Returns the code that was written.")
  public String writeCode(String filePath, String description) {
    return "Code written to " + filePath + ": implemented " + description;
  }

  @FunctionTool(description = "Run tests for the project. Returns test results.")
  public String runTests() {
    return "All tests passed (3 tests run, 0 failures)";
  }
}
