package com.bhaisquad.battlearenamax

import android.animation.ObjectAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var splashLayout: LinearLayout
    private lateinit var lobbyScroll: ScrollView
    private lateinit var arenaLayout: FrameLayout

    private lateinit var slideBadgeTv: TextView
    private lateinit var slideTitleTv: TextView
    private lateinit var slideDescTv: TextView
    private lateinit var loadPercentTv: TextView
    private lateinit var loadStatusTv: TextView
    private lateinit var stripedBarView: StripedProgressView

    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0
    private var slideIndex = 0
    private var selectedBundleColor = Color.parseColor("#38BDF8")
    private var squadJoinedCount = 0
    private var score = 0
    private var jetEnabled = false
    private var gunMode = 1

    // Gaming Audio Engine (Background Beat + SFX)
    private var musicEnabled = true
    private var musicThread: Thread? = null
    @Volatile private var keepMusicRunning = true
    private var toneGen: ToneGenerator? = null

    private val slides = listOf(
        Triple(
            "SLIDE 1 / 4 • ANDROID NATIVE",
            "Smooth Android Touch & Performance",
            "एंड्रॉइड फ़ोन के लिए खास वर्चुअल जॉयस्टिक, बड़े टच बटन और स्मूथ ग्राफिक्स।"
        ),
        Triple(
            "SLIDE 2 / 4 • LOBBY & SQUAD",
            "Friends Invite, Guild & Bundles",
            "लॉबी में दोस्तों को इनवाइट करें, गिल्ड देखें, बंडल चुनें और इमोट्स चलाएं।"
        ),
        Triple(
            "SLIDE 3 / 4 • WEAPONS & JET",
            "PLASMA-1, BURST-2 & Sky Jet",
            "JET ऑन करके हवा में उठें और GUN SWAP से सिंगल या ट्रिपल शॉट बदलें।"
        ),
        Triple(
            "SLIDE 4 / 4 • READY TO PLAY",
            "High-Action Android Arena",
            "लोडिंग 100% होते ही वॉर्निंग कन्फ़र्म करें और सीधे अरीना में उतरें।"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {
            toneGen = null
        }

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#030712"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        buildSplashScreen()
        buildLobbyScreen()
        buildArenaScreen()

        rootLayout.addView(splashLayout)
        rootLayout.addView(lobbyScroll)
        rootLayout.addView(arenaLayout)

        lobbyScroll.visibility = View.GONE
        arenaLayout.visibility = View.GONE
        splashLayout.visibility = View.VISIBLE

        setContentView(rootLayout)
        startGamingBeatLoop()
        startSlidesAndLoading()
    }

    // ================= GAMING BEAT & SOUND EFFECTS =================
    private fun startGamingBeatLoop() {
        keepMusicRunning = true
        musicThread = Thread {
            val sampleRate = 22050
            val stepMs = 220
            val samplesPerStep = (sampleRate * stepMs) / 1000
            val buffer = ShortArray(samplesPerStep)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samplesPerStep * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()

            val bassNotes = doubleArrayOf(110.0, 110.0, 146.83, 130.81, 98.0, 98.0, 130.81, 146.83)
            var step = 0

            while (keepMusicRunning) {
                if (!musicEnabled) {
                    Thread.sleep(120)
                    continue
                }
                val freq = bassNotes[step % bassNotes.size]
                val isKick = (step % 2 == 0)

                for (i in 0 until samplesPerStep) {
                    val t = i.toDouble() / sampleRate
                    val env = 1.0 - (i.toDouble() / samplesPerStep)
                    val synthWave = sin(2.0 * PI * freq * t) * 0.35
                    val kickPitch = 140.0 * (1.0 - (i.toDouble() / samplesPerStep)) + 42.0
                    val kickWave = if (isKick) sin(2.0 * PI * kickPitch * t) * 0.45 else 0.0
                    val sample = ((synthWave + kickWave) * env * Short.MAX_VALUE * 0.55).toInt()
                    buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                try {
                    track.write(buffer, 0, samplesPerStep)
                } catch (_: Exception) {
                    break
                }
                step++
            }

            try {
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun playFireSfx(burst: Boolean) {
        if (!musicEnabled) return
        try {
            toneGen?.startTone(
                if (burst) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP,
                if (burst) 140 else 80
            )
        } catch (_: Exception) {
        }
    }

    private fun playToggleSfx() {
        if (!musicEnabled) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 70)
        } catch (_: Exception) {
        }
    }

    // ================= 1. LOADING SCREEN (SLIDES + YELLOW/BLACK BAR) =================
    private fun buildSplashScreen() {
        splashLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(44, 60, 44, 60)
            setBackgroundColor(Color.parseColor("#060D1F"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val headerTv = TextView(this).apply {
            text = "BATTLE ARENA MAX"
            textSize = 24f
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val cardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(40, 40, 40, 40)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.weight = 1f
            lp.gravity = Gravity.CENTER
            layoutParams = lp
        }

        slideBadgeTv = TextView(this).apply {
            text = slides[0].first
            textSize = 12f
            setTextColor(Color.parseColor("#22C55E"))
            setPadding(0, 0, 0, 14)
        }

        slideTitleTv = TextView(this).apply {
            text = slides[0].second
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 14)
        }

        slideDescTv = TextView(this).apply {
            text = slides[0].third
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
        }

        cardBox.addView(slideBadgeTv)
        cardBox.addView(slideTitleTv)
        cardBox.addView(slideDescTv)

        val progressHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 28, 0, 12)
        }

        loadStatusTv = TextView(this).apply {
            text = "Assets load ho rahe hain..."
            textSize = 13f
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        loadPercentTv = TextView(this).apply {
            text = "0%"
            textSize = 13f
            setTextColor(Color.parseColor("#FACC15"))
        }

        progressHeader.addView(loadStatusTv)
        progressHeader.addView(loadPercentTv)

        stripedBarView = StripedProgressView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56
            )
        }

        splashLayout.addView(headerTv)
        splashLayout.addView(cardBox)
        splashLayout.addView(progressHeader)
        splashLayout.addView(stripedBarView)
    }

    private fun startSlidesAndLoading() {
        val slideRunnable = object : Runnable {
            override fun run() {
                if (progress >= 100) return
                slideIndex = (slideIndex + 1) % slides.size
                val item = slides[slideIndex]
                slideBadgeTv.text = item.first
                slideTitleTv.text = item.second
                slideDescTv.text = item.third
                handler.postDelayed(this, 1800)
            }
        }
        handler.postDelayed(slideRunnable, 1800)

        val loadRunnable = object : Runnable {
            override fun run() {
                progress = min(100, progress + 2)
                stripedBarView.setProgressPercent(progress)
                loadPercentTv.text = "$progress%"

                loadStatusTv.text = when {
                    progress < 40 -> "Loading Android arena & graphics..."
                    progress < 80 -> "Preparing lobby, bundles & invites..."
                    progress < 100 -> "Almost ready..."
                    else -> "100% Complete"
                }

                if (progress < 100) {
                    handler.postDelayed(this, 85)
                } else {
                    handler.postDelayed({ showWarningDialog() }, 250)
                }
            }
        }
        handler.postDelayed(loadRunnable, 85)
    }

    // ================= 2. WARNING AT 100% =================
    private fun showWarningDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ WARNING")
            .setMessage(
                "अगर गेम चलाने पर आपका फ़ोन गर्म होता है, लैग करता है या हैंग करने लगता है, " +
                    "तो इसके लिए हम ज़िम्मेदार नहीं होंगे। आगे बढ़ने के लिए CONFIRM दबाएं।"
            )
            .setCancelable(false)
            .setPositiveButton("CONFIRM") { dialog, _ ->
                playToggleSfx()
                dialog.dismiss()
                splashLayout.visibility = View.GONE
                lobbyScroll.visibility = View.VISIBLE
            }
            .show()
    }

    // ================= 3. FREE FIRE MAX STYLE LOBBY =================
    private fun buildLobbyScreen() {
        lobbyScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#090D16"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 48)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val playerTitle = TextView(this).apply {
            text = "PRO PLAYER • BHAI SQUAD"
            textSize = 15f
            setTextColor(Color.parseColor("#38BDF8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val musicBtn = Button(this).apply {
            text = "🎵 ON"
            setOnClickListener {
                musicEnabled = !musicEnabled
                text = if (musicEnabled) "🎵 ON" else "🔇 OFF"
                Toast.makeText(
                    this@MainActivity,
                    if (musicEnabled) "Gaming Song ON" else "Gaming Song OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val guildBtn = Button(this).apply {
            text = "Guild"
            setOnClickListener {
                playToggleSfx()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("🛡️ Guild Hub")
                    .setMessage("Guild: BHAI SQUAD\nLevel: Lv. 6\nMembers: 24 / 30\nWeekly Glory: 12,450")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        val settingsBtn = Button(this).apply {
            text = "Settings"
            setOnClickListener {
                playToggleSfx()
                Toast.makeText(this@MainActivity, "Android Graphics: Smooth 60 FPS Ready", Toast.LENGTH_SHORT).show()
            }
        }

        topBar.addView(playerTitle)
        topBar.addView(musicBtn)
        topBar.addView(guildBtn)
        topBar.addView(settingsBtn)
        container.addView(topBar)

        val charPreview = TextView(this).apply {
            text = "🔥 LOBBY CHARACTER 🔥"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(selectedBundleColor)
            setPadding(24, 56, 24, 56)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 24)
            layoutParams = lp
        }
        container.addView(charPreview)

        container.addView(sectionLabel("👕 Bundles"))
        val bundleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 20)
        }
        val bundles = listOf(
            "Cyber Blue" to "#38BDF8",
            "Crimson" to "#EF4444",
            "Emerald" to "#22C55E",
            "Gold Max" to "#FACC15"
        )
        bundles.forEach { (bName, hex) ->
            val bBtn = Button(this).apply {
                text = bName
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    playToggleSfx()
                    selectedBundleColor = Color.parseColor(hex)
                    charPreview.setBackgroundColor(selectedBundleColor)
                    Toast.makeText(this@MainActivity, "Bundle: $bName", Toast.LENGTH_SHORT).show()
                }
            }
            bundleRow.addView(bBtn)
        }
        container.addView(bundleRow)

        container.addView(sectionLabel("🕺 Emotes"))
        val emoteRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 20)
        }
        listOf("🔥 Booyah", "⚡ Flex", "👑 King", "🚀 Sky").forEach { em ->
            val eBtn = Button(this).apply {
                text = em
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    playToggleSfx()
                    charPreview.text = "$em  $em"
                    ObjectAnimator.ofFloat(charPreview, "translationY", 0f, -24f, 0f).apply {
                        duration = 450
                        start()
                    }
                }
            }
            emoteRow.addView(eBtn)
        }
        container.addView(emoteRow)

        container.addView(sectionLabel("👥 Invite Friends"))
        val squadStatusTv = TextView(this).apply {
            text = "Squad: You (1/4)"
            textSize = 13f
            setTextColor(Color.parseColor("#86EFAC"))
            setPadding(0, 6, 0, 10)
        }
        container.addView(squadStatusTv)

        listOf("Satyam_Pro", "Bhai_Fierce", "Legend_X").forEach { friend ->
            val fRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#1E293B"))
                setPadding(24, 16, 24, 16)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, 12)
                layoutParams = lp
            }
            val fTv = TextView(this).apply {
                text = "🟢 $friend"
                textSize = 14f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val invBtn = Button(this).apply {
                text = "INVITE"
                setOnClickListener {
                    if (text != "JOINED") {
                        playToggleSfx()
                        text = "JOINED"
                        squadJoinedCount = min(3, squadJoinedCount + 1)
                        squadStatusTv.text = "Squad: You + $friend (${squadJoinedCount + 1}/4)"
                    }
                }
            }
            fRow.addView(fTv)
            fRow.addView(invBtn)
            container.addView(fRow)
        }

        val startBtn = Button(this).apply {
            text = "START MATCH ▶"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#FACC15"))
            setTextColor(Color.parseColor("#090D16"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 24, 0, 0)
            layoutParams = lp
            setOnClickListener {
                playFireSfx(true)
                lobbyScroll.visibility = View.GONE
                arenaLayout.visibility = View.VISIBLE
            }
        }
        container.addView(startBtn)

        lobbyScroll.addView(container)
    }

    private fun sectionLabel(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.parseColor("#FACC15"))
        }
    }

    // ================= 4. ANDROID TOUCH ARENA =================
    private fun buildArenaScreen() {
        arenaLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#040918"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scoreTv = TextView(this).apply {
            text = "Score: 0 | Gun: PLASMA-1"
            textSize = 14f
            setTextColor(Color.parseColor("#FACC15"))
            setBackgroundColor(Color.parseColor("#CC0F172A"))
            setPadding(24, 16, 24, 16)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
            lp.setMargins(24, 24, 0, 0)
            layoutParams = lp
        }

        val lobbyBackBtn = Button(this).apply {
            text = "⬅ LOBBY"
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            lp.setMargins(0, 20, 24, 0)
            layoutParams = lp
            setOnClickListener {
                playToggleSfx()
                arenaLayout.visibility = View.GONE
                lobbyScroll.visibility = View.VISIBLE
            }
        }

        val arenaCanvas = ArenaTouchView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val actionsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            )
            lp.setMargins(0, 0, 28, 36)
            layoutParams = lp
        }

        val jetBtn = Button(this).apply {
            text = "JET: OFF"
            setOnClickListener {
                playToggleSfx()
                jetEnabled = !jetEnabled
                text = if (jetEnabled) "JET: ON" else "JET: OFF"
                arenaCanvas.setJetMode(jetEnabled)
            }
        }

        val gunBtn = Button(this).apply {
            text = "GUN SWAP"
            setOnClickListener {
                playToggleSfx()
                gunMode = if (gunMode == 1) 2 else 1
                scoreTv.text = "Score: $score | Gun: ${if (gunMode == 1) "PLASMA-1" else "BURST-2"}"
            }
        }

        val fireBtn = Button(this).apply {
            text = "FIRE"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                playFireSfx(gunMode == 2)
                val gained = arenaCanvas.fireAtTarget(gunMode)
                if (gained > 0) {
                    score += gained
                    scoreTv.text = "Score: $score | Gun: ${if (gunMode == 1) "PLASMA-1" else "BURST-2"}"
                }
            }
        }

        actionsPanel.addView(jetBtn)
        actionsPanel.addView(gunBtn)
        actionsPanel.addView(fireBtn)

        arenaLayout.addView(arenaCanvas)
        arenaLayout.addView(scoreTv)
        arenaLayout.addView(lobbyBackBtn)
        arenaLayout.addView(actionsPanel)
    }

    override fun onDestroy() {
        super.onDestroy()
        keepMusicRunning = false
        handler.removeCallbacksAndMessages(null)
        try {
            toneGen?.release()
        } catch (_: Exception) {
        }
    }

    class StripedProgressView(context: android.content.Context) : View(context) {
        private var percent = 0
        private val bgPaint = Paint().apply { color = Color.parseColor("#0F172A") }
        private val borderPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun setProgressPercent(p: Int) {
            percent = max(0, min(100, p))
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRoundRect(0f, 0f, w, h, 28f, 28f, bgPaint)
            canvas.drawRoundRect(0f, 0f, w, h, 28f, 28f, borderPaint)

            val fillW = (w - 8f) * (percent / 100f)
            if (fillW > 0f) {
                val shader = LinearGradient(
                    0f, 0f, 28f, 28f,
                    intArrayOf(
                        Color.parseColor("#FACC15"),
                        Color.parseColor("#FACC15"),
                        Color.parseColor("#090D16"),
                        Color.parseColor("#090D16")
                    ),
                    floatArrayOf(0f, 0.5f, 0.5f, 1f),
                    Shader.TileMode.REPEAT
                )
                stripePaint.shader = shader
                canvas.drawRoundRect(4f, 4f, 4f + fillW, h - 4f, 24f, 24f, stripePaint)
            }
        }
    }

    class ArenaTouchView(context: android.content.Context) : View(context) {
        private var playerX = 260f
        private var playerY = 760f
        private var jetActive = false
        private var targetX = 680f
        private var targetY = 420f
        private var targetDir = 1f

        private val joyCenterX = 210f
        private val joyCenterY = 1280f
        private val joyRadius = 110f
        private var knobX = 210f
        private var knobY = 1280f

        private val bgPaint = Paint().apply { color = Color.parseColor("#040918") }
        private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38BDF8") }
        private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF4444") }
        private val joyRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FACC15") }

        fun setJetMode(enabled: Boolean) {
            jetActive = enabled
            playerPaint.color = if (enabled) Color.parseColor("#22C55E") else Color.parseColor("#38BDF8")
            invalidate()
        }

        fun fireAtTarget(gunMode: Int): Int {
            val hitRange = if (gunMode == 2) 210f else 150f
            val dist = hypot((playerX - targetX).toDouble(), (playerY - targetY).toDouble())
            return if (dist <= hitRange) {
                targetX = 200f + (Math.random() * 550f).toFloat()
                targetY = 250f + (Math.random() * 450f).toFloat()
                invalidate()
                if (gunMode == 2) 20 else 10
            } else {
                0
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - joyCenterX
                    val dy = event.y - joyCenterY
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist <= joyRadius * 1.4f) {
                        val clamped = min(dist, joyRadius)
                        val angle = Math.atan2(dy.toDouble(), dx.toDouble())
                        knobX = joyCenterX + (clamped * Math.cos(angle)).toFloat()
                        knobY = joyCenterY + (clamped * Math.sin(angle)).toFloat()

                        playerX = max(70f, min(width - 70f, playerX + (dx / max(1f, dist)) * 14f))
                        playerY = max(160f, min(height - 140f, playerY + (dy / max(1f, dist)) * 14f))
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    knobX = joyCenterX
                    knobY = joyCenterY
                    invalidate()
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            targetX += targetDir * 5f
            if (targetX > width - 110f || targetX < 110f) targetDir *= -1f
            canvas.drawCircle(targetX, targetY, 44f, targetPaint)

            val pRadius = if (jetActive) 48f else 38f
            canvas.drawCircle(playerX, playerY, pRadius, playerPaint)

            val baseY = height - 220f
            val curKnobY = baseY + (knobY - joyCenterY)
            canvas.drawCircle(joyCenterX, baseY, joyRadius, joyRingPaint)
            canvas.drawCircle(knobX, curKnobY, 38f, knobPaint)

            postInvalidateDelayed(16)
        }
    }
}
