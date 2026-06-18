package com.example.smspicker

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smspicker.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SmsAdapter
    private val smsList = mutableListOf<SmsInfo>()

    private val pendingOutItems = mutableMapOf<String, SmsInfo>()

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(SmsReceiver.EXTRA_SMS_INFO, SmsInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(SmsReceiver.EXTRA_SMS_INFO) as? SmsInfo
            }
            info?.let {
                if (!SmsStorage.isOut(it.id) && !pendingOutItems.containsKey(it.id)) {
                    addSmsToFront(it)
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.all { it.value }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            Toast.makeText(this, "未授予短信权限，功能无法使用", Toast.LENGTH_LONG).show()
        }
    }

    private val saveImagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingSaveBitmap?.let { saveQrCodeToGallery(it) }
        } else {
            Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
        }
        pendingSaveBitmap = null
    }

    private var pendingSaveBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        SmsStorage.init(this)

        setupRecyclerView()
        setupSwipeRefresh()
        checkAndRequestPermissions()
        registerSmsReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadSmsFromInbox()
        }
    }

    private fun setupRecyclerView() {
        adapter = SmsAdapter(
            onAllOut = { station, infos -> handleAllOut(station, infos) },
            onQrClick = { bitmap -> shareQrCode(bitmap) },
            onQrLongClick = { bitmap -> saveQrCode(bitmap) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewHolder is SmsAdapter.ItemViewHolder) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else 0
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos < 0) return
                val item = adapter.getItemAt(pos)
                if (item is SmsListItem.SmsItem) {
                    handleSwipeOut(item.info)
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary)
        )
        binding.swipeRefresh.setOnRefreshListener {
            loadSmsFromInbox()
        }
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            onPermissionsGranted()
        }
    }

    private fun onPermissionsGranted() {
        if (isMiuiDevice() && !SmsStorage.isMiuiDialogShown()) {
            SmsStorage.setMiuiDialogShown(true)
            showMiuiPermissionDialog()
        } else {
            loadSmsFromInbox()
        }
    }

    private fun isMiuiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val model = Build.MODEL.lowercase()

        return manufacturer.contains("xiaomi") ||
               manufacturer.contains("redmi") ||
               brand.contains("xiaomi") ||
               brand.contains("redmi") ||
               device.contains("xiaomi") ||
               device.contains("redmi") ||
               model.contains("mi") ||
               model.contains("redmi") ||
               getSystemProperty("ro.miui.ui.version.name").isNotEmpty()
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun showMiuiPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要额外设置")
            .setMessage("检测到您使用的是小米/红米手机。MIUI 系统将短信权限分为「普通短信」和「通知类短信」，本应用需要同时获取这两类权限。\n\n请在弹出的设置页面中，手动开启「通知类短信」权限。")
            .setPositiveButton("去设置") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("暂不开启") { _, _ ->
                loadSmsFromInbox()
            }
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSmsFromInbox() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            binding.swipeRefresh.isRefreshing = false
            return
        }
        binding.swipeRefresh.isRefreshing = true
        val days = SmsStorage.getReadDays()
        thread {
            val list = SmsReader.readInbox(this, days)
            runOnUiThread {
                smsList.clear()
                for (info in list) {
                    if (!SmsStorage.isOut(info.id) && !pendingOutItems.containsKey(info.id)) {
                        smsList.add(info)
                    }
                }
                updateUI()
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun registerSmsReceiver() {
        val filter = IntentFilter(SmsReceiver.ACTION_NEW_EXPRESS_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(smsReceiver, filter)
        }
    }

    private fun addSmsToFront(info: SmsInfo) {
        if (SmsStorage.isOut(info.id) || pendingOutItems.containsKey(info.id)) return
        if (!smsList.any { it.id == info.id }) {
            smsList.add(0, info)
            updateUI()
        }
    }

    private fun updateUI() {
        adapter.submitGroupedList(smsList)
        binding.tvEmpty.visibility = View.GONE
    }

    private fun handleSwipeOut(info: SmsInfo) {
        pendingOutItems[info.id] = info
        smsList.removeAll { it.id == info.id }
        updateUI()

        Snackbar.make(binding.recyclerView, "已出库: ${info.pickupCode}", Snackbar.LENGTH_LONG)
            .setAction("撤销") {
                pendingOutItems.remove(info.id)
                smsList.add(0, info)
                updateUI()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        val pending = pendingOutItems.remove(info.id)
                        pending?.let {
                            SmsStorage.markOut(it.id)
                        }
                    }
                }
            })
            .show()
    }

    private fun handleAllOut(station: String, infos: List<SmsInfo>) {
        if (infos.isEmpty()) return

        infos.forEach { pendingOutItems[it.id] = it }
        smsList.removeAll { info -> infos.any { it.id == info.id } }
        updateUI()

        Snackbar.make(binding.recyclerView, "$station 的 ${infos.size} 件已出库", Snackbar.LENGTH_LONG)
            .setAction("撤销") {
                infos.forEach { pendingOutItems.remove(it.id) }
                smsList.addAll(0, infos)
                updateUI()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        val toRemove = mutableListOf<String>()
                        infos.forEach {
                            if (pendingOutItems.containsKey(it.id)) {
                                pendingOutItems.remove(it.id)
                                toRemove.add(it.id)
                            }
                        }
                        SmsStorage.markOut(toRemove)
                    }
                }
            })
            .show()
    }

    private fun shareQrCode(bitmap: Bitmap) {
        thread {
            try {
                val sharePath = File(cacheDir, "share").apply { mkdirs() }
                val file = File(sharePath, "donate_qrcode.png")
                val fos = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
                fos.close()

                val uri: Uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                runOnUiThread {
                    startActivity(Intent.createChooser(shareIntent, "分享二维码"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveQrCode(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveQrCodeToGallery(bitmap)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                saveQrCodeToGallery(bitmap)
            } else {
                pendingSaveBitmap = bitmap
                saveImagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun saveQrCodeToGallery(bitmap: Bitmap) {
        thread {
            try {
                val filename = "donate_qrcode_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
                val outputStream: OutputStream?

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SmsPicker")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = uri?.let { contentResolver.openOutputStream(it) }
                } else {
                    @Suppress("DEPRECATION")
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val appDir = File(imagesDir, "SmsPicker")
                    if (!appDir.exists()) appDir.mkdirs()
                    val file = File(appDir, filename)
                    outputStream = FileOutputStream(file)
                }

                outputStream?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }

                runOnUiThread {
                    Toast.makeText(this, "二维码已保存到相册", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
