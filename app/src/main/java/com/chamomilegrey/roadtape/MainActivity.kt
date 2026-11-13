/*
 * roadtape v0.1.2 — single-screen UI: open-window switch, intensity, demo input panel
 * Created: 2026-11-13 by Claude Fable 5.2 via claude.ai
 * Modified: 2026-11-14 by Claude Fable 5.2 via claude.ai
 * Install path: app/src/main/java/com/chamomilegrey/roadtape/MainActivity.kt
 * Changelog:
 *   v0.1.0 2026-11-13 Claude Fable 5.2 initial screen with simulated and AAOS inputs
 *   v0.1.2 2026-11-14 Claude Fable 5.2 scale UI for large car displays, show why car speed is not connected
 */
package com.chamomilegrey.roadtape

import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.chamomilegrey.roadtape.audio.AudioEngine
import com.chamomilegrey.roadtape.vehicle.CarPropertyVehicleSource
import com.chamomilegrey.roadtape.vehicle.VehicleSignal
import com.chamomilegrey.roadtape.vehicle.VehicleSignalSource
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var engine: AudioEngine
    private var carSource: VehicleSignalSource? = null
    private var carAvailable = false
    private var useCarSpeed = false
    private var inPark = true
    private var carIssue = 0
    private var uiScale = 1f

    private lateinit var powerButton: Button
    private lateinit var speedText: TextView
    private lateinit var sourceText: TextView
    private lateinit var gearHint: TextView
    private lateinit var speedBar: SeekBar
    private lateinit var carSpeedCheck: CheckBox
    private var gearButtons: List<Button> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = AudioEngine(this)
        engine.onForcedOff = { refreshPower() }
        buildUi()
        connectCar()
    }

    override fun onDestroy() {
        carSource?.stop()
        engine.release()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildUi()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQUEST_CAR_SPEED) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCarSource()
        } else {
            carIssue = R.string.source_sim_no_permission
            refreshSource()
        }
    }

    // ---- vehicle signals -------------------------------------------------

    private fun connectCar() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        if (checkSelfPermission(PERMISSION_CAR_SPEED) == PackageManager.PERMISSION_GRANTED) {
            startCarSource()
        } else {
            requestPermissions(arrayOf(PERMISSION_CAR_SPEED), REQUEST_CAR_SPEED)
        }
    }

    private fun startCarSource() {
        val source = CarPropertyVehicleSource(this)
        carAvailable = source.start { signal -> runOnUiThread { onCarSignal(signal) } }
        if (carAvailable) {
            carSource = source
            useCarSpeed = true
            carIssue = 0
        } else {
            carIssue = R.string.source_sim_unavailable
        }
        refreshSource()
    }

    private fun onCarSignal(signal: VehicleSignal) {
        if (!useCarSpeed) return
        signal.speedKph?.let { setSpeed(it, fromCar = true) }
        signal.inPark?.let { setPark(it) }
    }

    private fun setSpeed(kph: Float, fromCar: Boolean) {
        engine.synth.speedKph = kph
        speedText.text = getString(R.string.speed_format, kph.roundToInt())
        if (fromCar) speedBar.progress = kph.roundToInt().coerceIn(0, speedBar.max)
    }

    private fun setPark(park: Boolean) {
        inPark = park
        gearHint.setText(if (park) R.string.gear_park_hint else R.string.gear_drive_hint)
        gearButtons.forEachIndexed { index, button -> styleChoice(button, (index == 0) == park) }
    }

    private fun refreshSource() {
        sourceText.setText(
            when {
                useCarSpeed -> R.string.source_car
                carIssue != 0 -> carIssue
                else -> R.string.source_sim
            }
        )
        carSpeedCheck.isEnabled = carAvailable
        carSpeedCheck.isChecked = useCarSpeed
        speedBar.isEnabled = !useCarSpeed
    }

    private fun refreshPower() {
        val on = engine.synth.enabled
        powerButton.setText(if (on) R.string.open_window_off else R.string.open_window_on)
        powerButton.background = rounded(if (on) COLOR_ACCENT else COLOR_PANEL_LIGHT)
        powerButton.setTextColor(if (on) Color.BLACK else Color.WHITE)
    }

    // ---- UI ----------------------------------------------------------------

    private fun buildUi() {
        // Car displays are large and low-density; scale sizes up so targets stay glanceable.
        uiScale = (resources.configuration.smallestScreenWidthDp / 480f).coerceIn(1f, 2.2f)
        val landscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        val root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val main = buildMainColumn()
        val demo = buildDemoColumn()
        if (landscape) {
            root.addView(main, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(24) })
            root.addView(demo, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        } else {
            root.addView(main, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(24) })
            root.addView(demo, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            addView(root)
        })
        refreshPower()
        refreshSource()
        setPark(inPark)
        speedText.text = getString(R.string.speed_format, engine.synth.speedKph.roundToInt())
    }

    private fun buildMainColumn(): LinearLayout = column().apply {
        addView(label(R.string.app_name, 34f, Color.WHITE, bold = true))
        addView(label(R.string.tagline, 18f, COLOR_MUTED))

        speedText = label(0, 64f, Color.WHITE, bold = true).apply { setPadding(0, dp(20), 0, 0) }
        addView(speedText)
        sourceText = label(R.string.source_sim, 16f, COLOR_MUTED)
        addView(sourceText)

        powerButton = Button(this@MainActivity).apply {
            textSize = sp(26f)
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            minimumHeight = dp(112)
            setOnClickListener {
                val turnOn = !engine.synth.enabled
                if (!engine.setEnabled(turnOn)) {
                    Toast.makeText(this@MainActivity, R.string.focus_denied, Toast.LENGTH_SHORT).show()
                }
                refreshPower()
            }
        }
        addView(powerButton, spaced(top = 20))

        addView(label(R.string.intensity_title, 18f, COLOR_MUTED), spaced(top = 24))
        val levels = floatArrayOf(0.35f, 0.65f, 1f)
        val current = levels.indexOfFirst { it >= engine.synth.intensity - 0.01f }.coerceAtLeast(0)
        addView(
            choiceRow(
                listOf(R.string.intensity_low, R.string.intensity_mid, R.string.intensity_high), current
            ) { engine.synth.intensity = levels[it] }.first,
            spaced(top = 8)
        )

        gearHint = label(R.string.gear_park_hint, 16f, COLOR_MUTED)
        addView(gearHint, spaced(top = 20))
    }

    private fun buildDemoColumn(): LinearLayout = column().apply {
        background = rounded(COLOR_PANEL)
        setPadding(dp(20), dp(20), dp(20), dp(20))
        addView(label(R.string.demo_title, 22f, Color.WHITE, bold = true))
        addView(label(R.string.demo_note, 15f, COLOR_MUTED))

        // Speed
        addView(label(R.string.demo_speed, 18f, Color.WHITE), spaced(top = 16))
        speedBar = seekBar(160, engine.synth.speedKph.roundToInt()) { value ->
            if (!useCarSpeed) setSpeed(value.toFloat(), fromCar = false)
        }
        addView(speedBar, spaced(top = 4))
        carSpeedCheck = CheckBox(this@MainActivity).apply {
            setText(R.string.demo_use_car_speed)
            textSize = sp(16f)
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                useCarSpeed = checked && carAvailable
                refreshSource()
            }
        }
        addView(carSpeedCheck)

        // Wiper → rain
        addView(label(R.string.demo_wiper, 18f, Color.WHITE), spaced(top = 16))
        addView(
            choiceRow(
                listOf(R.string.wiper_off, R.string.wiper_1, R.string.wiper_2, R.string.wiper_3),
                engine.synth.wiperLevel
            ) { engine.synth.wiperLevel = it }.first,
            spaced(top = 8)
        )

        // Real window opening → duck
        val windowLabel = label(0, 18f, Color.WHITE)
        fun showWindow(percent: Int) {
            windowLabel.text = getString(R.string.demo_window) + "  " +
                getString(R.string.percent_format, percent)
        }
        showWindow((engine.synth.windowOpen * 100).roundToInt())
        addView(windowLabel, spaced(top = 16))
        addView(
            seekBar(100, (engine.synth.windowOpen * 100).roundToInt()) { value ->
                engine.synth.windowOpen = value / 100f
                showWindow(value)
            },
            spaced(top = 4)
        )

        // Tunnel
        addView(CheckBox(this@MainActivity).apply {
            setText(R.string.demo_tunnel)
            textSize = sp(18f)
            setTextColor(Color.WHITE)
            minimumHeight = dp(64)
            isChecked = engine.synth.inTunnel
            setOnCheckedChangeListener { _, checked -> engine.synth.inTunnel = checked }
        }, spaced(top = 8))

        // Gear (simulated unless the car reports it)
        addView(label(R.string.demo_gear, 18f, Color.WHITE), spaced(top = 8))
        val (row, buttons) = choiceRow(
            listOf(R.string.gear_p, R.string.gear_d), if (inPark) 0 else 1
        ) { setPark(it == 0) }
        gearButtons = buttons
        addView(row, spaced(top = 8))
    }

    // ---- small view helpers ------------------------------------------------

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun label(textRes: Int, sizeSp: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            if (textRes != 0) setText(textRes)
            textSize = sp(sizeSp)
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun spaced(top: Int) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun seekBar(max: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max
        progress = value.coerceIn(0, max)
        minimumHeight = dp(56)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    /** A row of large mutually exclusive buttons. Returns the row and its buttons. */
    private fun choiceRow(
        labels: List<Int>, selected: Int, onSelect: (Int) -> Unit
    ): Pair<LinearLayout, List<Button>> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = ArrayList<Button>()
        labels.forEachIndexed { index, textRes ->
            val button = Button(this).apply {
                setText(textRes)
                textSize = sp(18f)
                isAllCaps = false
                minimumHeight = dp(76)
                gravity = Gravity.CENTER
                setOnClickListener {
                    buttons.forEachIndexed { i, b -> styleChoice(b, i == index) }
                    onSelect(index)
                }
            }
            styleChoice(button, index == selected)
            buttons += button
            row.addView(button, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        return row to buttons
    }

    private fun styleChoice(button: Button, selected: Boolean) {
        button.background = rounded(if (selected) COLOR_ACCENT else COLOR_PANEL_LIGHT)
        button.setTextColor(if (selected) Color.BLACK else Color.WHITE)
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density * uiScale).roundToInt()

    private fun sp(value: Float): Float = value * uiScale

    companion object {
        private const val PERMISSION_CAR_SPEED = "android.car.permission.CAR_SPEED"
        private const val REQUEST_CAR_SPEED = 1
        private val COLOR_BACKGROUND = Color.parseColor("#0B1218")
        private val COLOR_PANEL = Color.parseColor("#15212B")
        private val COLOR_PANEL_LIGHT = Color.parseColor("#24343F")
        private val COLOR_ACCENT = Color.parseColor("#5EC8E5")
        private val COLOR_MUTED = Color.parseColor("#9FB3C0")
    }
}
