package com.company.touchdiagnostic

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var summaryView: TextView
    private lateinit var coverageView: TextView
    private lateinit var actionView: TextView
    private lateinit var qualityView: TextView
    private lateinit var clearButton: Button
    private lateinit var gridButton: Button
    private lateinit var touchTestView: TouchTestView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        summaryView = findViewById(R.id.summaryText)
        coverageView = findViewById(R.id.coverageText)
        actionView = findViewById(R.id.actionText)
        qualityView = findViewById(R.id.qualityText)
        clearButton = findViewById(R.id.clearButton)
        gridButton = findViewById(R.id.gridButton)
        touchTestView = findViewById(R.id.touchView)

        clearButton.setOnClickListener { touchTestView.clearCanvas() }
        gridButton.setOnClickListener {
            touchTestView.gridEnabled = !touchTestView.gridEnabled
            updateGridButtonLabel()
        }

        touchTestView.statsListener = ::renderStats
        updateGridButtonLabel()
    }

    private fun updateGridButtonLabel() {
        gridButton.setText(if (touchTestView.gridEnabled) R.string.hide_grid else R.string.show_grid)
    }

    private fun renderStats(stats: TouchTestView.TouchStats) {
        summaryView.text = getString(
            R.string.summary_template,
            stats.activePointers,
            stats.finishedStrokes,
            stats.totalEvents
        )
        coverageView.text = getString(
            R.string.coverage_template,
            stats.visitedCells,
            stats.totalCells,
            stats.coveragePercent
        )
        actionView.text = String.format(
            Locale.US,
            "%s  x=%.0f  y=%.0f",
            stats.lastAction,
            stats.lastX,
            stats.lastY
        )
        qualityView.text = getString(
            R.string.quality_template,
            stats.maxJumpDp,
            if (stats.gridEnabled) getString(R.string.grid_on) else getString(R.string.grid_off)
        )
    }
}
