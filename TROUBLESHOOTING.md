# Troubleshooting Guide - IBM MQ GUI

## Error 2009: MQRC_CONNECTION_BROKEN

This is the most common connection error. It means the connection to the queue manager failed or was broken.

### Quick Fixes

#### 1. Verify Queue Manager is Running

```bash
# Check queue manager status
dspmq

# Start queue manager if stopped
strmqm QM1
```

#### 2. Check Listener is Running

```bash
# Connect to queue manager
runmqsc QM1

# Display listener status
DISPLAY LISTENER(*)

# If not running, start it
START LISTENER(SYSTEM.DEFAULT.LISTENER.TCP)

# Verify it's listening on the correct port
DISPLAY LISTENER(SYSTEM.DEFAULT.LISTENER.TCP)
# Should show PORT(1414)

# Exit runmqsc
end
```

#### 3. Verify Channel Configuration

```bash
runmqsc QM1

# Display your channel (replace with your channel name)
DISPLAY CHANNEL(DEV.APP.SVRCONN)

# Should show:
# - CHLTYPE(SVRCONN)
# - STATUS(RUNNING) or STATUS(INACTIVE) is OK for SVRCONN

# If channel doesn't exist, create it:
DEFINE CHANNEL(DEV.APP.SVRCONN) CHLTYPE(SVRCONN)

end
```

#### 4. Check Channel Authentication (CHLAUTH)

CHLAUTH rules might be blocking your connection:

```bash
runmqsc QM1

# Display current CHLAUTH rules
DISPLAY CHLAUTH(*)

# For development, you can disable CHLAUTH (NOT for production!)
ALTER QMGR CHLAUTH(DISABLED)

# Or allow specific channel
SET CHLAUTH(DEV.APP.SVRCONN) TYPE(BLOCKUSER) USERLIST('nobody')
SET CHLAUTH('*') TYPE(ADDRESSMAP) ADDRESS('*') USERSRC(NOACCESS) DESCR('Block all by default')
SET CHLAUTH(DEV.APP.SVRCONN) TYPE(ADDRESSMAP) ADDRESS('*') USERSRC(CHANNEL) CHCKCLNT(REQUIRED)

# Refresh security
REFRESH SECURITY TYPE(CONNAUTH)

end
```

#### 5. Test Network Connectivity

```bash
# Ping the host
ping localhost
ping your-mq-host

# Test port connectivity (Windows)
Test-NetConnection -ComputerName localhost -Port 1414

# Test port connectivity (Linux/Mac)
telnet localhost 1414
# or
nc -zv localhost 1414
```

### Common Scenarios

#### Scenario 1: Docker MQ Container

If using IBM MQ Docker container:

```yaml
# Connection settings that usually work:
Host: localhost
Port: 1414
Channel: DEV.APP.SVRCONN
Queue Manager: QM1
Username: app
Password: <leave empty or use 'passw0rd' for dev container>
```

#### Scenario 2: Local Windows Installation

```yaml
Host: localhost
Port: 1414
Channel: SYSTEM.DEF.SVRCONN
Queue Manager: <your QM name>
Username: <Windows username or leave empty>
Password: <leave empty or Windows password>
```

#### Scenario 3: Remote Queue Manager

```yaml
Host: <IP or hostname>
Port: 1414
Channel: <channel name - ask your MQ admin>
Queue Manager: <queue manager name>
Username: <required - ask your MQ admin>
Password: <required>
```

### Setting Up a Test Queue Manager

If you need a quick test environment:

#### Using Docker (Recommended)

```bash
docker run --name mqtest -p 1414:1414 -p 9443:9443 \
  -e LICENSE=accept \
  -e MQ_QMGR_NAME=QM1 \
  -e MQ_APP_PASSWORD=passw0rd \
  ibmcom/mq:latest
```

Then connect with:
- Host: localhost
- Port: 1414
- Channel: DEV.APP.SVRCONN
- Queue Manager: QM1
- Username: app
- Password: passw0rd

#### Manual Setup (Windows/Linux)

```bash
# Create queue manager
crtmqm QM1

# Start queue manager
strmqm QM1

# Configure queue manager
runmqsc QM1

# Create listener
DEFINE LISTENER(SYSTEM.DEFAULT.LISTENER.TCP) TRPTYPE(TCP) PORT(1414) CONTROL(QMGR)
START LISTENER(SYSTEM.DEFAULT.LISTENER.TCP)

# Create server connection channel
DEFINE CHANNEL(DEV.APP.SVRCONN) CHLTYPE(SVRCONN)

# Create test queue
DEFINE QLOCAL(TEST.QUEUE) MAXDEPTH(5000)

# Disable CHLAUTH for testing (dev only!)
ALTER QMGR CHLAUTH(DISABLED)

# Or configure CHLAUTH properly
SET CHLAUTH(DEV.APP.SVRCONN) TYPE(ADDRESSMAP) ADDRESS('*') USERSRC(CHANNEL)

# Refresh security
REFRESH SECURITY TYPE(CONNAUTH)

end
```

## Other Common Errors

### Error 2059: MQRC_Q_MGR_NOT_AVAILABLE

- Queue manager not running: `strmqm <QM_NAME>`
- Wrong queue manager name in your connection config
- Listener not started

### Error 2538: MQRC_HOST_NOT_AVAILABLE

- Wrong hostname/IP address
- Network connectivity issues
- Firewall blocking connection
- DNS resolution failure - try IP address instead

### Error 2035: MQRC_NOT_AUTHORIZED

- Invalid username/password
- User doesn't have permission to connect
- CONNAUTH configuration issue
- Channel authentication blocking connection

### Error 2540: MQRC_CHANNEL_NOT_AVAILABLE

- Channel doesn't exist
- Channel name misspelled
- Channel type is not SVRCONN

## Viewing Logs

### Queue Manager Error Logs

**Windows:**
```
C:\ProgramData\IBM\MQ\qmgrs\<QM_NAME>\errors\AMQERR01.LOG
```

**Linux:**
```
/var/mqm/qmgrs/<QM_NAME>/errors/AMQERR01.LOG
```

### Application Logs

The IBM MQ GUI application logs are located in:
```
./logs/ibmmqgui.log
```

## Getting More Help

1. Check the queue manager error logs (AMQERR01.LOG)
2. Enable MQ client tracing if needed
3. Use MQ Explorer to verify channel and listener status
4. Check IBM MQ documentation for your specific version
5. Review the application log file for detailed connection attempts

## Quick Checklist

- [ ] Queue manager is running (`dspmq`)
- [ ] Listener is started and listening on correct port
- [ ] Channel exists and is type SVRCONN
- [ ] Network connectivity is working (ping/telnet)
- [ ] Firewall allows connection on MQ port
- [ ] CHLAUTH rules allow your connection
- [ ] Credentials are correct (if required)
- [ ] Queue manager name matches exactly
