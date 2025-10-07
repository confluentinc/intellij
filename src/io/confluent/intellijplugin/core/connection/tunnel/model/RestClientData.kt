package io.confluent.intellijplugin.core.connection.tunnel.model

import io.confluent.intellijplugin.core.settings.DoNotSerialize

interface RestClientData {
    var operationTimeout: String?

    @DoNotSerialize
    var headers: Map<String, String>?
}
