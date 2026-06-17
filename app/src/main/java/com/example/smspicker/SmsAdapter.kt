package com.example.smspicker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class SmsListItem {
    data class StationHeader(val station: String, val count: Int, val infos: List<SmsInfo>) : SmsListItem()
    data class SmsItem(val info: SmsInfo) : SmsListItem()
    object DonateItem : SmsListItem()
}

class SmsAdapter(
    private val onAllOut: (String, List<SmsInfo>) -> Unit,
    private val onQrClick: (Bitmap) -> Unit,
    private val onQrLongClick: (Bitmap) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SmsListItem>()
    private var donateQrBitmap: Bitmap? = null

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_DONATE = 2
        private const val DONATE_URL_ALIPAY = "https://qr.alipay.com/fkx110236f5kju6qtcnpy46"
        private const val DONATE_URL_WECHAT = "wxp://f2f0ysMx_7UEoCrhUt-P4KJqmeuU4V4CY4IQ3Qmo6jxyYHQ"
        private const val QR_GRAY = 0xFF727272.toInt()
        private const val QR_SIZE = 200
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvStation: TextView = itemView.findViewById(R.id.tvStation)
        val btnAllOut: MaterialButton = itemView.findViewById(R.id.btnAllOut)
    }

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCode: TextView = itemView.findViewById(R.id.tvCode)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvBody: TextView = itemView.findViewById(R.id.tvBody)
    }

    class DonateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivQrCode: ImageView = itemView.findViewById(R.id.ivQrCode)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SmsListItem.StationHeader -> VIEW_TYPE_HEADER
            is SmsListItem.SmsItem -> VIEW_TYPE_ITEM
            is SmsListItem.DonateItem -> VIEW_TYPE_DONATE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_station_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_DONATE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_donate, parent, false)
                DonateViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_sms, parent, false)
                ItemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SmsListItem.StationHeader -> {
                val h = holder as HeaderViewHolder
                h.tvStation.text = item.station.ifEmpty { "未知驿站" }
                h.btnAllOut.text = "一键出库(${item.count}件)"
                h.btnAllOut.setOnClickListener { onAllOut(item.station, item.infos) }
            }
            is SmsListItem.SmsItem -> {
                val h = holder as ItemViewHolder
                val info = item.info
                h.tvCode.text = info.pickupCode
                h.tvTime.text = formatTime(info.time)
                h.tvBody.text = info.body
            }
            is SmsListItem.DonateItem -> {
                val h = holder as DonateViewHolder
                if (donateQrBitmap == null) {
                    donateQrBitmap = generateDualQrCode()
                }
                h.ivQrCode.setImageBitmap(donateQrBitmap)
                h.ivQrCode.setOnClickListener {
                    donateQrBitmap?.let { onQrClick(it) }
                }
                h.ivQrCode.setOnLongClickListener {
                    donateQrBitmap?.let { onQrLongClick(it) }
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitGroupedList(smsList: List<SmsInfo>) {
        val newItems = mutableListOf<SmsListItem>()

        if (smsList.isEmpty()) {
            newItems.add(SmsListItem.DonateItem)
        } else {
            val grouped = smsList.groupBy { it.station.ifEmpty { "未知驿站" } }
            for ((station, infos) in grouped.toSortedMap()) {
                val sortedInfos = infos.sortedByDescending { it.time }
                newItems.add(SmsListItem.StationHeader(station, sortedInfos.size, sortedInfos))
                sortedInfos.forEach {
                    newItems.add(SmsListItem.SmsItem(it))
                }
            }
        }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldItem = items[oldPos]
                val newItem = newItems[newPos]
                return when {
                    oldItem is SmsListItem.SmsItem && newItem is SmsListItem.SmsItem ->
                        oldItem.info.id == newItem.info.id
                    oldItem is SmsListItem.StationHeader && newItem is SmsListItem.StationHeader ->
                        oldItem.station == newItem.station
                    oldItem is SmsListItem.DonateItem && newItem is SmsListItem.DonateItem -> true
                    else -> false
                }
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                return items[oldPos] == newItems[newPos]
            }
        })

        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    fun getItemAt(position: Int): SmsListItem? {
        return items.getOrNull(position)
    }

    private fun formatTime(time: Long): String {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val yesterdayStart = (todayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -1)
        }

        val dayBeforeStart = (todayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -2)
        }

        return when {
            time >= todayStart.timeInMillis -> "今天 ${formatHourMinute(time)}"
            time >= yesterdayStart.timeInMillis -> "昨天 ${formatHourMinute(time)}"
            time >= dayBeforeStart.timeInMillis -> "前天 ${formatHourMinute(time)}"
            else -> {
                val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                sdf.format(Date(time))
            }
        }
    }

    private fun formatHourMinute(time: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(time))
    }

    private fun generateGrayQrCode(content: String, size: Int): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) QR_GRAY else Color.TRANSPARENT)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun generateDualQrCode(): Bitmap? {
        val qr1 = generateGrayQrCode(DONATE_URL_ALIPAY, QR_SIZE) ?: return null

        val result = Bitmap.createBitmap(qr1.width, qr1.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawBitmap(qr1, 0f, 0f, null)

        return result
    }
}
