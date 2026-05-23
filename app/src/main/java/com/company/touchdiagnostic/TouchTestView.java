package com.company.touchdiagnostic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

public class TouchTestView extends View {

    public interface StatsListener {
        void onStatsChanged(@NonNull TouchStats stats);
    }

    public static final class TouchStats {
        private final int activePointers;
        private final long totalEvents;
        private final long finishedStrokes;
        private final int visitedCells;
        private final int totalCells;
        private final float lastX;
        private final float lastY;
        private final String lastAction;
        private final float maxJumpDp;
        private final boolean gridEnabled;

        TouchStats(
                int activePointers,
                long totalEvents,
                long finishedStrokes,
                int visitedCells,
                int totalCells,
                float lastX,
                float lastY,
                @NonNull String lastAction,
                float maxJumpDp,
                boolean gridEnabled
        ) {
            this.activePointers = activePointers;
            this.totalEvents = totalEvents;
            this.finishedStrokes = finishedStrokes;
            this.visitedCells = visitedCells;
            this.totalCells = totalCells;
            this.lastX = lastX;
            this.lastY = lastY;
            this.lastAction = lastAction;
            this.maxJumpDp = maxJumpDp;
            this.gridEnabled = gridEnabled;
        }

        public int getActivePointers() {
            return activePointers;
        }

        public long getTotalEvents() {
            return totalEvents;
        }

        public long getFinishedStrokes() {
            return finishedStrokes;
        }

        public int getVisitedCells() {
            return visitedCells;
        }

        public int getTotalCells() {
            return totalCells;
        }

        public int getCoveragePercent() {
            if (totalCells == 0) {
                return 0;
            }
            return Math.round((visitedCells * 100f) / totalCells);
        }

        public float getLastX() {
            return lastX;
        }

        public float getLastY() {
            return lastY;
        }

        @NonNull
        public String getLastAction() {
            return lastAction;
        }

        public float getMaxJumpDp() {
            return maxJumpDp;
        }

        public boolean isGridEnabled() {
            return gridEnabled;
        }
    }

    private static final int MAX_STORED_STROKES = 96;
    private static final int BACKGROUND_COLOR = Color.parseColor("#F4F7FB");
    private static final int GRID_COLOR = Color.parseColor("#B7C2D0");
    private static final int VISITED_COLOR = Color.parseColor("#D9E8FF");
    private static final int BORDER_COLOR = Color.parseColor("#2B4C7E");
    private static final int[] POINTER_COLORS = {
            Color.parseColor("#E63946"),
            Color.parseColor("#FF8C42"),
            Color.parseColor("#1D3557"),
            Color.parseColor("#2A9D8F"),
            Color.parseColor("#8338EC"),
            Color.parseColor("#FF006E")
    };

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint visitedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint helperPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseArray<Stroke> activeStrokes = new SparseArray<>();
    private final ArrayList<Stroke> finishedStrokes = new ArrayList<>();

    private boolean[] visitedCells = new boolean[0];
    private int columns;
    private int rows;
    private int cellSizePx;
    private int visitedCount;
    private long totalEvents;
    private long strokeCount;
    private float lastX = -1f;
    private float lastY = -1f;
    private float maxJumpPx;
    private String lastAction = "IDLE";
    private boolean gridEnabled = true;
    private StatsListener statsListener;

    public TouchTestView(Context context) {
        this(context, null);
    }

    public TouchTestView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TouchTestView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setFocusable(true);
        setClickable(true);

        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dpToPx(1f));

        visitedPaint.setColor(VISITED_COLOR);
        visitedPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(BORDER_COLOR);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(3f));

        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeCap(Paint.Cap.ROUND);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);
        pathPaint.setStrokeWidth(dpToPx(5f));

        pointPaint.setStyle(Paint.Style.FILL);

        helperPaint.setColor(BORDER_COLOR);
        helperPaint.setStyle(Paint.Style.STROKE);
        helperPaint.setStrokeWidth(dpToPx(1.5f));
        helperPaint.setAlpha(90);
    }

    public void setStatsListener(@Nullable StatsListener statsListener) {
        this.statsListener = statsListener;
        post(this::notifyStatsChanged);
    }

    public void clearCanvas() {
        activeStrokes.clear();
        finishedStrokes.clear();
        visitedCount = 0;
        for (int index = 0; index < visitedCells.length; index++) {
            visitedCells[index] = false;
        }
        strokeCount = 0L;
        totalEvents = 0L;
        maxJumpPx = 0f;
        lastX = -1f;
        lastY = -1f;
        lastAction = "CLEARED";
        invalidate();
        notifyStatsChanged();
    }

    public boolean isGridEnabled() {
        return gridEnabled;
    }

    public void setGridEnabled(boolean gridEnabled) {
        this.gridEnabled = gridEnabled;
        invalidate();
        notifyStatsChanged();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        cellSizePx = Math.max(dpToPx(72f), 1);
        columns = (int) Math.ceil(w / (float) cellSizePx);
        rows = (int) Math.ceil(h / (float) cellSizePx);
        visitedCells = new boolean[columns * rows];
        clearCanvas();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BACKGROUND_COLOR);
        drawCoverage(canvas);
        drawGrid(canvas);
        drawFinishedStrokes(canvas);
        drawActiveStrokes(canvas);
        drawFrame(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        totalEvents++;

        int actionMasked = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        lastAction = actionToLabel(actionMasked);
        lastX = event.getX(actionIndex);
        lastY = event.getY(actionIndex);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                startStroke(
                        event.getPointerId(actionIndex),
                        event.getX(actionIndex),
                        event.getY(actionIndex)
                );
                break;
            case MotionEvent.ACTION_MOVE:
                for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
                    int pointerId = event.getPointerId(pointerIndex);
                    Stroke stroke = activeStrokes.get(pointerId);
                    if (stroke == null) {
                        stroke = startStroke(pointerId, event.getX(pointerIndex), event.getY(pointerIndex));
                    }
                    appendHistoricalPoints(stroke, event, pointerIndex);
                    appendPoint(stroke, event.getX(pointerIndex), event.getY(pointerIndex));
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                finishStroke(
                        event.getPointerId(actionIndex),
                        event.getX(actionIndex),
                        event.getY(actionIndex)
                );
                break;
            case MotionEvent.ACTION_CANCEL:
                activeStrokes.clear();
                lastAction = "CANCEL";
                break;
            default:
                break;
        }

        invalidate();
        notifyStatsChanged();
        return true;
    }

    private void drawCoverage(@NonNull Canvas canvas) {
        if (!gridEnabled || visitedCells.length == 0) {
            return;
        }
        for (int index = 0; index < visitedCells.length; index++) {
            if (!visitedCells[index]) {
                continue;
            }
            int column = index % columns;
            int row = index / columns;
            float left = column * cellSizePx;
            float top = row * cellSizePx;
            canvas.drawRect(left, top, left + cellSizePx, top + cellSizePx, visitedPaint);
        }
    }

    private void drawGrid(@NonNull Canvas canvas) {
        if (!gridEnabled || columns == 0 || rows == 0) {
            return;
        }
        for (int column = 0; column <= columns; column++) {
            float x = Math.min(column * cellSizePx, getWidth());
            canvas.drawLine(x, 0f, x, getHeight(), gridPaint);
        }
        for (int row = 0; row <= rows; row++) {
            float y = Math.min(row * cellSizePx, getHeight());
            canvas.drawLine(0f, y, getWidth(), y, gridPaint);
        }
    }

    private void drawFinishedStrokes(@NonNull Canvas canvas) {
        for (Stroke stroke : finishedStrokes) {
            pathPaint.setColor(stroke.color);
            pathPaint.setAlpha(170);
            canvas.drawPath(stroke.path, pathPaint);
        }
    }

    private void drawActiveStrokes(@NonNull Canvas canvas) {
        for (int index = 0; index < activeStrokes.size(); index++) {
            Stroke stroke = activeStrokes.valueAt(index);
            pathPaint.setColor(stroke.color);
            pathPaint.setAlpha(255);
            canvas.drawPath(stroke.path, pathPaint);

            pointPaint.setColor(stroke.color);
            canvas.drawCircle(stroke.lastX, stroke.lastY, dpToPx(10f), pointPaint);
            canvas.drawCircle(stroke.lastX, stroke.lastY, dpToPx(18f), helperPaint);
        }
    }

    private void drawFrame(@NonNull Canvas canvas) {
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), borderPaint);
    }

    @NonNull
    private Stroke startStroke(int pointerId, float x, float y) {
        Stroke stroke = new Stroke(colorForPointer(pointerId));
        stroke.path.moveTo(x, y);
        stroke.lastX = x;
        stroke.lastY = y;
        activeStrokes.put(pointerId, stroke);
        strokeCount++;
        markVisited(x, y);
        return stroke;
    }

    private void finishStroke(int pointerId, float x, float y) {
        Stroke stroke = activeStrokes.get(pointerId);
        if (stroke == null) {
            stroke = startStroke(pointerId, x, y);
        }
        appendPoint(stroke, x, y);
        activeStrokes.remove(pointerId);
        finishedStrokes.add(stroke);
        if (finishedStrokes.size() > MAX_STORED_STROKES) {
            finishedStrokes.remove(0);
        }
    }

    private void appendHistoricalPoints(@NonNull Stroke stroke, @NonNull MotionEvent event, int pointerIndex) {
        for (int historyIndex = 0; historyIndex < event.getHistorySize(); historyIndex++) {
            appendPoint(
                    stroke,
                    event.getHistoricalX(pointerIndex, historyIndex),
                    event.getHistoricalY(pointerIndex, historyIndex)
            );
        }
    }

    private void appendPoint(@NonNull Stroke stroke, float x, float y) {
        float dx = x - stroke.lastX;
        float dy = y - stroke.lastY;
        float jumpPx = (float) Math.hypot(dx, dy);
        if (jumpPx > maxJumpPx) {
            maxJumpPx = jumpPx;
        }
        stroke.path.lineTo(x, y);
        stroke.lastX = x;
        stroke.lastY = y;
        markVisited(x, y);
    }

    private void markVisited(float x, float y) {
        if (columns == 0 || rows == 0) {
            return;
        }
        int column = clamp((int) (x / cellSizePx), 0, columns - 1);
        int row = clamp((int) (y / cellSizePx), 0, rows - 1);
        int index = row * columns + column;
        if (!visitedCells[index]) {
            visitedCells[index] = true;
            visitedCount++;
        }
    }

    private void notifyStatsChanged() {
        if (statsListener == null) {
            return;
        }
        statsListener.onStatsChanged(new TouchStats(
                activeStrokes.size(),
                totalEvents,
                strokeCount,
                visitedCount,
                visitedCells.length,
                lastX,
                lastY,
                lastAction,
                pxToDp(maxJumpPx),
                gridEnabled
        ));
    }

    private int colorForPointer(int pointerId) {
        return POINTER_COLORS[Math.abs(pointerId) % POINTER_COLORS.length];
    }

    @NonNull
    private String actionToLabel(int actionMasked) {
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_POINTER_DOWN:
                return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP:
                return "POINTER_UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            default:
                return String.format(Locale.US, "ACTION_%d", actionMasked);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private float pxToDp(float px) {
        return px / getResources().getDisplayMetrics().density;
    }

    private static final class Stroke {
        private final Path path = new Path();
        private final int color;
        private float lastX;
        private float lastY;

        private Stroke(int color) {
            this.color = color;
        }
    }
}
