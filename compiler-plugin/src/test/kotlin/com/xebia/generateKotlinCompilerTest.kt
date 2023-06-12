package com.xebia

import com.xebia.runners.AbstractBoxTest
import com.xebia.runners.AbstractDiagnosticTest
import org.jetbrains.kotlin.generators.generateTestGroupSuiteWithJUnit5

fun main() {
  generateTestGroupSuiteWithJUnit5 {
    testGroup(testDataRoot = "src/testData", testsRoot = "src/test-gen") {
      testClass<AbstractDiagnosticTest> {
        model("diagnostics")
      }

      testClass<AbstractBoxTest> {
        model("box")
      }
    }
  }
}
