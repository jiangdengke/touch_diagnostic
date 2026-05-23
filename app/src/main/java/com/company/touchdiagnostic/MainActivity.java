package com.company.touchdiagnostic;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView summaryView;
    private TextView coverageView;
    private TextView actionView;
    private TextView qualityView;
    private Button clearButton;
    private Button gridButton;
    private TouchTestView touchTestView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        summaryView = findViewById(R.id.summaryText);
        coverageView = findViewById(R.id.coverageText);
        actionView = findViewById(R.id.actionText);
        qualityView = findViewById(R.id.qualityText);
        clearButton = findViewById(R.id.clearButton);
        gridButton = findViewById(R.id.gridButton);
        touchTestView = findViewById(R.id.touchView);

        clearButton.setOnClickListener(view -> touchTestView.clearCanvas());
        gridButton.setOnClickListener(view -> {
            touchTestView.setGridEnabled(!touchTestView.isGridEnabled());
            updateGridButtonLabel();
        });

        touchTestView.setStatsListener(this::renderStats);
        updateGridButtonLabel();
    }

    private void updateGridButtonLabel() {
        gridButton.setText(touchTestView.isGridEnabled() ? R.string.hide_grid : R.string.show_grid);
    }

    private void renderStats(@NonNull TouchTestView.TouchStats stats) {
        summaryView.setText(getString(
                R.string.summary_template,
                stats.getActivePointers(),
                stats.getFinishedStrokes(),
                stats.getTotalEvents()
        ));
        coverageView.setText(getString(
                R.string.coverage_template,
                stats.getVisitedCells(),
                stats.getTotalCells(),
                stats.getCoveragePercent()
        ));
        actionView.setText(String.format(
                Locale.US,
                "%s  x=%.0f  y=%.0f",
                stats.getLastAction(),
                stats.getLastX(),
                stats.getLastY()
        ));
        qualityView.setText(getString(
                R.string.quality_template,
                stats.getMaxJumpDp(),
                stats.isGridEnabled() ? getString(R.string.grid_on) : getString(R.string.grid_off)
        ));
    }
}

