package rs.glodar

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ListenerService : Service() {
    companion object {
        const val ACTION_STOP = "rs.glodar.STOP"
        const val CH = "glodar"
    }

    @Volatile private var running = false
    private var thread: Thread? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (running) return START_STICKY
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CH, "Slušanje", NotificationManager.IMPORTANCE_LOW))
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, 1, notif(Store.all(this).size), type)
        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glodar:listen").apply { acquire() }
        running = true
        State.running = true
        thread = Thread { loop() }.also { it.start() }
        return START_STICKY
    }

    private fun notif(count: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListenerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Noćni slušalac radi")
            .setContentText("Zabeleženo događaja: $count")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Zaustavi", stop)
            .build()
    }

    private class Cur(val t: Long) {
        var last = t; var n = 0; var f = 0.0; var pk = -999.0; var tonal = 0.0
    }

    private fun loop() {
        val sr = 48000
        val n = 4096
        val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        fun make(src: Int) = AudioRecord(src, sr, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(minBuf, n * 4))
        var rec = make(MediaRecorder.AudioSource.UNPROCESSED)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); rec = make(MediaRecorder.AudioSource.MIC)
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { running = false; State.running = false; stopSelf(); return }
        rec.startRecording()

        val s = ShortArray(n)
        val re = DoubleArray(n); val im = DoubleArray(n)
        val win = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
        val hz = sr.toDouble() / n
        val lo = (3000 / hz).toInt()
        val hi = min((23000 / hz).toInt(), n / 2 - 1)
        var base = Double.NaN
        var loudN = 0
        var cur: Cur? = null

        fun finish() {
            val c = cur ?: return
            if (c.n >= 2) {
                Store.add(this, Ev(c.t, max(0.1, (c.last - c.t) / 1000.0), c.f, c.tonal > 22))
                getSystemService(NotificationManager::class.java).notify(1, notif(Store.all(this).size))
            }
            cur = null
        }

        while (running) {
            var got = 0
            while (got < n && running) {
                val r = rec.read(s, got, n - got)
                if (r < 0) { running = false; break }
                got += r
            }
            if (got < n) break
            for (i in 0 until n) { re[i] = s[i] / 32768.0 * win[i]; im[i] = 0.0 }
            Fft.run(re, im)

            var sum = 0.0; var mx = -999.0; var mi = lo
            for (i in lo..hi) {
                val db = 20 * log10(sqrt(re[i] * re[i] + im[i] * im[i]) / n + 1e-9)
                sum += db
                if (db > mx) { mx = db; mi = i }
            }
            val mean = sum / (hi - lo + 1)
            val tonal = mx - mean
            if (base.isNaN()) base = mean
            val over = mean - base
            State.level = over
            val thr = Store.threshold(this)
            val now = System.currentTimeMillis()
            val loud = over > thr || (tonal > 30 && over > thr / 2.0)

            if (loud) {
                loudN++
                if (cur == null && loudN >= 2) cur = Cur(now)
                cur?.let {
                    it.last = now; it.n++
                    if (mx > it.pk) { it.pk = mx; it.f = mi * hz }
                    it.tonal = max(it.tonal, tonal)
                }
            } else {
                loudN = 0
                base = base * 0.985 + mean * 0.015
                if (cur != null && now - cur!!.last > 1200) finish()
            }
        }
        finish()
        rec.stop(); rec.release()
    }

    override fun onDestroy() {
        running = false
        State.running = false
        try { thread?.join(1500) } catch (_: Exception) {}
        try { wl?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
