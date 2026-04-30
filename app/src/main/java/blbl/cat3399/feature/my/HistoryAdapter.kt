package blbl.cat3399.feature.my

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.model.HistoryEntry
import blbl.cat3399.core.model.LiveRoomCard
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.feature.live.LiveRoomAdapter
import blbl.cat3399.feature.video.VideoCardActionDelegate
import blbl.cat3399.feature.video.VideoCardAdapter

internal class HistoryAdapter(
    private val onVideoClick: (VideoCard, Int) -> Unit,
    private val onLiveClick: (LiveRoomCard) -> Unit,
    actionDelegate: VideoCardActionDelegate?,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = ArrayList<HistoryEntry>()
    private val videos = ArrayList<VideoCard>()
    private val videoAdapter =
        VideoCardAdapter(
            onClick = { card, _ -> videoIndex(card)?.let { onVideoClick(card, it) } },
            actionDelegate = actionDelegate,
        )
    private val liveAdapter = LiveRoomAdapter { _, room -> onLiveClick(room) }

    init {
        setHasStableIds(true)
        videoAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeChanged(
                    positionStart: Int,
                    itemCount: Int,
                ) = notifyVideoRangeChanged(positionStart, itemCount)
            },
        )
    }

    fun submit(list: List<HistoryEntry>) {
        items.clear()
        items.addAll(list)
        syncChildren()
        notifyDataSetChanged()
    }

    fun append(list: List<HistoryEntry>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        syncChildren()
        notifyItemRangeInserted(start, list.size)
    }

    fun videoSnapshot(): List<VideoCard> = videos.toList()

    fun videoPosition(card: VideoCard): Int? = videoIndex(card)

    fun removeByStableKey(stableKey: String): Int {
        val index = items.indexOfFirst { it.stableKey() == stableKey }
        if (index < 0) return -1
        items.removeAt(index)
        syncChildren()
        notifyItemRemoved(index)
        if (index < items.size) {
            notifyItemRangeChanged(index, items.size - index)
        }
        return index
    }

    override fun getItemId(position: Int): Long = items[position].stableKey().hashCode().toLong()

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is HistoryEntry.Video -> VIEW_TYPE_VIDEO
            is HistoryEntry.Live -> VIEW_TYPE_LIVE
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_VIDEO -> videoAdapter.onCreateViewHolder(parent, 0)
            VIEW_TYPE_LIVE -> liveAdapter.onCreateViewHolder(parent, 0)
            else -> error("unknown_history_view_type_$viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (items[position]) {
            is HistoryEntry.Video -> videoAdapter.onBindViewHolder(holder as VideoCardAdapter.Vh, videos.indexOf((items[position] as HistoryEntry.Video).card))
            is HistoryEntry.Live -> liveAdapter.onBindViewHolder(holder as LiveRoomAdapter.Vh, liveIndex(position))
        }
    }

    override fun getItemCount(): Int = items.size

    private fun syncChildren() {
        videos.clear()
        videos += items.mapNotNull { (it as? HistoryEntry.Video)?.card }
        videoAdapter.submit(videos)
        liveAdapter.submit(items.mapNotNull { (it as? HistoryEntry.Live)?.card })
    }

    private fun entryPositionOfVideoIndex(videoIndex: Int): Int {
        var index = -1
        for (i in items.indices) {
            if (items[i] is HistoryEntry.Video && ++index == videoIndex) return i
        }
        return -1
    }

    private fun videoIndex(card: VideoCard): Int? =
        videos.indexOfFirst { it.stableKey() == card.stableKey() }.takeIf { it >= 0 }

    private fun notifyVideoRangeChanged(
        positionStart: Int,
        itemCount: Int,
    ) {
        repeat(itemCount) { offset ->
            entryPositionOfVideoIndex(positionStart + offset).takeIf { it >= 0 }?.let(::notifyItemChanged)
        }
    }

    private fun liveIndex(position: Int): Int = items.take(position + 1).count { it is HistoryEntry.Live } - 1

    private companion object {
        const val VIEW_TYPE_VIDEO = 1
        const val VIEW_TYPE_LIVE = 2
    }
}
