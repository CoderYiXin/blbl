package blbl.cat3399.feature.custom

import androidx.fragment.app.Fragment
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.CustomPageConfig
import blbl.cat3399.core.prefs.CustomPageTabConfig
import blbl.cat3399.feature.category.CategoryZones
import blbl.cat3399.feature.live.LiveGridFragment
import blbl.cat3399.feature.my.MyHistoryFragment
import blbl.cat3399.feature.video.VideoGridFragment

data class CustomPageTabOption(
    val stableKey: String,
    val label: String,
    val config: CustomPageTabConfig,
)

data class CustomPageResolvedTab(
    val stableKey: String,
    val title: String,
    val createFragment: () -> Fragment,
)

object CustomPageTabRegistry {
    const val TYPE_HOME_RECOMMEND = "home_recommend"
    const val TYPE_HOME_POPULAR = "home_popular"
    const val TYPE_CATEGORY_ALL = "category_all"
    const val TYPE_CATEGORY_ZONE = "category_zone"
    const val TYPE_DYNAMIC_VIDEO = "dynamic_video"
    const val TYPE_MY_HISTORY = "my_history"
    const val TYPE_LIVE_RECOMMEND = "live_recommend"
    const val TYPE_LIVE_FOLLOWING = "live_following"

    private data class Descriptor(
        val stableKey: String,
        val managerLabel: String,
        val tabTitle: String,
        val requiresLogin: Boolean = false,
        val createFragment: () -> Fragment,
    )

    fun isEnabled(
        config: CustomPageConfig,
        isLoggedIn: Boolean = BiliClient.cookies.hasSessData(),
    ): Boolean {
        return config.enabled && resolvedTabs(config = config, isLoggedIn = isLoggedIn).isNotEmpty()
    }

    fun resolvedTabs(
        config: CustomPageConfig,
        isLoggedIn: Boolean = BiliClient.cookies.hasSessData(),
    ): List<CustomPageResolvedTab> {
        if (!config.enabled) return emptyList()

        val out = ArrayList<CustomPageResolvedTab>(config.tabs.size)
        val seen = HashSet<String>(config.tabs.size * 2)
        config.tabs.forEach { tab ->
            val descriptor = descriptorFor(tab) ?: return@forEach
            if (descriptor.requiresLogin && !isLoggedIn) return@forEach
            if (!seen.add(descriptor.stableKey)) return@forEach
            out.add(
                CustomPageResolvedTab(
                    stableKey = descriptor.stableKey,
                    title = descriptor.tabTitle,
                    createFragment = descriptor.createFragment,
                ),
            )
        }
        return out
    }

    fun availableAddOptions(
        config: CustomPageConfig,
        isLoggedIn: Boolean = BiliClient.cookies.hasSessData(),
    ): List<CustomPageTabOption> {
        val existingStableKeys = config.tabs.mapTo(HashSet(config.tabs.size * 2)) { stableKeyFor(it) }
        return supportedConfigs()
            .filterNot { stableKeyFor(it) in existingStableKeys }
            .map { supportedConfig ->
                val descriptor = descriptorFor(supportedConfig)
                val label =
                    when {
                        descriptor == null -> invalidLabelFor(supportedConfig)
                        descriptor.requiresLogin && !isLoggedIn -> "${descriptor.managerLabel}（需登录后显示）"
                        else -> descriptor.managerLabel
                    }
                CustomPageTabOption(
                    stableKey = stableKeyFor(supportedConfig),
                    label = label,
                    config = supportedConfig,
                )
            }
    }

    fun managerLabel(
        config: CustomPageTabConfig,
        isLoggedIn: Boolean = BiliClient.cookies.hasSessData(),
    ): String {
        val descriptor = descriptorFor(config)
        return when {
            descriptor == null -> invalidLabelFor(config)
            descriptor.requiresLogin && !isLoggedIn -> "${descriptor.managerLabel}（需登录后显示）"
            else -> descriptor.managerLabel
        }
    }

    fun settingsLabelForConfig(
        config: CustomPageTabConfig,
        isLoggedIn: Boolean = BiliClient.cookies.hasSessData(),
    ): String = managerLabel(config = config, isLoggedIn = isLoggedIn)

    fun stableKeyFor(config: CustomPageTabConfig): String {
        val sourceType = config.sourceType.trim()
        val sourceKey = config.sourceKey?.trim().orEmpty()
        return if (sourceKey.isBlank()) sourceType else "$sourceType:$sourceKey"
    }

    private fun supportedConfigs(): List<CustomPageTabConfig> {
        return buildList {
            add(CustomPageTabConfig(sourceType = TYPE_HOME_RECOMMEND))
            add(CustomPageTabConfig(sourceType = TYPE_HOME_POPULAR))
            add(CustomPageTabConfig(sourceType = TYPE_CATEGORY_ALL))
            CategoryZones.defaultZones
                .mapNotNull { zone -> zone.tid?.let { tid -> CustomPageTabConfig(sourceType = TYPE_CATEGORY_ZONE, sourceKey = tid.toString()) } }
                .forEach(::add)
            add(CustomPageTabConfig(sourceType = TYPE_DYNAMIC_VIDEO))
            add(CustomPageTabConfig(sourceType = TYPE_MY_HISTORY))
            add(CustomPageTabConfig(sourceType = TYPE_LIVE_RECOMMEND))
            add(CustomPageTabConfig(sourceType = TYPE_LIVE_FOLLOWING))
        }
    }

    private fun descriptorFor(config: CustomPageTabConfig): Descriptor? {
        return when (config.sourceType) {
            TYPE_HOME_RECOMMEND ->
                Descriptor(
                    stableKey = TYPE_HOME_RECOMMEND,
                    managerLabel = "首页-推荐",
                    tabTitle = "推荐",
                    createFragment = { VideoGridFragment.newRecommend() },
                )

            TYPE_HOME_POPULAR ->
                Descriptor(
                    stableKey = TYPE_HOME_POPULAR,
                    managerLabel = "首页-热门",
                    tabTitle = "热门",
                    createFragment = { VideoGridFragment.newPopular() },
                )

            TYPE_CATEGORY_ALL ->
                Descriptor(
                    stableKey = TYPE_CATEGORY_ALL,
                    managerLabel = "分类-全站",
                    tabTitle = "全站",
                    createFragment = { VideoGridFragment.newPopular() },
                )

            TYPE_CATEGORY_ZONE -> {
                val tid = config.sourceKey?.toIntOrNull()?.takeIf { it > 0 } ?: return null
                val zone = CategoryZones.findByTid(tid) ?: return null
                Descriptor(
                    stableKey = stableKeyFor(config),
                    managerLabel = "分类-${zone.title}",
                    tabTitle = zone.title,
                    createFragment = { VideoGridFragment.newRegion(tid) },
                )
            }

            TYPE_DYNAMIC_VIDEO ->
                Descriptor(
                    stableKey = TYPE_DYNAMIC_VIDEO,
                    managerLabel = "动态-视频",
                    tabTitle = "动态",
                    createFragment = { CustomDynamicVideoFragment.newInstance() },
                )

            TYPE_MY_HISTORY ->
                Descriptor(
                    stableKey = TYPE_MY_HISTORY,
                    managerLabel = "我的-历史记录",
                    tabTitle = "历史",
                    createFragment = { MyHistoryFragment() },
                )

            TYPE_LIVE_RECOMMEND ->
                Descriptor(
                    stableKey = TYPE_LIVE_RECOMMEND,
                    managerLabel = "直播-推荐",
                    tabTitle = "推荐",
                    createFragment = { LiveGridFragment.newRecommend() },
                )

            TYPE_LIVE_FOLLOWING ->
                Descriptor(
                    stableKey = TYPE_LIVE_FOLLOWING,
                    managerLabel = "直播-关注",
                    tabTitle = "关注",
                    requiresLogin = true,
                    createFragment = { LiveGridFragment.newFollowing() },
                )

            else -> null
        }
    }

    private fun invalidLabelFor(config: CustomPageTabConfig): String {
        val sourceType = config.sourceType.ifBlank { "unknown" }
        val sourceKey = config.sourceKey?.takeIf { it.isNotBlank() }
        return if (sourceKey == null) {
            "无效来源($sourceType)"
        } else {
            "无效来源($sourceType:$sourceKey)"
        }
    }
}
