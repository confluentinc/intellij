package com.jetbrains.bigdatatools.kafka.core.settings.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.bigdatatools.kafka.core.settings.StatisticsSettings
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionSettingProviderEP
import org.apache.http.client.utils.URLEncodedUtils
import java.net.URI
import java.nio.charset.StandardCharsets

// Currently we will only send the events about broken connection settings and wrong http responses
internal object ConnectionUsagesCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  enum class ErrorReason {
    CNF, // Class not found.
    UNKNOWN, // Not a class not found.
    MIXED // Class not found and also any other.
  }

  private val GROUP = EventLogGroup("bdt.connection", 8)

  private const val unknown = "UNKNOWN"
  private val connectionAllowedNames by lazy { ConnectionSettingProviderEP.getGroups().map { it.id } + unknown }

  private val groupId = EventFields.String("group_id", connectionAllowedNames)

  private val isEnabled = EventFields.Boolean("is_enabled")
  private val isPerProject = EventFields.Boolean("is_per_project")
  private val anonymous = EventFields.Boolean("anonymous")
  private val reason = EventFields.Enum<ErrorReason>("reason")

  private val errorLoadingConnectionEvent = GROUP.registerVarargEvent("bdt.connection.load.error",
                                                                      groupId, isEnabled, isPerProject, anonymous, reason)

  //private val oauthParamsAllowedNames by lazy { OAuthRequestUtil.oauthRequestParams + unknown }

  private val httpResponse = EventFields.Int("response")
  private val redirectCount = EventFields.Int("redirect_count")
  //private val oauthRequestParams = EventFields.StringList("oauth_request_params", oauthParamsAllowedNames)
  //private val isOauthRedirectToSelf = EventFields.Boolean("is_oauth_redirect_to_self")

  private val errorRestClientResponseEvent = GROUP.registerVarargEvent("rest.client.responded.with.error",
                                                                       groupId, httpResponse, redirectCount)

  private fun getAllowedConnectionType(groupId: String): String {
    if (connectionAllowedNames.contains(groupId)) return groupId
    else return unknown
  }

  //private fun getAllowedOauthRequestParam(oauthRequestParam: String): String {
  //  if (oauthParamsAllowedNames.contains(oauthRequestParam)) return oauthRequestParam
  //  else return unknown
  //}

  fun logRestClientErrorResponse(connectionGroupId: String,
                                 requestedUri: URI,
                                 response: Int,
                                 internalRedirects: List<URI>,
                                 oauthRequest: URI?) {
    val oauthRequestParams = oauthRequest?.let { URLEncodedUtils.parse(oauthRequest, StandardCharsets.UTF_8) } ?: emptyList()
    //val redirectToSelf = oauthRequest?.let { OAuthRequestUtil.oauthRedirectHost(oauthRequest) } == requestedUri.host
    errorRestClientResponseEvent.log(this.groupId.with(getAllowedConnectionType(connectionGroupId)),
                                     this.httpResponse.with(response),
                                     this.redirectCount.with(internalRedirects.size),
                                     //this.oauthRequestParams.with(oauthRequestParams.map { getAllowedOauthRequestParam(it.name) }),
                                     //this.isOauthRedirectToSelf.with(redirectToSelf))
    )
  }

  fun logLoadErrors(errors: Map<ConnectionData, MutableList<Throwable>>) {
    val alreadyReportedIds = StatisticsSettings.getInstance().reportedBrokenConnections
    val onlyNew = errors.filter { !alreadyReportedIds.contains(it.key.innerId) }

    errors.forEach {
      var hasCNF = false
      var hasOther = false
      it.value.forEach { throwable ->
        if (throwable is ClassNotFoundException)
          hasCNF = true
        else
          hasOther = true
      }

      val errorReason = if (hasCNF && hasOther) ErrorReason.MIXED
      else if (hasCNF) ErrorReason.CNF else ErrorReason.UNKNOWN

      errorLoadingConnectionEvent.log(groupId.with(getAllowedConnectionType(it.key.groupId)),
                                      isEnabled.with(it.key.isEnabled),
                                      isPerProject.with(it.key.isPerProject),
                                      anonymous.with(it.key.anonymous),
                                      reason.with(errorReason))
    }

    StatisticsSettings.getInstance().reportedBrokenConnections.addAll(onlyNew.map { it.key.innerId })
  }
}