package io.confluent.intellijplugin.core.rfs.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.UIBundle
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import io.confluent.intellijplugin.core.delegate.Delegate
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.settings.buildValidator
import io.confluent.intellijplugin.core.settings.registerValidator
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class TextFieldWithRfsBrowseButton(
    private val project: Project,
    private var targetDriver: Driver,
    @NlsSafe targetFolderPath: String,
    private val availableTargetDrivers: List<Driver> = listOf(targetDriver),
    showFiles: Boolean = false,
    disposable: Disposable
) : ExtendableTextField() {

    private var rfsTargetDriver: Driver = targetDriver

    val rfsTargetFolder: RfsPath
        get() = targetDriver.createRfsPath(text)

    val onNewDriverSelectDelegate = Delegate<Driver, Unit>()

    init {
        text = targetFolderPath.removePrefix("/")

        addExtension(
            ExtendableTextComponent.Extension.create(
                AllIcons.General.OpenDisk,
                AllIcons.General.OpenDisk,
                UIBundle.message("component.with.browse.button.accessible.name")
            ) {
                val descriptor = if (showFiles)
                    RfsChooserDescriptor(false)
                else
                    RfsDirOnlyDescriptor(false)
                val rfsFileChooser = RfsFileChooser(
                    mainTitle = KafkaMessagesBundle.message("rfs.file.chooser.title"),
                    project = project,
                    preselectedDriver = targetDriver,
                    preselectedPath = targetFolderPath,
                    descriptor = descriptor,
                    drivers = availableTargetDrivers
                )
                try {
                    val res = rfsFileChooser.showAndGetResult()?.firstOrNull() ?: return@create
                    text = res.path.stringRepresentation()
                    if (rfsTargetDriver != res.driver) {
                        rfsTargetDriver = res.driver
                        onNewDriverSelectDelegate.notify(res.driver)
                    }
                } finally {
                    rfsFileChooser.disposeIfNeeded()
                }
            })

        text = targetFolderPath.removePrefix("/")

        val validator = buildValidator(this, { text }, { _ ->
            null
        })

        registerValidator(disposable, validator, this)
    }

    fun updateDriver(newTargetDriver: Driver) {
        targetDriver = newTargetDriver
        validate()
    }
}