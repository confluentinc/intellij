package io.confluent.intellijplugin.core.settings.kerberos

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(name = "ConfluentIntellijKafkaKerberosSettings", storages = [Storage("confluent_kafka_kerberos_settings.xml")])
class KerberosSettings : PersistentStateComponent<KerberosSettings> {
    var enabled = false

    var krb5Config = ""
    var loginConfig = ""

    var krb5Debug = false
    var jgssDebug = false

    override fun getState() = this

    override fun loadState(state: KerberosSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun instance(): KerberosSettings = service()
    }

}