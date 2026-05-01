package blbl.cat3399.core.update

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import blbl.cat3399.BuildConfig
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.popup.AppPopup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object ApkUpdateFlow {
    private var activeJob: Job? = null

    fun startDownloadAndInstall(
        activity: ComponentActivity,
        latestVersionHint: String? = null,
        onResolved: ((latestVersion: String, isNewer: Boolean) -> Unit)? = null,
    ): Job? {
        if (activeJob?.isActive == true) {
            AppToast.show(activity, "正在下载更新…")
            return null
        }

        val now = System.currentTimeMillis()
        val cooldownLeftMs = ApkUpdater.cooldownLeftMs(now)
        if (cooldownLeftMs > 0) {
            AppToast.show(activity, "操作太频繁，请稍后再试（${(cooldownLeftMs / 1000).coerceAtLeast(1)}s）")
            return null
        }

        val popup =
            AppPopup.progress(
                context = activity,
                title = "下载更新",
                status = "检查更新…",
                negativeText = "取消",
                cancelable = false,
                onNegative = { activeJob?.cancel() },
            )

        val job =
            activity.lifecycleScope.launch {
                try {
                    val currentVersion = BuildConfig.VERSION_NAME
                    val latestVersion = latestVersionHint ?: ApkUpdater.fetchLatestUpdate().versionName
                    val isNewer = ApkUpdater.isRemoteNewer(latestVersion, currentVersion)
                    onResolved?.invoke(latestVersion, isNewer)
                    if (!isNewer) {
                        popup?.dismiss()
                        AppToast.show(activity, "已是最新版（当前：$currentVersion）")
                        return@launch
                    }

                    popup?.updateStatus("准备下载…（最新：$latestVersion）")
                    popup?.updateProgress(null)

                    ApkUpdater.markStarted(now)
                    val apkFile =
                        ApkUpdater.downloadApkToCache(
                            context = activity,
                            url = ApkUpdater.TEST_APK_URL,
                        ) { dlState ->
                            when (dlState) {
                                ApkUpdater.Progress.Connecting -> {
                                    popup?.updateProgress(null)
                                    popup?.updateStatus("连接中…")
                                }

                                is ApkUpdater.Progress.Downloading -> {
                                    val pct = dlState.percent
                                    if (pct != null) {
                                        popup?.updateProgress(pct.coerceIn(0, 100))
                                        popup?.updateStatus("下载中… ${pct.coerceIn(0, 100)}% ${dlState.hint}")
                                    } else {
                                        popup?.updateProgress(null)
                                        popup?.updateStatus("下载中… ${dlState.hint}")
                                    }
                                }
                            }
                        }

                    popup?.updateStatus("准备安装…")
                    popup?.updateProgress(null)
                    popup?.dismiss()
                    ApkUpdater.installApk(activity, apkFile)
                } catch (_: CancellationException) {
                    popup?.dismiss()
                    AppToast.show(activity, "已取消更新")
                } catch (t: Throwable) {
                    AppLog.w("Update", "update failed: ${t.message}", t)
                    popup?.dismiss()
                    AppToast.showLong(activity, "更新失败：${t.message ?: "未知错误"}")
                } finally {
                    if (activeJob === this.coroutineContext[Job]) {
                        activeJob = null
                    }
                }
            }
        activeJob = job
        return job
    }
}
