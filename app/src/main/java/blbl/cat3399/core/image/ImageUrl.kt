package blbl.cat3399.core.image

import blbl.cat3399.core.net.BiliClient

object ImageUrl {
    private fun normalize(url: String): String {
        val u = url.trim()
        val fixed = when {
            u.startsWith("//") -> "https:$u"
            u.startsWith("http://") -> "https://" + u.removePrefix("http://")
            else -> u
        }
        return fixed
    }

    fun cover(url: String?): String? {
        val u = url ?: return null
        if (u.isBlank()) return null
        val nu = normalize(u)
        if (nu.contains("@")) return nu
        val suffix = when (BiliClient.prefs.imageQuality) {
            "small" -> "@320w_180h_1c.webp"
            "large" -> "@640w_360h_1c.webp"
            else -> "@480w_270h_1c.webp"
        }
        return nu + suffix
    }

    fun poster(url: String?): String? {
        val u = url ?: return null
        if (u.isBlank()) return null
        val nu = normalize(u)
        if (nu.contains("@")) return nu
        val suffix = when (BiliClient.prefs.imageQuality) {
            "small" -> "@240w_340h_1c.webp"
            "large" -> "@480w_680h_1c.webp"
            else -> "@360w_510h_1c.webp"
        }
        return nu + suffix
    }

    fun avatar(url: String?): String? {
        val u = url ?: return null
        if (u.isBlank()) return null
        val nu = normalize(u)
        if (nu.contains("@")) return nu
        return nu + "@80w_80h_1c.webp"
    }

    fun commentThumbnail(url: String?): String? {
        val u = url ?: return null
        if (u.isBlank()) return null
        val nu = normalize(u)
        if (nu.contains("@")) return nu
        val suffix = when (BiliClient.prefs.imageQuality) {
            "small" -> "@320w_240h_1c.webp"
            "large" -> "@640w_480h_1c.webp"
            else -> "@480w_360h_1c.webp"
        }
        return nu + suffix
    }
}
