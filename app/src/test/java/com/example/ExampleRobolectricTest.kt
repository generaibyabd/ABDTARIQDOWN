package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.EngineOrchestrator
import com.example.engine.SupportedPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    assertEquals("ABDownloader", appName)
  }

  @Test
  fun `test single url validation and multi link rejection`() {
    val orchestrator = EngineOrchestrator()

    // Valid single URLs
    val validYt = orchestrator.validateSingleSupportedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    assertNotNull(validYt)
    assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", validYt)

    val validIg = orchestrator.validateSingleSupportedUrl("https://www.instagram.com/reel/C3zYx12L/")
    assertNotNull(validIg)

    // Invalid: empty or junk
    assertNull(orchestrator.validateSingleSupportedUrl(""))
    assertNull(orchestrator.validateSingleSupportedUrl("just some random text"))

    // Invalid: Multiple URLs in one string must be strictly rejected
    val multiUrls = "https://www.youtube.com/watch?v=123 https://www.youtube.com/watch?v=456"
    assertNull(orchestrator.validateSingleSupportedUrl(multiUrls))
  }

  @Test
  fun `test platform detection`() {
    assertEquals(SupportedPlatform.YOUTUBE, SupportedPlatform.detect("https://youtu.be/dQw4w9WgXcQ"))
    assertEquals(SupportedPlatform.INSTAGRAM, SupportedPlatform.detect("https://instagram.com/p/C9921/"))
    assertEquals(SupportedPlatform.TIKTOK, SupportedPlatform.detect("https://www.tiktok.com/@user/video/123"))
    assertEquals(SupportedPlatform.TWITTER, SupportedPlatform.detect("https://x.com/user/status/12345"))
  }
}

