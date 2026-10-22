package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ThreatEvaluationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FocusSense", appName)
  }

  @Test
  fun `threat engine detects stranger risk`() {
    val engine = ThreatEvaluationEngine()
    val result = engine.evaluateLayer2MobileBertML(
      appName = "Discord",
      contentTitle = "Direct Message",
      extractedText = "Hey are you alone? Don't tell your mom or dad, let's meet up at the skatepark behind school at 6pm"
    )
    assertTrue("Should be flagged", result.isFlagged)
    assertTrue("Should contain Stranger Risk", result.threatCategory.contains("Stranger Risk"))
    assertTrue("Confidence should be high", result.confidenceScore >= 0.70f)
  }

  @Test
  fun `threat engine marks educational content as safe`() {
    val engine = ThreatEvaluationEngine()
    val result = engine.evaluateLayer2MobileBertML(
      appName = "YouTube",
      contentTitle = "Khan Academy",
      extractedText = "Introduction to cellular biology and photosynthesis in plant cells"
    )
    assertFalse("Should not be flagged", result.isFlagged)
    assertEquals("Safe", result.threatCategory)
  }

  @Test
  fun `schedule rule blocks restricted package during active hours`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.FocusSenseRepository(context)

    // Add an all-day rule restricting Chrome
    val rule = com.example.data.model.ScheduleRuleEntity(
      ruleId = "test-curfew-1",
      childId = "child-default",
      ruleName = "Study Focus",
      category = "Homework",
      startTime = "00:00",
      endTime = "23:59",
      dayOfWeek = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
      restrictedPackages = "com.android.chrome",
      isActive = true
    )
    repository.addScheduleRule(rule)

    val restriction = repository.checkAppRestriction("child-default", "com.android.chrome")
    assertTrue("com.android.chrome should be blocked during active curfew", restriction.isBlocked)
    assertEquals("Study Focus", restriction.ruleName)

    val allowedRestriction = repository.checkAppRestriction("child-default", "com.example.unrestricted")
    assertFalse("Unrestricted package should not be blocked", allowedRestriction.isBlocked)
  }

  @Test
  fun `instant app lock blocks app immediately`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.FocusSenseRepository(context)

    repository.toggleAppBlock("child-default", "com.google.android.youtube", true)

    assertTrue("Should be blocked by instant lock", repository.isPackageBlockedSync("com.google.android.youtube"))
    val check = repository.checkAppRestriction("child-default", "com.google.android.youtube")
    assertTrue("checkAppRestriction should return blocked", check.isBlocked)

    repository.toggleAppBlock("child-default", "com.google.android.youtube", false)
    assertFalse("Should be unblocked after toggle off", repository.isPackageBlockedSync("com.google.android.youtube"))
  }
}
