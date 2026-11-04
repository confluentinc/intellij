package io.confluent.intellijplugin.common.editor

import com.intellij.icons.AllIcons
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.AnimatedIcon
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
class KafkaProducerConsumerProgressComponentTest {

    private val progress = KafkaProducerConsumerProgressComponent().apply { onStart() }

    @Test
    fun `should keep showing the error after stop`() {
        progress.onError()
        progress.onStop()

        assertSame(AllIcons.General.Error, progress.timestampLabel.icon)
        assertEquals(KafkaMessagesBundle.message("consumer.last.update.label.error"), progress.timestampLabel.text)
    }

    @Test
    fun `should replace the error icon with the running icon on a later update`() {
        progress.onError()
        progress.onUpdate()

        assertSame(AnimatedIcon.Default.INSTANCE, progress.timestampLabel.icon)
    }

    @Test
    fun `should clear the icon on stop after recovering from an error`() {
        progress.onError()
        progress.onUpdate()
        progress.onStop()

        assertEquals(null, progress.timestampLabel.icon)
    }
}
