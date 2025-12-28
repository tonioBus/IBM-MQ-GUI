package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class QueueListViewer extends Composite {
    private final Table table;
    private final List<QueueInfo> queues;
    private final AlertManager alertManager;
    private Consumer<QueueInfo> selectionListener;
    private Color greenColor;
    private Color yellowColor;
    private Color redColor;

    public QueueListViewer(Composite parent, int style, AlertManager alertManager) {
        super(parent, style);
        this.queues = new ArrayList<>();
        this.alertManager = alertManager;

        setLayout(new GridLayout());

        greenColor = new Color(getDisplay(), 200, 255, 200);
        yellowColor = new Color(getDisplay(), 255, 255, 200);
        redColor = new Color(getDisplay(), 255, 200, 200);

        Label label = new Label(this, SWT.NONE);
        label.setText("Queues:");

        table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        TableColumn nameColumn = new TableColumn(table, SWT.LEFT);
        nameColumn.setText("Queue Name");
        nameColumn.setWidth(250);

        TableColumn depthColumn = new TableColumn(table, SWT.RIGHT);
        depthColumn.setText("Depth");
        depthColumn.setWidth(80);

        TableColumn maxDepthColumn = new TableColumn(table, SWT.RIGHT);
        maxDepthColumn.setText("Max Depth");
        maxDepthColumn.setWidth(80);

        TableColumn percentColumn = new TableColumn(table, SWT.RIGHT);
        percentColumn.setText("% Full");
        percentColumn.setWidth(70);

        table.addListener(SWT.Selection, e -> {
            int index = table.getSelectionIndex();
            if (index >= 0 && index < queues.size() && selectionListener != null) {
                selectionListener.accept(queues.get(index));
            }
        });

        addDisposeListener(e -> {
            greenColor.dispose();
            yellowColor.dispose();
            redColor.dispose();
        });
    }

    public void setQueues(List<QueueInfo> queues) {
        this.queues.clear();
        this.queues.addAll(queues);
        refresh();
    }

    public void refresh() {
        table.removeAll();

        for (QueueInfo queue : queues) {
            TableItem item = new TableItem(table, SWT.NONE);
            updateTableItem(item, queue);
        }

        if (!queues.isEmpty() && table.getSelectionIndex() < 0) {
            table.select(0);
            if (selectionListener != null) {
                selectionListener.accept(queues.get(0));
            }
        }
    }

    private void updateTableItem(TableItem item, QueueInfo queue) {
        item.setText(0, queue.getName());
        item.setText(1, String.valueOf(queue.getCurrentDepth()));
        item.setText(2, String.valueOf(queue.getMaxDepth()));
        item.setText(3, String.format("%.1f%%", queue.getDepthPercentage()));

        ThresholdConfig.AlertLevel alertLevel = alertManager.getCurrentAlertLevel(queue.getName());

        switch (alertLevel) {
            case CRITICAL:
                item.setBackground(redColor);
                break;
            case WARNING:
                item.setBackground(yellowColor);
                break;
            default:
                if (queue.getCurrentDepth() == 0) {
                    item.setBackground(null);
                } else {
                    item.setBackground(greenColor);
                }
                break;
        }
    }

    public void clearQueues() {
        queues.clear();
        table.removeAll();
    }

    public List<QueueInfo> getQueues() {
        return new ArrayList<>(queues);
    }

    public void addSelectionListener(Consumer<QueueInfo> listener) {
        this.selectionListener = listener;
    }

    public QueueInfo getSelectedQueue() {
        int index = table.getSelectionIndex();
        if (index >= 0 && index < queues.size()) {
            return queues.get(index);
        }
        return null;
    }
}
