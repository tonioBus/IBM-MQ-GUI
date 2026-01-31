# IBM MQ GUI

A comprehensive desktop application for managing IBM Message Queue (MQ) queue managers, queues, and messages. Built with Java and Eclipse SWT for a native, responsive user interface.

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage Guide](#usage-guide)
- [Troubleshooting](#troubleshooting)
- [Project Structure](#project-structure)

---

## Features

### Connection Management

- **Multiple Simultaneous Connections**: Manage multiple queue manager connections concurrently
- **Connection Profiles**: Save and reuse connection configurations
- **Connection Testing**: Validate connectivity before saving a profile
- **SSL/TLS Support**: Secure connections to queue managers
- **MQCSP Authentication**: Modern credential-based authentication support
- **Auto-disconnect**: Graceful cleanup on application shutdown

### Queue Management

- **Queue Browsing**: View all queues on connected queue managers with filtering support
- **Queue Types**: Support for Local, Remote, Alias, Model, and Cluster queues
- **Queue Properties Display**:
  - Current and maximum depth
  - Open input/output counts
  - Description and creation date/time
  - Inhibit put/get flags
  - Default priority and persistence
  - Base queue name (for alias queues)
- **Real-time Depth Monitoring**: Background thread continuously updates queue depths
- **Queue Handles**: View all processes/applications currently using a queue:
  - Process ID and Thread ID
  - User ID and channel name
  - Open options (Input/Output/Browse/Inquire)
  - Connection IDs

### Message Operations

- **Message Browsing**: Non-destructive queue message inspection
  - Browse up to 1000 messages per queue
  - Display message metadata: ID, correlation ID, priority, persistence, encoding
  - View message content with size information
  - Detailed message properties panel
- **Message Sending**:
  - Send single or batch messages to queues
  - Configure priority (0-9) and persistence
  - Batch sending with configurable delay between messages
- **Message Templates**:
  - Save reusable message patterns
  - Template management (create, edit, delete)
  - Quick message composition from templates

### Alert System

- **Threshold Configuration**: Per-queue warning and critical thresholds
  - Percentage-based thresholds (relative to max depth)
  - Absolute value thresholds
- **Visual Alerts**: Color-coded queue list
  - Green: Has messages, within normal thresholds
  - Yellow: Warning threshold exceeded
  - Red: Critical threshold exceeded
  - White: Empty queue
- **Sound Alerts**:
  - Audio notifications for threshold breaches
  - 600Hz tone for warnings, 800Hz for critical
  - Enableable/disableable per preference
- **Alert History**: Track alert transitions over time

### Real-time Monitoring

- **Background Monitoring Thread**: Continuous queue depth updates
  - Configurable refresh interval (1-300 seconds)
  - Pause/resume functionality
- **Auto-refresh Toggle**: Enable/disable monitoring from the UI
- **Depth Charts**: Visual queue depth trends over the last 60 data points

### Import/Export Configuration

- **Full Configuration Export**: Export entire hierarchy and connection profiles
- **Selective Export**: Export specific queue manager or folder hierarchies
- **JSON Format**: Human-readable configuration files
- **Batch Import**: Import multiple queue managers and hierarchies at once

### Hierarchical Organization

- **Folder Structure**: Organize queue managers in logical folder hierarchies
- **Tree Navigation**: Browse connections in an intuitive tree view
- **Persistent Organization**: Hierarchy automatically saved and restored

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17+ |
| GUI Framework | Eclipse SWT (Standard Widget Toolkit) |
| Charts | JFreeChart / SWT Chart |
| IBM MQ Client | com.ibm.mq.allclient 9.4.4.1 |
| Serialization | Jackson, Gson |
| Logging | SLF4J with Logback |
| Build Tool | Maven |
| Native Compilation | GraalVM (optional) |

---

## Prerequisites

- **Java**: JDK 17 or higher
- **Maven**: 3.6+
- **IBM MQ**: Access to an IBM MQ queue manager (local or remote)
- **Operating System**: Windows x86_64, Linux x86_64, or macOS

---

## Installation

### Building from Source

```bash
# Clone the repository
git clone https://github.com/tonioBus/IBM-MQ-GUI.git
cd IBM-MQ-GUI

# Build the application
mvn clean package

# Build native image (optional, requires GraalVM)
mvn clean package -Pnative
```

### Running the Application

```bash
# Using Maven
mvn exec:java -Dexec.mainClass="com.aquila.ibm.mq.gui.IBMMQViewer"

# Using JAR
java -jar target/IBM-MQ-GUI-1.0-SNAPSHOT.jar

# Native executable (if built with native profile)
./IBMMQViewer
```

### Platform-Specific SWT Configuration

The default configuration targets Windows x86_64. For other platforms, update the SWT dependency in `pom.xml`:

| Platform | Artifact ID |
|----------|-------------|
| Windows x86_64 | `org.eclipse.swt.win32.win32.x86_64` |
| Linux x86_64 | `org.eclipse.swt.gtk.linux.x86_64` |
| macOS x86_64 | `org.eclipse.swt.cocoa.macosx.x86_64` |
| macOS ARM64 | `org.eclipse.swt.cocoa.macosx.aarch64` |

---

## Configuration

### Configuration Directory

All configuration files are stored in `~/.ibmmqgui/`:

| File | Description |
|------|-------------|
| `connections.json` | Queue manager connection profiles |
| `thresholds.json` | Queue alert thresholds |
| `hierarchy.json` | Folder and queue manager organization |
| `hierarchy.json.bak` | Automatic backup of hierarchy |
| `message_templates.json` | Saved message templates |

### Connection Properties

| Property | Description | Default |
|----------|-------------|---------|
| `host` | Queue manager hostname or IP | - |
| `port` | Listener port | 1414 |
| `channel` | Server connection channel | - |
| `queueManager` | Queue manager name | - |
| `username` | Authentication username | - |
| `password` | Authentication password | - |
| `sslEnabled` | Enable SSL/TLS | false |

### Threshold Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `warningThreshold` | Warning level value | 70 |
| `criticalThreshold` | Critical level value | 90 |
| `warningThresholdPercentage` | Use percentage for warning | true |
| `criticalThresholdPercentage` | Use percentage for critical | true |
| `enabled` | Enable threshold alerts | true |

### Logging Configuration

Logs are written to `logs/ibmmqgui.log` with daily rotation and 30-day retention.

To adjust log levels, modify `src/main/resources/logback.xml`:

```xml
<root level="INFO">
    <!-- Change to DEBUG, WARN, or ERROR as needed -->
</root>
```

---

## Usage Guide

### Connecting to a Queue Manager

1. Click **File > New Connection** or use the toolbar button
2. Enter connection details:
   - Queue Manager Name
   - Host address
   - Port (default: 1414)
   - Channel name
   - Credentials (if required)
3. Click **Test Connection** to validate
4. Click **Save** to store the profile

### Browsing Queues

1. Select a connected queue manager from the tree view
2. Queues are displayed in the main panel with depth information
3. Click on a queue to view its properties
4. Double-click to browse messages
5. Queues are color-coded based on depth thresholds:
   - **Green**: Has messages, within normal thresholds
   - **Yellow**: Warning threshold exceeded
   - **Red**: Critical threshold exceeded
   - **White**: Empty queue

### Browsing Messages

1. Select a queue and open the message browser
2. Messages are displayed non-destructively (browse mode)
3. Click a message to view its full content and properties
4. Message details include:
   - Message ID and Correlation ID
   - Priority and Persistence
   - Put date/time
   - Message format and encoding
   - Full message body
5. Use the refresh button to update the message list

### Sending Messages

1. Right-click on a queue and select **Send Message**
2. Enter or paste message content
3. Configure options:
   - Priority (0-9)
   - Persistence (persistent/non-persistent)
   - Batch count for multiple messages
   - Delay between batch messages
4. Optionally save as template for reuse
5. Click **Send**

### Using Message Templates

1. In the Send Message dialog, compose your message
2. Click **Save as Template** and provide a name
3. To reuse, select the template from the dropdown
4. Templates are stored in `~/.ibmmqgui/message_templates.json`

### Configuring Alerts

1. Right-click on a queue and select **Configure Thresholds**
2. Set warning threshold (default: 70%)
3. Set critical threshold (default: 90%)
4. Choose between percentage or absolute values
5. Enable/disable sound alerts
6. Click **Save**

### Monitoring Queue Depths

1. Enable **View > Auto-refresh** to start background monitoring
2. Configure refresh interval in preferences
3. Switch to the **Depth Chart** tab to see real-time visualization
4. The chart shows the last 60 data points for the selected queue

### Viewing Queue Handles

1. Select a queue from the list
2. Open the **Handles** tab
3. View all applications/processes using the queue:
   - Application name
   - Process ID and Thread ID
   - User ID
   - Connection type (Input/Output/Browse)

### Import/Export Configuration

**Exporting:**
1. Right-click on a folder or queue manager in the tree
2. Select **Export Configuration**
3. Choose save location
4. Configuration is saved as JSON

**Importing:**
1. Click **File > Import Configuration**
2. Select the JSON configuration file
3. Review imported items
4. Confirm import

### Import Configuration Format

```json
{
  "label": "Production Environment",
  "queueManagers": {
    "QM1_ID": {
      "name": "QM1",
      "host": "192.168.1.100",
      "port": 1414,
      "channel": "APP.SVRCONN",
      "queueManager": "QM1",
      "username": "mquser",
      "password": "password",
      "sslEnabled": false
    }
  },
  "hierarchy": {
    "Production": {
      "type": "folder",
      "children": {
        "Queue Browser": {
          "type": "queue",
          "queueMgr": "QM1_ID",
          "children": {
            "APP.REQUEST.QUEUE": "Request Queue",
            "APP.RESPONSE.QUEUE": "Response Queue"
          }
        }
      }
    }
  }
}
```

---

## Troubleshooting

### Common Error Codes

| Error Code | Description | Solution |
|------------|-------------|----------|
| MQRC 2009 | Connection broken | Check network connectivity and queue manager status |
| MQRC 2035 | Not authorized | Verify username/password and user permissions |
| MQRC 2059 | Queue manager not available | Ensure queue manager is running |
| MQRC 2393 | Authentication error | Check MQCSP credentials configuration |
| MQRC 2538 | Host not available | Verify hostname and network connectivity |
| MQRC 2540 | Channel not available | Check channel name and listener status |

### Connection Issues

1. **Verify queue manager is running**:
   ```bash
   dspmq -m <QMGR_NAME>
   ```

2. **Check listener status**:
   ```bash
   echo "display lsstatus(*)" | runmqsc <QMGR_NAME>
   ```

3. **Verify channel configuration**:
   ```bash
   echo "display channel(<CHANNEL_NAME>)" | runmqsc <QMGR_NAME>
   ```

4. **Check authentication settings**:
   ```bash
   echo "display authinfo(*)" | runmqsc <QMGR_NAME>
   ```

### Platform-Specific Issues

The application uses SWT which is platform-specific. Ensure the correct SWT dependency is configured in `pom.xml` for your operating system.

### Log Files

Application logs are located at:
- `logs/ibmmqgui.log` - Main application log
- Console output when running with Maven

For detailed troubleshooting information, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

---

## Project Structure

```
IBM-MQ-GUI/
├── pom.xml                     # Maven configuration
├── README.md                   # This file
├── TROUBLESHOOTING.md          # Detailed troubleshooting guide
├── doc/                        # Documentation and examples
│   ├── import.json             # Example import configuration
│   └── setup.txt               # MQ setup instructions
├── src/main/java/              # Java source code
│   └── com/aquila/ibm/mq/gui/
│       ├── IBMMQViewer.java    # Main entry point
│       ├── config/             # Configuration management
│       │   ├── Configuration.java
│       │   └── AlertManager.java
│       ├── model/              # Data models
│       │   ├── QueueManagerConfig.java
│       │   ├── QueueInfo.java
│       │   ├── MessageInfo.java
│       │   ├── ThresholdConfig.java
│       │   └── MessageTemplate.java
│       ├── mq/                 # IBM MQ integration
│       │   ├── MQConnectionManager.java
│       │   ├── QueueService.java
│       │   ├── MessageService.java
│       │   └── QueueMonitor.java
│       ├── ui/                 # SWT UI components
│       │   ├── MainWindow.java
│       │   ├── MessageBrowserPanel.java
│       │   ├── QueuePropertiesPanel.java
│       │   ├── DepthChartPanel.java
│       │   └── various dialogs
│       ├── util/               # Utility classes
│       │   ├── ImportExportUtil.java
│       │   ├── SoundPlayer.java
│       │   └── TemplateProcessor.java
│       └── importation/        # Import framework
├── src/main/resources/         # Resources
│   ├── logback.xml             # Logging configuration
│   └── icons/                  # Application icons
├── src/test/                   # Unit tests
└── logs/                       # Application logs
```

---

## License

This project is provided as-is for IBM MQ browsing purposes.

---

## Contributing

Contributions are welcome. Please submit issues and pull requests to the [GitHub repository](https://github.com/tonioBus/IBM-MQ-GUI).

---

## Author

**Anthony Bussani**

[GitHub](https://github.com/tonioBus)
