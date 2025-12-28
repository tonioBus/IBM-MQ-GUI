# IBM MQ Queue Manager GUI

A comprehensive SWT-based GUI application for managing IBM MQ queue managers, queues, and messages.

## Features

- **Connection Management**: Connect to IBM MQ queue managers with configurable connection profiles
- **Queue Browsing**: View all queues with real-time depth monitoring
- **Queue Properties**: Display all queue attributes and properties
- **Message Operations**:
  - Browse messages in queues (non-destructive)
  - Put messages to queues with configurable priority and persistence
- **Alert System**:
  - Configurable warning and critical thresholds for queue depths
  - Visual alerts (color-coded queue list)
  - Sound alerts when thresholds are breached
- **Real-time Monitoring**:
  - Auto-refresh capability with background monitoring
  - Real-time line chart showing queue depth over time
- **Persistent Configuration**: Saves connection profiles and threshold settings

## Prerequisites

- Java 17 or higher
- IBM MQ queue manager (local or remote)
- Maven

## Building the Application

```bash
mvn clean package
```

## Running the Application

```bash
mvn exec:java -Dexec.mainClass="com.aquila.ibm.mq.gui.Main"
```

Or after building:

```bash
java -jar target/IBM-MQ-GUI-1.0-SNAPSHOT.jar
```

## Configuration

### Connection Profiles

Connection profiles are saved in `~/.ibmmqgui/connections.json`. You can manage them through the UI:

1. Click **Connection > Connect**
2. Enter connection details:
   - Profile Name: A friendly name for this connection
   - Host: Queue manager hostname
   - Port: Listener port (default: 1414)
   - Channel: Server connection channel (e.g., DEV.APP.SVRCONN)
   - Queue Manager: Queue manager name
   - Username/Password: Authentication credentials
3. Click **Save Profile** to persist the configuration
4. Click **Test Connection** to verify connectivity
5. Click **Connect** to establish the connection

### Threshold Configuration

Configure queue depth thresholds through **Tools > Configure Thresholds**:

- **Warning Threshold**: Depth percentage/value that triggers a warning alert
- **Critical Threshold**: Depth percentage/value that triggers a critical alert
- **Type**: Percentage (%) or Absolute value

Thresholds are saved in `~/.ibmmqgui/thresholds.json`.

## Usage

### Viewing Queues

1. Connect to a queue manager
2. The queue list will automatically populate on the left panel
3. Queues are color-coded:
   - **Green**: Has messages, within normal thresholds
   - **Yellow**: Warning threshold exceeded
   - **Red**: Critical threshold exceeded
   - **White**: Empty queue

### Browsing Messages

1. Select a queue from the list
2. Click the **Messages** tab
3. Click **Refresh** to browse messages
4. Select a message to view its details

### Sending Messages

1. Select a queue from the list
2. Click the **Send Message** tab
3. Enter message content
4. Configure priority (0-9) and persistence
5. Click **Send**

### Monitoring Queue Depths

1. Enable **View > Auto-refresh** to start background monitoring
2. Switch to the **Depth Chart** tab to see real-time visualization
3. The chart shows the last 60 data points for the selected queue

## Architecture

```
com.aquila.ia.rag/
├── model/           - Data models (ConnectionConfig, QueueInfo, etc.)
├── mq/              - IBM MQ integration layer
├── config/          - Configuration and alert management
├── ui/              - SWT UI components
└── util/            - Utilities (sound player, etc.)
```

## Logging

Logs are written to:
- Console (stdout)
- `logs/ibmmqgui.log` (rolling daily, 30-day retention)

Log level can be adjusted in `src/main/resources/logback.xml`.

## Troubleshooting

### Connection Issues

- Verify queue manager is running and accessible
- Check firewall rules for the MQ port
- Ensure channel has appropriate permissions
- Verify username/password if authentication is enabled

### Platform-Specific Issues

The application uses SWT which is platform-specific. The current `pom.xml` is configured for Windows x86_64. For other platforms, update the SWT dependency:

- **Linux x86_64**: `org.eclipse.swt.gtk.linux.x86_64`
- **macOS x86_64**: `org.eclipse.swt.cocoa.macosx.x86_64`
- **macOS ARM64**: `org.eclipse.swt.cocoa.macosx.aarch64`

## License

This project is provided as-is for educational and demonstration purposes.

## Author

Generated with Claude Code
