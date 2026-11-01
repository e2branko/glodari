package rs.glodar

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var btn: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvThr: TextView
    private lateinit var tvLog: TextView
    private val h = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("dd.MM. HH:mm:ss", Locale("sr"))
    private val refresh = object : Runnable {
        override fun run() { update(); h.postDelayed(this, 1000) }
    }

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.RECORD_AUDIO] == true) startSvc()
        else Toast.makeText(this, "Bez dozvole za mikrofon aplikacija ne može da radi.", Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(size: Float, color: Int = Color.parseColor("#d8dee6")) = TextView(this).apply {
        textSize = size; setTextColor(color)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0b0d10"))
            addView(root)
        }
        setContentView(scroll)

        root.addView(tv(22f).apply { text = "🐭 Noćni slušalac glodara" })
        tvStatus = tv(14f, Color.parseColor("#7d8894"))
        root.addView(tvStatus)

        btn = button("Započni slušanje") { toggle() }
        root.addView(btn)

        tvCount = tv(30f, Color.parseColor("#e0a458")).apply { setPadding(0, dp(16), 0, 0) }
        root.addView(tvCount)

        tvThr = tv(14f).apply { setPadding(0, dp(12), 0, 0) }
        root.addView(tvThr)
        val sb = SeekBar(this).apply {
            max = 22
            progress = Store.threshold(this@MainActivity) - 3
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    Store.setThreshold(this@MainActivity, p + 3); update()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(sb)

        root.addView(button("Kopiraj dnevnik") {
            val text = Store.all(this).joinToString("\n") { line(it) }
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("dnevnik", text))
            Toast.makeText(this, "Kopirano.", Toast.LENGTH_SHORT).show()
        })
        root.addView(button("Obriši dnevnik") { Store.clear(this); update() })
        root.addView(button("Podešavanja baterije (važno za Xiaomi)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        tvLog = tv(13f).apply { setPadding(0, dp(16), 0, 0) }
        root.addView(tvLog)
    }

    private fun line(e: Ev) =
        "${fmt.format(Date(e.t))}  ${if (e.sq) "cijuk" else "šuškanje"}  " +
            "${"%.1f".format(e.f / 1000)} kHz  ${"%.1f".format(e.d)} s"

    private fun toggle() {
        if (State.running) {
            startService(Intent(this, ListenerService::class.java).setAction(ListenerService.ACTION_STOP))
        } else {
            val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.launch(p.toTypedArray())
        }
    }

    private fun startSvc() {
        val i = Intent(this, ListenerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun update() {
        val ev = Store.all(this)
        btn.text = if (State.running) "Zaustavi slušanje" else "Započni slušanje"
        tvStatus.text = if (State.running)
            "Sluša se (radi i sa ugašenim ekranom). Nivo iznad pozadine: ${"%.1f".format(State.level)} dB"
        else "Zaustavljeno."
        tvCount.text = "${ev.size} događaja"
        tvThr.text = "Osetljivost – prag: ${Store.threshold(this)} dB (niže = osetljivije)"
        tvLog.text = if (ev.isEmpty()) "Još nema zabeleženih zvukova."
        else ev.takeLast(80).reversed().joinToString("\n") { line(it) }
    }

    override fun onResume() { super.onResume(); h.post(refresh) }
    override fun onPause() { super.onPause(); h.removeCallbacks(refresh) }
}
