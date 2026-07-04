package com.projenefes.radar

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.projenefes.radar.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PROJE NEFES — Android Native PoC
 *
 * DÜRÜST KAPSAM:
 *  - Wi-Fi RSSI taraması: GERÇEK (WifiManager.getScanResults)
 *  - LocalOnlyHotspot: GERÇEK Android API (kullanıcı izniyle, ayara gitmeden)
 *  - Jiroskop/yön: GERÇEK (SensorManager, ROTATION_VECTOR)
 *  - Mikrofon vurma/darbe tespiti: GERÇEK (AudioRecord + enerji eşiği)
 *  - "Duvar arkası nefes tespiti" (CSI tabanlı): BU UYGULAMADA YOK.
 *    Sebep: CSI ham verisi Android'de tüketici cihazlarda erişilebilir değildir
 *    (root + özel firmware + genelde iki-nokta ölçüm gerektirir). Bu PoC, gerçekte
 *    çalışabilen bir alternatif olan AKUSTIK VURMA TESPİTİNE odaklanır — bu, gerçek
 *    USAR (kentsel arama-kurtarma) ekiplerinin bugün kullandığı bilinen bir yöntemdir.
 */
class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    // --- Ses (mikrofon) tespiti ---
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )

    // Vurma tespiti eşik ve durum
    private var noiseFloor = 0.0
    private var knockCooldownMs = 0L
    private var knockCount = 0
    private val knockTimestamps = mutableListOf<Long>()

    // Açı (jiroskop)
    private var currentAzimuth = 0f
    private var currentPitch = 0f

    private val handler = Handler(Looper.getMainLooper())

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CHANGE_WIFI_STATE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        binding.txtStatus.text = "İzinler bekleniyor..."
        requestAllPermissions()

        binding.btnHotspot.setOnClickListener { startRealHotspot() }
        binding.btnScanWifi.setOnClickListener { scanRealWifi() }
        binding.btnListen.setOnClickListener { toggleListening() }
    }

    // ---------------- İZİNLER ----------------
    private fun requestAllPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        } else {
            binding.txtStatus.text = "İzinler tamam. Hazır."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        binding.txtStatus.text = if (allGranted) "İzinler tamam. Hazır." else "Bazı izinler reddedildi — ilgili özellik çalışmayacak."
    }

    // ---------------- GERÇEK LOCALONLYHOTSPOT ----------------
    // Bu, Android 8.0+ (API 26) SDK'sının resmi, dokümante edilmiş bir parçasıdır.
    // Kullanıcı onayı gerektirir (sistem izin diyaloğu) ama AYARLAR EKRANINA GİTMEDEN,
    // uygulama içinden gerçek bir yerel hotspot başlatır. Bu bir "bypass" değil, SDK'nın
    // kendi sunduğu resmi yoldur.
    private fun startRealHotspot() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            binding.txtStatus.text = "Hotspot için konum izni gerekli (Android kuralı)."
            requestAllPermissions()
            return
        }
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    val config = reservation.wifiConfiguration
                    binding.txtStatus.text = "Hotspot AKTİF — SSID: ${config?.SSID ?: "?"}"
                    Log.i("ProjeNefes", "LocalOnlyHotspot started: ${config?.SSID}")
                }
                override fun onStopped() {
                    binding.txtStatus.text = "Hotspot durduruldu."
                }
                override fun onFailed(reason: Int) {
                    binding.txtStatus.text = "Hotspot başlatılamadı (kod: $reason)."
                }
            }, handler)
        } catch (e: SecurityException) {
            binding.txtStatus.text = "Hotspot izni reddedildi: ${e.message}"
        }
    }

    // ---------------- GERÇEK WI-FI RSSI TARAMASI ----------------
    // Not: Bu sadece çevredeki erişim noktalarının sinyal gücünü (RSSI) verir.
    // CSI (kanal durum bilgisi) DEĞİLDİR ve tek başına "canlı tespiti" için
    // bilimsel olarak yeterli değildir — burada şantiye/ortam bilgisi olarak sunulur.
    private fun scanRealWifi() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            binding.txtStatus.text = "Wi-Fi taraması için konum izni gerekli (Android kuralı)."
            requestAllPermissions()
            return
        }
        val success = wifiManager.startScan()
        val results = wifiManager.scanResults
        val sb = StringBuilder()
        results.sortedByDescending { it.level }.take(6).forEach {
            sb.append("${it.SSID.ifBlank { "(gizli)" }}: ${it.level} dBm\n")
        }
        binding.txtWifiList.text = if (sb.isEmpty()) "Sonuç yok (tarama: $success)" else sb.toString()
    }

    // ---------------- GERÇEK JİROSKOP / YÖN ----------------
    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopListening()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            currentPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            binding.txtAngle.text = "Açı: %.1f° / %.1f°".format(currentAzimuth, currentPitch)
            binding.radarView.setAngle(currentAzimuth, currentPitch)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------------- GERÇEK MİKROFON VURMA/DARBE TESPİTİ ----------------
    // Yöntem: kısa pencerede RMS enerji hesapla, hareketli ortalamadan belirgin
    // şekilde sapan ani darbeleri (knock) say. Enkaz altında ritmik vurma sesi
    // gerçek USAR protokollerinde kullanılan bilinen bir yaşam belirtisi arama yöntemidir.
    private fun toggleListening() {
        if (isListening) stopListening() else startListening()
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            binding.txtStatus.text = "Mikrofon izni gerekli."
            requestAllPermissions()
            return
        }
        if (bufferSize <= 0) {
            binding.txtStatus.text = "Ses donanımı desteklenmiyor."
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )
        audioRecord?.startRecording()
        isListening = true
        binding.btnListen.text = "■ DİNLEMEYİ DURDUR"
        binding.txtStatus.text = "Dinleniyor — vurma/darbe aranıyor..."
        Thread { audioLoop() }.start()
    }

    private fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        binding.btnListen.text = "▶ DİNLEMEYİ BAŞLAT"
        runOnUiThread { binding.radarView.setKnockDetected(false) }
    }

    private fun audioLoop() {
        val buffer = ShortArray(bufferSize)
        noiseFloor = 0.0
        var warmupFrames = 0

        while (isListening) {
            val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            if (read <= 0) continue

            // RMS enerji hesapla
            var sum = 0.0
            for (i in 0 until read) sum += buffer[i].toDouble() * buffer[i]
            val rms = sqrt(sum / read)

            // İlk ~20 çerçeve ile ortam gürültü tabanını kalibre et
            if (warmupFrames < 20) {
                noiseFloor = (noiseFloor * warmupFrames + rms) / (warmupFrames + 1)
                warmupFrames++
                continue
            }

            val now = System.currentTimeMillis()
            val threshold = noiseFloor * 3.5 + 200 // ani darbe eşiği

            if (rms > threshold && now > knockCooldownMs) {
                knockCooldownMs = now + 250 // aynı darbeyi tekrar saymamak için kısa kilit
                knockTimestamps.add(now)
                knockTimestamps.removeAll { now - it > 8000 } // son 8 sn pencere
                knockCount = knockTimestamps.size

                runOnUiThread {
                    binding.radarView.setKnockDetected(true)
                    binding.txtStatus.text = "DARBE ALGILANDI — son 8 sn'de $knockCount vurma"
                }
                handler.postDelayed({ binding.radarView.setKnockDetected(false) }, 400)
            }

            // Gürültü tabanını yavaşça adapte et (ani darbeleri dahil etmeden)
            if (rms < threshold) {
                noiseFloor = noiseFloor * 0.98 + rms * 0.02
            }
        }
    }
}
