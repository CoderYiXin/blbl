package blbl.cat3399.core.prefs

import blbl.cat3399.BuildConfig
import blbl.cat3399.core.net.CookieStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppConfigBackup {
    const val JSON_MIME = "application/json"

    private const val SCHEMA_VERSION = 1
    private const val KEY_SCHEMA = "schema"
    private const val KEY_KIND = "kind"
    private const val KEY_EXPORTED_AT_MS = "exported_at_ms"
    private const val KEY_APP = "app"
    private const val KEY_CONFIG = "config"
    private const val KEY_CREDENTIALS = "credentials"

    private const val KIND = "blbl_config_backup"

    enum class ExportMode {
        CONFIG_ONLY,
        CONFIG_WITH_CREDENTIALS,
    }

    class ParsedBackup internal constructor(
        private val root: JSONObject,
    ) {
        val hasCredentials: Boolean
            get() = hasCredentialsJson(root.optJSONObject(KEY_CREDENTIALS))

        internal val configJson: JSONObject
            get() = root.optJSONObject(KEY_CONFIG) ?: JSONObject()

        internal val credentialsJson: JSONObject?
            get() = root.optJSONObject(KEY_CREDENTIALS)
    }

    fun buildFileName(
        mode: ExportMode,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val ts = runCatching { sdf.format(Date(nowMs)) }.getOrNull() ?: nowMs.toString()
        val suffix =
            when (mode) {
                ExportMode.CONFIG_ONLY -> "config"
                ExportMode.CONFIG_WITH_CREDENTIALS -> "config_with_credentials"
            }
        return "blbl_${suffix}_$ts.json"
    }

    fun buildJson(
        prefs: AppPrefs,
        cookies: CookieStore,
        mode: ExportMode,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        val root =
            JSONObject()
                .put(KEY_SCHEMA, SCHEMA_VERSION)
                .put(KEY_KIND, KIND)
                .put(KEY_EXPORTED_AT_MS, nowMs)
                .put(
                    KEY_APP,
                    JSONObject()
                        .put("package", BuildConfig.APPLICATION_ID)
                        .put("version_name", BuildConfig.VERSION_NAME)
                        .put("version_code", BuildConfig.VERSION_CODE),
                )
                .put(KEY_CONFIG, buildConfigJson(prefs))

        val credentials =
            when (mode) {
                ExportMode.CONFIG_ONLY -> null
                ExportMode.CONFIG_WITH_CREDENTIALS -> buildCredentialsJsonOrNull(prefs, cookies)
            }
        if (credentials != null) root.put(KEY_CREDENTIALS, credentials)
        return root.toString(2)
    }

    fun parse(raw: String): ParsedBackup {
        val text = raw.trim()
        if (text.isBlank()) error("配置文件为空")

        val root = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("配置文件不是有效的 JSON") }
        if (root.optInt(KEY_SCHEMA, -1) != SCHEMA_VERSION) {
            error("不支持的配置文件版本")
        }
        if (root.optString(KEY_KIND, "").trim() != KIND) {
            error("不是 blbl 配置文件")
        }
        if (root.optJSONObject(KEY_CONFIG) == null) {
            error("配置文件缺少 config 字段")
        }
        return ParsedBackup(root)
    }

    fun apply(
        parsed: ParsedBackup,
        prefs: AppPrefs,
        cookies: CookieStore,
    ) {
        applyConfigJson(parsed.configJson, prefs)
        if (parsed.hasCredentials) {
            applyCredentialsJson(parsed.credentialsJson ?: JSONObject(), prefs, cookies)
        }
    }

    private fun buildConfigJson(prefs: AppPrefs): JSONObject {
        val osdButtons = JSONArray()
        for (button in prefs.playerOsdButtons) osdButtons.put(button)

        val customShortcuts = JSONObject(PlayerCustomShortcutsStore.serialize(prefs.playerCustomShortcuts))

        return JSONObject()
            .put(
                "ui",
                JSONObject()
                    .put("theme_preset", prefs.themePreset)
                    .put("ui_scale_factor", prefs.uiScaleFactor)
                    .put("sidebar_size", prefs.sidebarSize)
                    .put("startup_page", prefs.startupPage)
                    .put("following_list_order", prefs.followingListOrder)
                    .put("dynamic_following_recent_update_dot_enabled", prefs.dynamicFollowingRecentUpdateDotEnabled)
                    .put("image_quality", prefs.imageQuality)
                    .put("fullscreen_enabled", prefs.fullscreenEnabled)
                    .put("tab_switch_follows_focus", prefs.tabSwitchFollowsFocus)
                    .put("main_back_focus_scheme", prefs.mainBackFocusScheme)
                    .put("grid_span", prefs.gridSpanCount)
                    .put("dynamic_grid_span", prefs.dynamicGridSpanCount)
                    .put("pgc_grid_span", prefs.pgcGridSpanCount)
                    .put("pgc_episode_order_reversed", prefs.pgcEpisodeOrderReversed),
            )
            .put(
                "network",
                JSONObject()
                    .put("ipv4_only_enabled", prefs.ipv4OnlyEnabled)
                    .put("user_agent", prefs.userAgent),
            )
            .put(
                "player",
                JSONObject()
                    .put("preferred_qn", prefs.playerPreferredQn)
                    .put("preferred_qn_portrait", prefs.playerPreferredQnPortrait)
                    .put("preferred_codec", prefs.playerPreferredCodec)
                    .put("render_view_type", prefs.playerRenderViewType)
                    .put("engine_kind", prefs.playerEngineKind)
                    .put("preferred_audio_id", prefs.playerPreferredAudioId)
                    .put("cdn_preference", prefs.playerCdnPreference)
                    .put("live_high_bitrate_enabled", prefs.liveHighBitrateEnabled)
                    .put("subtitle_lang", prefs.subtitlePreferredLang)
                    .put("subtitle_enabled_default", prefs.subtitleEnabledDefault)
                    .put("subtitle_text_size_sp", prefs.subtitleTextSizeSp)
                    .put("subtitle_bottom_padding_fraction", prefs.subtitleBottomPaddingFraction)
                    .put("subtitle_background_opacity", prefs.subtitleBackgroundOpacity)
                    .put("speed", prefs.playerSpeed)
                    .put("hold_seek_speed", prefs.playerHoldSeekSpeed)
                    .put("hold_seek_mode", prefs.playerHoldSeekMode)
                    .put("auto_resume_enabled", prefs.playerAutoResumeEnabled)
                    .put("auto_skip_segments_enabled", prefs.playerAutoSkipSegmentsEnabled)
                    .put("open_detail_before_play", prefs.playerOpenDetailBeforePlay)
                    .put("debug_enabled", prefs.playerDebugEnabled)
                    .put("double_back_to_exit", prefs.playerDoubleBackToExit)
                    .put("down_key_osd_focus_target", prefs.playerDownKeyOsdFocusTarget)
                    .put("persistent_bottom_progress_enabled", prefs.playerPersistentBottomProgressEnabled)
                    .put("playback_mode", prefs.playerPlaybackMode)
                    .put("audio_balance_level", prefs.playerAudioBalanceLevel)
                    .put("video_shot_preview_size", prefs.playerVideoShotPreviewSize)
                    .put("osd_buttons", osdButtons)
                    .put("custom_shortcuts", customShortcuts),
            )
            .put(
                "danmaku",
                JSONObject()
                    .put("enabled", prefs.danmakuEnabled)
                    .put("allow_top", prefs.danmakuAllowTop)
                    .put("allow_bottom", prefs.danmakuAllowBottom)
                    .put("allow_scroll", prefs.danmakuAllowScroll)
                    .put("allow_color", prefs.danmakuAllowColor)
                    .put("allow_special", prefs.danmakuAllowSpecial)
                    .put("follow_bili_shield", prefs.danmakuFollowBiliShield)
                    .put("ai_shield_enabled", prefs.danmakuAiShieldEnabled)
                    .put("ai_shield_level", prefs.danmakuAiShieldLevel)
                    .put("opacity", prefs.danmakuOpacity)
                    .put("text_size_sp", prefs.danmakuTextSizeSp)
                    .put("lane_density", prefs.danmakuLaneDensity)
                    .put("stroke_width_px", prefs.danmakuStrokeWidthPx)
                    .put("font_weight", prefs.danmakuFontWeight)
                    .put("speed", prefs.danmakuSpeed)
                    .put("area", prefs.danmakuArea),
            )
    }

    private fun buildCredentialsJsonOrNull(
        prefs: AppPrefs,
        cookies: CookieStore,
    ): JSONObject? {
        val hasLoginCredentials = cookies.hasSessData() || !prefs.webRefreshToken.isNullOrBlank()
        if (!hasLoginCredentials) return null

        return JSONObject()
            .put("cookies", cookies.exportSnapshotJson(includeExpired = false))
            .put("web_refresh_token", prefs.webRefreshToken)
            .put("web_cookie_refresh_checked_epoch_day", prefs.webCookieRefreshCheckedEpochDay)
            .put("bili_ticket_checked_epoch_day", prefs.biliTicketCheckedEpochDay)
            .put("gaia_vgate_v_voucher", prefs.gaiaVgateVVoucher)
            .put("gaia_vgate_v_voucher_saved_at_ms", prefs.gaiaVgateVVoucherSavedAtMs)
    }

    private fun applyConfigJson(
        config: JSONObject,
        prefs: AppPrefs,
    ) {
        config.optJSONObject("ui")?.let { ui ->
            ui.optStringValue("theme_preset")?.let { prefs.themePreset = it }
            ui.optFloatValue("ui_scale_factor")?.let { prefs.uiScaleFactor = it }
            ui.optStringValue("sidebar_size")?.let { prefs.sidebarSize = it }
            ui.optStringValue("startup_page")?.let { prefs.startupPage = it }
            ui.optStringValue("following_list_order")?.let { prefs.followingListOrder = it }
            ui.optBooleanValue("dynamic_following_recent_update_dot_enabled")?.let { prefs.dynamicFollowingRecentUpdateDotEnabled = it }
            ui.optStringValue("image_quality")?.let { prefs.imageQuality = it }
            ui.optBooleanValue("fullscreen_enabled")?.let { prefs.fullscreenEnabled = it }
            ui.optBooleanValue("tab_switch_follows_focus")?.let { prefs.tabSwitchFollowsFocus = it }
            ui.optStringValue("main_back_focus_scheme")?.let { prefs.mainBackFocusScheme = it }
            ui.optIntValue("grid_span")?.let { prefs.gridSpanCount = it }
            ui.optIntValue("dynamic_grid_span")?.let { prefs.dynamicGridSpanCount = it }
            ui.optIntValue("pgc_grid_span")?.let { prefs.pgcGridSpanCount = it }
            ui.optBooleanValue("pgc_episode_order_reversed")?.let { prefs.pgcEpisodeOrderReversed = it }
        }

        config.optJSONObject("network")?.let { network ->
            network.optBooleanValue("ipv4_only_enabled")?.let { prefs.ipv4OnlyEnabled = it }
            network.optStringValue("user_agent")?.let { prefs.userAgent = it }
        }

        config.optJSONObject("player")?.let { player ->
            player.optIntValue("preferred_qn")?.let { prefs.playerPreferredQn = it }
            player.optIntValue("preferred_qn_portrait")?.let { prefs.playerPreferredQnPortrait = it }
            player.optStringValue("preferred_codec")?.let { prefs.playerPreferredCodec = it }
            player.optStringValue("render_view_type")?.let { prefs.playerRenderViewType = it }
            player.optStringValue("engine_kind")?.let { prefs.playerEngineKind = it }
            player.optIntValue("preferred_audio_id")?.let { prefs.playerPreferredAudioId = it }
            player.optStringValue("cdn_preference")?.let { prefs.playerCdnPreference = it }
            player.optBooleanValue("live_high_bitrate_enabled")?.let { prefs.liveHighBitrateEnabled = it }
            player.optStringValue("subtitle_lang")?.let { prefs.subtitlePreferredLang = it }
            player.optBooleanValue("subtitle_enabled_default")?.let { prefs.subtitleEnabledDefault = it }
            player.optFloatValue("subtitle_text_size_sp")?.let { prefs.subtitleTextSizeSp = it }
            player.optFloatValue("subtitle_bottom_padding_fraction")?.let { prefs.subtitleBottomPaddingFraction = it }
            player.optFloatValue("subtitle_background_opacity")?.let { prefs.subtitleBackgroundOpacity = it }
            player.optFloatValue("speed")?.let { prefs.playerSpeed = it }
            player.optFloatValue("hold_seek_speed")?.let { prefs.playerHoldSeekSpeed = it }
            player.optStringValue("hold_seek_mode")?.let { prefs.playerHoldSeekMode = it }
            player.optBooleanValue("auto_resume_enabled")?.let { prefs.playerAutoResumeEnabled = it }
            player.optBooleanValue("auto_skip_segments_enabled")?.let { prefs.playerAutoSkipSegmentsEnabled = it }
            player.optBooleanValue("open_detail_before_play")?.let { prefs.playerOpenDetailBeforePlay = it }
            player.optBooleanValue("debug_enabled")?.let { prefs.playerDebugEnabled = it }
            player.optBooleanValue("double_back_to_exit")?.let { prefs.playerDoubleBackToExit = it }
            player.optStringValue("down_key_osd_focus_target")?.let { prefs.playerDownKeyOsdFocusTarget = it }
            player.optBooleanValue("persistent_bottom_progress_enabled")?.let { prefs.playerPersistentBottomProgressEnabled = it }
            player.optStringValue("playback_mode")?.let { prefs.playerPlaybackMode = it }
            player.optStringValue("audio_balance_level")?.let { prefs.playerAudioBalanceLevel = it }
            player.optStringValue("video_shot_preview_size")?.let { prefs.playerVideoShotPreviewSize = it }
            player.optStringList("osd_buttons")?.let { prefs.playerOsdButtons = it }
            player.optJSONObject("custom_shortcuts")?.let { prefs.playerCustomShortcuts = PlayerCustomShortcutsStore.parse(it.toString()) }
        }

        config.optJSONObject("danmaku")?.let { danmaku ->
            danmaku.optBooleanValue("enabled")?.let { prefs.danmakuEnabled = it }
            danmaku.optBooleanValue("allow_top")?.let { prefs.danmakuAllowTop = it }
            danmaku.optBooleanValue("allow_bottom")?.let { prefs.danmakuAllowBottom = it }
            danmaku.optBooleanValue("allow_scroll")?.let { prefs.danmakuAllowScroll = it }
            danmaku.optBooleanValue("allow_color")?.let { prefs.danmakuAllowColor = it }
            danmaku.optBooleanValue("allow_special")?.let { prefs.danmakuAllowSpecial = it }
            danmaku.optBooleanValue("follow_bili_shield")?.let { prefs.danmakuFollowBiliShield = it }
            danmaku.optBooleanValue("ai_shield_enabled")?.let { prefs.danmakuAiShieldEnabled = it }
            danmaku.optIntValue("ai_shield_level")?.let { prefs.danmakuAiShieldLevel = it }
            danmaku.optFloatValue("opacity")?.let { prefs.danmakuOpacity = it }
            danmaku.optFloatValue("text_size_sp")?.let { prefs.danmakuTextSizeSp = it }
            danmaku.optStringValue("lane_density")?.let { prefs.danmakuLaneDensity = it }
            danmaku.optIntValue("stroke_width_px")?.let { prefs.danmakuStrokeWidthPx = it }
            danmaku.optStringValue("font_weight")?.let { prefs.danmakuFontWeight = it }
            danmaku.optIntValue("speed")?.let { prefs.danmakuSpeed = it }
            danmaku.optFloatValue("area")?.let { prefs.danmakuArea = it }
        }
    }

    private fun applyCredentialsJson(
        credentials: JSONObject,
        prefs: AppPrefs,
        cookies: CookieStore,
    ) {
        val cookieJson = credentials.optJSONObject("cookies") ?: JSONObject()
        cookies.replaceAllFromJson(cookieJson)
        prefs.webRefreshToken = credentials.optStringValue("web_refresh_token")
        prefs.webCookieRefreshCheckedEpochDay = credentials.optLongValue("web_cookie_refresh_checked_epoch_day") ?: -1L
        prefs.biliTicketCheckedEpochDay = credentials.optLongValue("bili_ticket_checked_epoch_day") ?: -1L
        prefs.gaiaVgateVVoucher = credentials.optStringValue("gaia_vgate_v_voucher")
        prefs.gaiaVgateVVoucherSavedAtMs = credentials.optLongValue("gaia_vgate_v_voucher_saved_at_ms") ?: -1L
    }

    private fun hasCredentialsJson(credentials: JSONObject?): Boolean {
        if (credentials == null) return false
        val cookies = credentials.optJSONObject("cookies")
        if (cookies != null) {
            val it = cookies.keys()
            while (it.hasNext()) {
                val domain = it.next()
                val arr = cookies.optJSONArray(domain) ?: continue
                for (i in 0 until arr.length()) {
                    val cookie = arr.optJSONObject(i) ?: continue
                    if (cookie.optString("name", "").trim() == "SESSDATA") return true
                }
            }
        }
        return !credentials.optStringValue("web_refresh_token").isNullOrBlank()
    }

    private fun JSONObject.optStringValue(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name, "").trim().takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optBooleanValue(name: String): Boolean? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String ->
                when (value.trim().lowercase(Locale.US)) {
                    "1", "true" -> true
                    "0", "false" -> false
                    else -> null
                }

            else -> null
        }
    }

    private fun JSONObject.optIntValue(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optLongValue(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
    }

    private fun JSONObject.optFloatValue(name: String): Float? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value.toFloat()
            is String -> value.trim().toFloatOrNull()
            else -> null
        }?.takeIf { it.isFinite() }
    }

    private fun JSONObject.optStringList(name: String): List<String>? {
        if (!has(name) || isNull(name)) return null
        val arr = optJSONArray(name) ?: return null
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val value = arr.optString(i, "").trim()
            if (value.isNotBlank()) out.add(value)
        }
        return out
    }
}
