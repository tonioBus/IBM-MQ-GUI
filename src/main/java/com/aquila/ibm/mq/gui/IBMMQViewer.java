/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Main entry point for the IBM MQ GUI application.
 * Initializes the SWT display and launches the main window.
 */
package com.aquila.ibm.mq.gui;

import com.aquila.ibm.mq.gui.ui.MainWindow;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IBMMQViewer {
    private static final Logger logger = LoggerFactory.getLogger(IBMMQViewer.class);

    public static void main(String[] args) {
        logger.info("Starting IBM MQ GUI Application");

        try {
            Display display = new Display();
            MainWindow mainWindow = new MainWindow(display);
            mainWindow.open();
            display.dispose();
        } catch (Exception e) {
            logger.error("Fatal error in application", e);
            e.printStackTrace();
        }

        logger.info("IBM MQ GUI Application terminated");
    }
}