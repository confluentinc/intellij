package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.settings.fields.WrappedComponent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.event.FocusEvent
import java.io.File
import java.nio.file.Files
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

@TestApplication
@DisplayName("ValidationUtils")
class ValidationUtilsTest {

    private lateinit var disposable: com.intellij.openapi.Disposable

    @BeforeEach
    fun setUp() {
        disposable = Disposer.newDisposable("ValidationUtilsTest")
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    @Nested
    @DisplayName("labelToName")
    inner class LabelToNameTests {

        @Test
        fun `should strip a single trailing colon`() {
            assertEquals("Hostname", labelToName("Hostname:"))
        }

        @Test
        fun `should leave a label without trailing colon untouched`() {
            assertEquals("Hostname", labelToName("Hostname"))
        }

        @Test
        fun `should only strip the final colon, not internal ones`() {
            assertEquals("Foo: Bar", labelToName("Foo: Bar:"))
        }

        @Test
        fun `should return empty string for empty input`() {
            assertEquals("", labelToName(""))
        }

        @Test
        fun `should return empty string for input that is just a colon`() {
            assertEquals("", labelToName(":"))
        }
    }

    @Nested
    @DisplayName("isParentVisible")
    inner class IsParentVisibleTests {

        @Test
        fun `should return true for a visible component with no parent`() {
            val component = JTextField().apply { isVisible = true }
            assertTrue(isParentVisible(component))
        }

        @Test
        fun `should return false when the component itself is invisible`() {
            val component = JTextField().apply { isVisible = false }
            assertFalse(isParentVisible(component))
        }

        @Test
        fun `should return true when component and all parents are visible`() {
            val outer = JPanel().apply { isVisible = true }
            val middle = JPanel().apply { isVisible = true }
            val inner = JTextField().apply { isVisible = true }
            outer.add(middle)
            middle.add(inner)

            assertTrue(isParentVisible(inner))
        }

        @Test
        fun `should return false when any ancestor is invisible`() {
            val outer = JPanel().apply { isVisible = false }
            val middle = JPanel().apply { isVisible = true }
            val inner = JTextField().apply { isVisible = true }
            outer.add(middle)
            middle.add(inner)

            assertFalse(isParentVisible(inner))
        }
    }

    @Nested
    @DisplayName("isValidateable")
    inner class IsValidateableTests {

        @Test
        fun `should be true when component is enabled and parents visible`() {
            val component = JTextField().apply {
                isVisible = true
                isEnabled = true
            }
            assertTrue(isValidateable(component))
        }

        @Test
        fun `should be false when component is disabled`() {
            val component = JTextField().apply {
                isVisible = true
                isEnabled = false
            }
            assertFalse(isValidateable(component))
        }

        @Test
        fun `should be false when component is invisible`() {
            val component = JTextField().apply {
                isVisible = false
                isEnabled = true
            }
            assertFalse(isValidateable(component))
        }
    }

    @Nested
    @DisplayName("buildValidator")
    inner class BuildValidatorTests {

        @Test
        fun `string variant should produce null when validator returns null`() {
            val component = JTextField("hello")
            val supplier = buildValidator(component, { component.text }, { _ -> null })

            assertNull(supplier.get())
        }

        @Test
        fun `string variant should wrap error message in ValidationInfo bound to component`() {
            val component = JTextField("bad")
            val supplier = buildValidator(component, { component.text }, { txt ->
                if (txt == "bad") "broken" else null
            })

            val info = supplier.get()
            assertNotNull(info)
            assertEquals("broken", info!!.message)
            assertSame(component, info.component)
        }

        @Test
        fun `JComponent-aware variant should pass the component to the validator`() {
            val component = JTextField("x")
            var receivedComponent: javax.swing.JComponent? = null
            val supplier = buildValidator(component, { component.text }, { c, _ ->
                receivedComponent = c
                "err"
            })

            val info = supplier.get()
            assertNotNull(info)
            assertSame(component, receivedComponent)
            assertSame(component, info!!.component)
        }

        @Test
        fun `InputValidatorEx variant should return null when validator is null`() {
            val component = JTextField("x")
            val supplier = buildValidator(component, { component.text }, null as InputValidatorEx?)

            assertNull(supplier.get())
        }

        @Test
        fun `InputValidatorEx variant should propagate error text`() {
            val component = JTextField("x")
            val validator = object : InputValidatorEx {
                override fun getErrorText(inputString: String?): String? =
                    if (inputString == "x") "no x allowed" else null
            }
            val supplier = buildValidator(component, { component.text }, validator)

            val info = supplier.get()
            assertNotNull(info)
            assertEquals("no x allowed", info!!.message)
            assertSame(component, info.component)
        }

        @Test
        fun `InputValidatorEx variant should return null when validator reports no error`() {
            val component = JTextField("good")
            val validator = object : InputValidatorEx {
                override fun getErrorText(inputString: String?): String? = null
            }
            val supplier = buildValidator(component, { component.text }, validator)

            assertNull(supplier.get())
        }
    }

    @Nested
    @DisplayName("JTextComponent.withValidator")
    inner class JTextComponentWithValidatorTests {

        @Test
        fun `should install a ComponentValidator on the component`() {
            val field = JTextField("ok")
            field.withValidator(disposable) { if (it == "bad") "err" else null }

            assertNotNull(ComponentValidator.getInstance(field).orElse(null))
        }

        @Test
        fun `getValidator extension should return the installed validator`() {
            val field = JTextField("ok")
            field.withValidator(disposable) { null }

            assertNotNull(field.getValidator())
        }

        @Test
        fun `getValidator should return null when nothing is installed`() {
            val field = JTextField("ok")
            assertNull(field.getValidator())
        }
    }

    @Nested
    @DisplayName("JTextComponent.withPositiveIntValidator")
    inner class PositiveIntValidatorTests {

        @Test
        fun `should accept a non-negative integer`() {
            val field = enabledField("42").also { it.withPositiveIntValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNull(field.getValidationInfo())
        }

        @Test
        fun `should accept zero`() {
            val field = enabledField("0").also { it.withPositiveIntValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNull(field.getValidationInfo())
        }

        @Test
        fun `should reject a negative integer`() {
            val field = enabledField("-3").also { it.withPositiveIntValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNotNull(field.getValidationInfo())
        }

        @Test
        fun `should reject non-numeric text`() {
            val field = enabledField("abc").also { it.withPositiveIntValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNotNull(field.getValidationInfo())
        }

        @Test
        fun `should reject empty input`() {
            val field = enabledField("").also { it.withPositiveIntValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNotNull(field.getValidationInfo())
        }
    }

    @Nested
    @DisplayName("JTextComponent.withNumberOrEmptyValidator")
    inner class NumberOrEmptyValidatorTests {

        @Test
        fun `should accept empty input`() {
            val field = enabledField("").also { it.withNumberOrEmptyValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNull(field.getValidationInfo())
        }

        @Test
        fun `should accept a valid number`() {
            val field = enabledField("123").also { it.withNumberOrEmptyValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNull(field.getValidationInfo())
        }

        @Test
        fun `should reject non-numeric text`() {
            val field = enabledField("12x").also { it.withNumberOrEmptyValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNotNull(field.getValidationInfo())
        }
    }

    @Nested
    @DisplayName("JTextComponent.withNonEmptyValidator")
    inner class NonEmptyValidatorTests {

        @Test
        fun `should accept non-empty input`() {
            val field = enabledField("anything").also { it.withNonEmptyValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNull(field.getValidationInfo())
        }

        @Test
        fun `should reject blank input`() {
            val field = enabledField("   ").also { it.withNonEmptyValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNotNull(field.getValidationInfo())
        }

        @Test
        fun `should reject empty input`() {
            val field = enabledField("").also { it.withNonEmptyValidator(disposable) }
            startValidatorViaFocusLoss(field)
            assertNotNull(field.getValidationInfo())
        }
    }

    @Nested
    @DisplayName("JTextComponent.withEmptyOrFileExistValidator")
    inner class FileExistValidatorTests {

        @Test
        fun `should accept blank when canBeEmpty is true`() {
            val field = enabledField("")
            field.withEmptyOrFileExistValidator(disposable, canBeEmpty = true)
            startValidatorViaFocusLoss(field)
            assertNull(field.getValidationInfo())
        }

        @Test
        fun `should reject blank when canBeEmpty is false`() {
            val field = enabledField("")
            field.withEmptyOrFileExistValidator(disposable, canBeEmpty = false)
            startValidatorViaFocusLoss(field)
            assertNotNull(field.getValidationInfo())
        }

        @Test
        fun `should accept a path to a file that exists`() {
            val tempFile = Files.createTempFile("validation-utils-test", ".txt").toFile()
            try {
                val field = enabledField(tempFile.absolutePath)
                field.withEmptyOrFileExistValidator(disposable, canBeEmpty = false)
                startValidatorViaFocusLoss(field)
                assertNull(field.getValidationInfo())
            } finally {
                tempFile.delete()
            }
        }

        @Test
        fun `should reject a path to a file that does not exist`() {
            val missing = File(System.getProperty("java.io.tmpdir"), "definitely-does-not-exist-${System.nanoTime()}.txt")
            val field = enabledField(missing.absolutePath)
            field.withEmptyOrFileExistValidator(disposable, canBeEmpty = false)
            startValidatorViaFocusLoss(field)
            assertNotNull(field.getValidationInfo())
        }
    }

    @Nested
    @DisplayName("getValidationInfo")
    inner class GetValidationInfoTests {

        @Test
        fun `should return null when component is disabled`() {
            val field = enabledField("bad").also { it.withNonEmptyValidator(disposable) }
            field.isEnabled = false

            assertNull(field.getValidationInfo())
        }

        @Test
        fun `should return null when component is invisible`() {
            val field = enabledField("bad").also { it.withNonEmptyValidator(disposable) }
            field.isVisible = false

            assertNull(field.getValidationInfo())
        }

        @Test
        fun `should return null when no validator is installed`() {
            val field = enabledField("anything")
            assertNull(field.getValidationInfo())
        }
    }

    @Nested
    @DisplayName("revalidateComponent")
    inner class RevalidateComponentTests {

        @Test
        fun `should be a no-op when no validator is installed`() {
            val field = enabledField("x")
            field.revalidateComponent()
            assertNull(ComponentValidator.getInstance(field).orElse(null))
        }

        @Test
        fun `should not throw when validator is installed`() {
            val field = enabledField("").also { it.withNonEmptyValidator(disposable) }
            field.revalidateComponent()
        }
    }

    @Nested
    @DisplayName("revalidateComponentRecursive")
    inner class RevalidateComponentRecursiveTests {

        @Test
        fun `should return true when no children have validators`() {
            val panel = JPanel().apply {
                add(JLabel("label"))
                add(JCheckBox("box"))
            }
            assertTrue(panel.revalidateComponentRecursive())
        }

        @Test
        fun `should return true when child validators all pass`() {
            val good = enabledField("not empty").also { it.withNonEmptyValidator(disposable) }
            val panel = JPanel().apply { add(good) }

            assertTrue(panel.revalidateComponentRecursive())
        }

        @Test
        fun `should return false when any descendant has a validation error`() {
            val bad = enabledField("").also { it.withNonEmptyValidator(disposable) }
            val good = enabledField("filled").also { it.withNonEmptyValidator(disposable) }
            startValidatorViaFocusLoss(bad)
            startValidatorViaFocusLoss(good)
            val panel = JPanel().apply {
                add(good)
                add(bad)
            }

            assertFalse(panel.revalidateComponentRecursive())
        }
    }

    @Nested
    @DisplayName("getValidationInfos and getValidationErrors")
    inner class WrappedComponentValidationTests {

        @Test
        fun `getValidationInfos should be empty when no wrapped components have errors`() {
            val good = enabledField("ok").also { it.withNonEmptyValidator(disposable) }
            val wrapped = TestWrappedComponent(good)

            assertTrue(getValidationInfos(listOf(wrapped)).isEmpty())
        }

        @Test
        fun `getValidationInfos should collect errors from wrapped components`() {
            val bad = enabledField("").also { it.withNonEmptyValidator(disposable) }
            startValidatorViaFocusLoss(bad)
            val wrapped = TestWrappedComponent(bad)

            val infos = getValidationInfos(listOf(wrapped))
            assertEquals(1, infos.size)
        }

        @Test
        fun `getValidationInfos should ignore wrapped components that are not validateable`() {
            val bad = enabledField("").also { it.withNonEmptyValidator(disposable) }
            bad.isEnabled = false
            val wrapped = TestWrappedComponent(bad)

            assertTrue(getValidationInfos(listOf(wrapped)).isEmpty())
        }

        @Test
        fun `getValidationErrors should exclude warnings`() {
            val warningField = enabledField("x")
            val installedValidator = ComponentValidator(disposable)
                .withValidator(java.util.function.Supplier {
                    ValidationInfo("warn", warningField).asWarning()
                })
                .installOn(warningField)
            // Force first evaluation.
            installedValidator.revalidate()

            val errorField = enabledField("").also { it.withNonEmptyValidator(disposable) }
            startValidatorViaFocusLoss(errorField)

            val wrappedWarning = TestWrappedComponent(warningField)
            val wrappedError = TestWrappedComponent(errorField)

            val errors = getValidationErrors(listOf(wrappedWarning, wrappedError))
            assertEquals(1, errors.size)
            assertFalse(errors[0].warning)
        }
    }

    @Nested
    @DisplayName("SKIP_VALIDATION key")
    inner class SkipValidationKeyTests {

        @Test
        fun `should be the configured user-data key`() {
            val field = JTextField()
            field.putClientProperty(SKIP_VALIDATION, true)
            assertEquals(true, field.getClientProperty(SKIP_VALIDATION))
        }
    }

    private fun enabledField(text: String): JTextField = JTextField(text).apply {
        isEnabled = true
        isVisible = true
    }

    /**
     * `ComponentValidator.andStartOnFocusLost()` only starts validating after a real focus-lost event.
     * In a headless test we never get one, so synthesize one to push the validator into its started state.
     */
    private fun startValidatorViaFocusLoss(field: javax.swing.JComponent) {
        val event = FocusEvent(field, FocusEvent.FOCUS_LOST)
        for (listener in field.focusListeners) {
            listener.focusLost(event)
        }
    }

    private class TestWrappedComponent(
        private val component: javax.swing.JComponent
    ) : WrappedComponent<io.confluent.intellijplugin.core.settings.connections.ConnectionData>(
        io.confluent.intellijplugin.core.settings.ModificationKey("test")
    ) {
        override fun getValue(): Any? = null
        override fun getComponent(): javax.swing.JComponent = component
        override fun apply(conn: io.confluent.intellijplugin.core.settings.connections.ConnectionData) {}
        override fun isModified(conn: io.confluent.intellijplugin.core.settings.connections.ConnectionData): Boolean = false
    }
}
