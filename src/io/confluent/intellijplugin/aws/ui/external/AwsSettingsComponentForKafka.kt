package io.confluent.intellijplugin.aws.ui.external


import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowsRange
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.layout.selectedValueMatches
import io.confluent.intellijplugin.aws.connection.AwsConnectionUtils
import io.confluent.intellijplugin.aws.connection.auth.AuthenticationType
import io.confluent.intellijplugin.aws.credentials.profiles.loader.BdtProfileReader
import io.confluent.intellijplugin.aws.settings.models.AwsRegionEntity
import io.confluent.intellijplugin.aws.ui.AwsComponentsBuilder
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.core.ui.components.BdtGroupRender
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import software.amazon.awssdk.regions.Region

class AwsSettingsComponentForKafka(
    val includeRegionSetting: Boolean = false,
    val onChanged: (AwsSettingsComponentForKafka) -> Unit
) {
    private val authTypes = listOf(
        AuthenticationType.DEFAULT,
        AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE,
        AuthenticationType.KEY_PAIR
    )
    internal val profiles = try {
        BdtProfileReader.getProfilesNames(null, null)
    } catch (t: Throwable) {
        logger.info(t)
        listOf()
    }

    var region: Cell<ComboBox<AwsRegionEntity?>>? = null

    lateinit var authTypeChooser: Cell<ComboBox<AuthenticationType>>
    lateinit var profileComboBox: Cell<ComboBox<String>>
    lateinit var accessKey: Cell<JBTextField>
    lateinit var secretKey: Cell<JBPasswordField>

    private lateinit var profileRows: RowsRange
    private lateinit var credentialRows: RowsRange

    fun getComponentRows(panel: Panel): Panel = panel.apply {
        if (includeRegionSetting) {
            row(KafkaMessagesBundle.message("settings.region")) {
                val groups = AwsComponentsBuilder.getAwsRegions(null)
                val regionGroups: MutableList<Pair<String, List<AwsRegionEntity?>>> = groups.toMutableList()
                val regions = groups.flatMap { it.second }

                val renderer = object : BdtGroupRender<AwsRegionEntity?>(null, regionGroups) {
                    override fun getText(item: AwsRegionEntity?): String = item?.title ?: ""
                    override fun getSecondaryText(item: AwsRegionEntity?) = item?.id ?: ""
                }
                region = comboBox(regions, renderer)
                region?.onChanged {
                    onChanged(this@AwsSettingsComponentForKafka)
                }
            }
        }

        row(KafkaMessagesBundle.message("settings.aws.auth.type")) {
            authTypeChooser = comboBox(authTypes, CustomListCellRenderer<AuthenticationType> { it.title }).onChanged {
                onChanged(this@AwsSettingsComponentForKafka)
            }
        }
        indent {
            profileRows = rowsRange {
                row(KafkaMessagesBundle.message("settings.aws.auth.type.profile")) {
                    profileComboBox = comboBox(profiles, CustomListCellRenderer<String> { it }).onChanged {
                        onChanged(this@AwsSettingsComponentForKafka)
                    }
                }
            }

            credentialRows = rowsRange {
                row(KafkaMessagesBundle.message("settings.aws.auth.type.access.key")) {
                    accessKey = textField().onChanged { onChanged(this@AwsSettingsComponentForKafka) }
                }
                row(KafkaMessagesBundle.message("settings.aws.auth.type.secret.key")) {
                    secretKey = passwordField().onChanged { onChanged(this@AwsSettingsComponentForKafka) }
                }
                row {
                    comment(KafkaMessagesBundle.message("kafka.credentials.comment.through.system.properties"))
                }
            }

            row {
                link(KafkaMessagesBundle.message("open.credentials")) {
                    try {
                        val defaultCredentialFile = AwsConnectionUtils.getDefaultCredentialFile()
                        val credFile = defaultCredentialFile.takeIf { it.exists() }
                            ?: AwsConnectionUtils.createCredentialFileIfDoesNotExists()
                            ?: return@link

                        if (credFile.exists()) {
                            RevealFileAction.openFile(credFile.toPath())
                            return@link
                        }
                    } catch (t: Throwable) {
                        RfsNotificationUtils.showExceptionMessage(null, t)
                    }
                }
            }.visibleIf(authTypeChooser.component.selectedValueMatches {
                it in arrayOf(AuthenticationType.DEFAULT, AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE)
            })


            authTypeChooser.onChanged {
                updateVisibility()
            }
        }
    }

    fun updateVisibility() {
        profileRows.visible(authTypeChooser.component.item == AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE)
        credentialRows.visible(authTypeChooser.component.item == AuthenticationType.KEY_PAIR)
    }

    fun loadInfo(info: StaticAwsSettingsInfo) {
        if (AuthenticationType.getById(info.authenticationType) in authTypes) {
            authTypeChooser.component.item = AuthenticationType.getById(info.authenticationType)
        } else {
            return
        }

        accessKey.text(info.accessKey ?: System.getProperty(AWS_ACCESS_KEY, "").trim())
        secretKey.text(info.secretKey ?: System.getProperty(AWS_SECRET_KEY, "").trim())

        info.region?.let {
            region?.component?.item = AwsRegionEntity(Region.of(it))
        }

        @Suppress("HardCodedStringLiteral")
        val profile = profiles.firstOrNull { it == info.profile } ?: return
        profileComboBox.component.item = profile
    }

    @Suppress("DEPRECATION")
    fun getInfo(): StaticAwsSettingsInfo = when (authTypeChooser.component.item) {
        AuthenticationType.DEFAULT -> StaticAwsSettingsInfo(authTypeChooser.component.item.id, region = getRegionId())
        AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE -> StaticAwsSettingsInfo(
            authenticationType = authTypeChooser.component.item.id,
            profile = profileComboBox.component.item,
            region = getRegionId()
        )

        AuthenticationType.KEY_PAIR -> StaticAwsSettingsInfo(
            authenticationType = authTypeChooser.component.item.id,
            accessKey = accessKey.component.text,
            secretKey = secretKey.component.text,
            region = getRegionId()
        )

        else -> error("Unrecognized auth")
    }

    private fun getRegionId() = region?.component?.item?.id

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        const val AWS_MECHANISM: String = "AWS_MSK_IAM"
        const val AWS_ACCESS_KEY: String = "aws.accessKeyId"
        const val AWS_SECRET_KEY: String = "aws.secretKey"
    }
}