package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.model.QueueInfo;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swtchart.Chart;
import org.eclipse.swtchart.ILineSeries;
import org.eclipse.swtchart.ISeries;
import org.eclipse.swtchart.Range;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DepthChartPanel extends Composite {
    private Chart chart;
    private QueueInfo selectedQueue;
    private List<QueueInfo> allQueues;
    private final Map<String, LinkedList<DataPoint>> queueDataHistory;
    private static final int MAX_DATA_POINTS = 60;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public DepthChartPanel(Composite parent, int style) {
        super(parent, style);
        this.allQueues = new ArrayList<>();
        this.queueDataHistory = new HashMap<>();

        setLayout(new GridLayout());

        Label label = new Label(this, SWT.NONE);
        label.setText("Queue Depth Over Time:");

        chart = new Chart(this, SWT.NONE);
        chart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        chart.getTitle().setText("Queue Depth Monitoring");
        chart.getAxisSet().getXAxis(0).getTitle().setText("Time");
        chart.getAxisSet().getYAxis(0).getTitle().setText("Depth");
        chart.getAxisSet().getXAxis(0).enableCategory(true);

        chart.getLegend().setPosition(SWT.RIGHT);
    }

    public void setQueues(List<QueueInfo> queues) {
        this.allQueues = new ArrayList<>(queues);
        initializeDataHistory();
    }

    public void setSelectedQueue(QueueInfo queue) {
        this.selectedQueue = queue;
        updateChart();
    }

    public void updateData(QueueInfo updatedQueue) {
        if (updatedQueue == null) {
            return;
        }

        LinkedList<DataPoint> history = queueDataHistory.computeIfAbsent(
            updatedQueue.getQueue(), k -> new LinkedList<>());

        history.add(new DataPoint(LocalDateTime.now(), updatedQueue.getCurrentDepth()));

        if (history.size() > MAX_DATA_POINTS) {
            history.removeFirst();
        }

        if (selectedQueue != null && selectedQueue.getQueue().equals(updatedQueue.getQueue())) {
            updateChart();
        }
    }

    private void initializeDataHistory() {
        queueDataHistory.clear();
        for (QueueInfo queue : allQueues) {
            LinkedList<DataPoint> history = new LinkedList<>();
            history.add(new DataPoint(LocalDateTime.now(), queue.getCurrentDepth()));
            queueDataHistory.put(queue.getQueue(), history);
        }
    }

    private void updateChart() {
        if (selectedQueue == null) {
            return;
        }

        LinkedList<DataPoint> history = queueDataHistory.get(selectedQueue.getQueue());
        if (history == null || history.isEmpty()) {
            return;
        }

        for (ISeries series : chart.getSeriesSet().getSeries()) {
            chart.getSeriesSet().deleteSeries(series.getId());
        }

        String[] xLabels = new String[history.size()];
        double[] yValues = new double[history.size()];

        int i = 0;
        for (DataPoint point : history) {
            xLabels[i] = point.timestamp.format(TIME_FORMATTER);
            yValues[i] = point.depth;
            i++;
        }

        chart.getAxisSet().getXAxis(0).setCategorySeries(xLabels);
        ILineSeries lineSeries = (ILineSeries) chart.getSeriesSet().createSeries(
            ISeries.SeriesType.LINE, selectedQueue.getQueue());
        lineSeries.setYSeries(yValues);
        lineSeries.setLineColor(getDisplay().getSystemColor(SWT.COLOR_BLUE));
        lineSeries.setSymbolType(ILineSeries.PlotSymbolType.CIRCLE);
        lineSeries.setSymbolSize(4);

        if (yValues.length > 0) {
            double maxDepth = selectedQueue.getMaxDepth();
            if (maxDepth > 0) {
                chart.getAxisSet().getYAxis(0).setRange(new Range(0, maxDepth * 1.1));
            } else {
                chart.getAxisSet().adjustRange();
            }
        }

        chart.getAxisSet().getXAxis(0).adjustRange();
        chart.redraw();
    }

    public void clearData() {
        queueDataHistory.clear();
        for (ISeries series : chart.getSeriesSet().getSeries()) {
            chart.getSeriesSet().deleteSeries(series.getId());
        }
        chart.redraw();
    }

    private static class DataPoint {
        LocalDateTime timestamp;
        int depth;

        DataPoint(LocalDateTime timestamp, int depth) {
            this.timestamp = timestamp;
            this.depth = depth;
        }
    }
}
