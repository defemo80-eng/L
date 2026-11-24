package com.example

import com.example.data.model.DeviceActionType
import com.example.data.model.Emotion
import com.example.data.model.MemoryCategory
import com.example.data.model.MessageSender
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun emotion_safeFromRaw_parsesAndFallsBack() {
    assertEquals(Emotion.ENTUSIASMO, Emotion.safeFromRaw("entusiasmo"))
    assertEquals(Emotion.ENOJO_DIVERTIDO, Emotion.safeFromRaw("ENOJO_DIVERTIDO"))
    assertEquals(Emotion.ALEGRIA, Emotion.safeFromRaw("DESCONOCIDA"))
    assertEquals(Emotion.ALEGRIA, Emotion.safeFromRaw(null))
  }

  @Test
  fun memoryCategory_and_deviceAction_safeFromRaw() {
    assertEquals(MemoryCategory.PREFERENCIA, MemoryCategory.safeFromRaw("preferencia"))
    assertEquals(MemoryCategory.DATO, MemoryCategory.safeFromRaw("invalid"))
    assertEquals(DeviceActionType.OPEN_APP, DeviceActionType.safeFromRaw("OPEN_APP"))
    assertEquals(DeviceActionType.NONE, DeviceActionType.safeFromRaw("unknown"))
    assertEquals(MessageSender.USER, MessageSender.safeFromRaw("user"))
  }
}
