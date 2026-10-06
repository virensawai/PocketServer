# PocketServer V1

## Turn Your Android Phone Into a Public Web Server

**Product Type:** Android Developer Tool
**Version:** V1.0 MVP
**Target Users:** Developers, students, hackathon participants
**Primary Platform:** Android
**Status:** Product Definition

---

# 1. Product Overview

PocketServer is an Android application that allows developers and students to turn an Android smartphone into a publicly accessible web server.

Users select or upload a static website project on their Android device. PocketServer runs a lightweight HTTP server locally on the phone and establishes a secure outbound tunnel to a cloud relay server.

The relay server exposes the project to the public Internet through a generated URL.

### Example

A user deploys:

```text
my-portfolio/
├── index.html
├── style.css
├── script.js
└── assets/
```

PocketServer starts the local server and provides:

```text
https://<project-id>.<temporary-domain>
```

Visitors can access the website even when the Android device is behind mobile-network CGNAT.

---

# 2. Problem Statement

Developers frequently need to demonstrate or temporarily host projects without deploying them to traditional cloud platforms.

Existing solutions often require:

* VPS hosting
* Vercel/Netlify/Cloudflare deployment
* Git repositories
* domain configuration
* cloud credentials
* configuration of servers

For students and developers working from a phone, these workflows can be unnecessarily complicated.

PocketServer aims to provide:

> **Select project → Deploy → Get public URL**

The phone becomes the compute/server device while a lightweight cloud relay provides Internet connectivity.

---

# 3. Product Vision

### V1 Vision

> Make an Android phone capable of publicly serving static websites with almost zero configuration.

The experience should feel similar to:

```text
Vercel:
Import → Deploy → URL
```

but:

```text
PocketServer:
Select project → Deploy → URL
```

with the actual website files remaining on the user's Android device.

---

# 4. Target Users

## Primary Users

### Students

Use cases:

* College project demonstrations
* Hackathons
* Portfolio demonstrations
* Classroom presentations
* Sharing assignments
* Temporary project hosting

### Developers

Use cases:

* Testing websites from another network
* Temporary demos
* Client previews
* Development experiments
* Sharing prototypes
* Hosting static documentation

---

# 5. V1 Goals

PocketServer V1 must allow a user to:

1. Install the Android application.
2. Select a static website directory.
3. Start a local web server.
4. Establish a secure tunnel to the PocketServer relay.
5. Receive a public URL.
6. Open that URL from another device/network.
7. Serve website files from the Android device.
8. Stop the deployment at any time.
9. Monitor basic deployment information.

---

# 6. Non-Goals for V1

The following features are intentionally excluded from V1.

### Backend runtimes

```text
Node.js
Python
PHP
Java
Ruby
```

### Databases

```text
PostgreSQL
MySQL
MongoDB
Redis
```

### Containers

```text
Docker
Linux containers
```

### Advanced networking

```text
Custom domains
Static public IP
Port forwarding
```

### Advanced deployment

```text
GitHub integration
CI/CD
Automatic builds
Server-side compilation
```

### Infrastructure

PocketServer V1 will NOT attempt to turn Android into a general-purpose VPS.

The first version focuses exclusively on:

> **Public static website hosting from Android.**

---

# 7. Core User Journey

## First Launch

```text
Install App
     ↓
Open PocketServer
     ↓
Create / Sign in to account
     ↓
Dashboard
```

---

# 8. Deployment Flow

```text
Dashboard
    ↓
[ + New Deployment ]
    ↓
Select website folder
    ↓
Validate project
    ↓
Choose project name
    ↓
[ Deploy ]
    ↓
Start local HTTP server
    ↓
Establish secure tunnel
    ↓
Register deployment
    ↓
Generate public URL
    ↓
🟢 LIVE
```

---

# 9. Example User Experience

The user selects:

```text
portfolio/
```

PocketServer detects:

```text
✓ index.html found
✓ CSS files found
✓ JavaScript files found
✓ Assets found
```

The application displays:

```text
┌─────────────────────────────┐
│ Deploy Project              │
│                             │
│ Project                     │
│ portfolio                   │
│                             │
│ Files                       │
│ 42 files                    │
│ 18.4 MB                     │
│                             │
│ [ Deploy ]                  │
└─────────────────────────────┘
```

After deployment:

```text
┌─────────────────────────────┐
│ 🟢 LIVE                     │
│                             │
│ portfolio                   │
│                             │
│ https://abc123.example.dev  │
│                             │
│ [ OPEN ]                    │
│ [ COPY ]                    │
│ [ QR CODE ]                 │
│                             │
│ Requests: 124               │
│ Traffic: 3.2 MB             │
│                             │
│ [ STOP SERVER ]             │
└─────────────────────────────┘
```

---

# 10. V1 Feature Requirements

## 10.1 Static File Hosting

The Android application must serve:

```text
HTML
CSS
JavaScript
Images
Fonts
SVG
JSON
WebAssembly/static assets
```

The server must correctly provide MIME types.

Examples:

```text
.html → text/html
.css  → text/css
.js   → application/javascript
.json → application/json
.png  → image/png
.jpg  → image/jpeg
.svg  → image/svg+xml
```

---

# 11. Project Selection

Users must be able to select a project directory using Android's Storage Access Framework.

The app should NOT require unrestricted access to the entire phone filesystem.

The user explicitly selects the directory to be hosted.

Example:

```text
Documents/
└── MyPortfolio/
    ├── index.html
    ├── assets/
    └── css/
```

PocketServer receives access only to the selected directory.

---

# 12. Project Validation

Before deployment, PocketServer should check:

### Required

```text
index.html
```

### Optional

```text
assets/
css/
js/
images/
fonts/
```

If `index.html` is missing:

```text
⚠ No index.html found.

This directory cannot be deployed as a static website.
```

Future versions may support configurable entry points.

---

# 13. Local HTTP Server

The Android application must run a lightweight HTTP server locally.

Example:

```text
Android Device
      │
      ▼
PocketServer HTTP Server
      │
      ▼
127.0.0.1:8080
```

The local server must:

* Serve static files.
* Support GET requests.
* Return correct HTTP status codes.
* Support directory routing.
* Return `index.html` for `/`.
* Handle 404 errors.
* Support HTTP caching headers where appropriate.

---

# 14. Public Tunnel

This is the core infrastructure component.

The Android application establishes an outbound connection to a PocketServer relay.

```text
Android
   │
   │ outbound connection
   ▼
PocketServer Relay
   │
   │ HTTPS
   ▼
Internet
```

This avoids requiring:

* Public IPv4
* Port forwarding
* Router configuration
* Mobile carrier port forwarding

---

# 15. Tunnel Requirements

The tunnel must:

* Maintain a persistent connection.
* Automatically reconnect after temporary network failures.
* Detect connection loss.
* Support request forwarding.
* Associate a tunnel with a deployment.
* Authenticate the Android client.
* Encrypt traffic.

Recommended initial implementation:

```text
HTTPS + WebSocket
```

A future version can evaluate:

```text
QUIC
HTTP/3
custom multiplexed TCP/UDP tunnel
```

---

# 16. Public URL Generation

V1 should use a temporary PocketServer-controlled domain or development domain.

Example:

```text
https://a7f3k2.<temporary-domain>
```

The URL must uniquely identify the deployment.

Internally:

```text
deployment_id
      ↓
project_id
      ↓
device_id
      ↓
tunnel_id
```

Example:

```text
deployment_id = dep_8a73
project_id    = proj_91cd
device_id     = dev_7821
tunnel_id     = tun_1938
```

---

# 17. URL Requirements

The public URL must:

* Be unique.
* Be shareable.
* Remain active while deployment is running.
* Return an error when deployment is stopped.
* Not expose the phone's IP address.
* Not expose the user's local network information.

Example:

```text
https://portfolio-a81f.<temporary-domain>
```

---

# 18. QR Code

Every live deployment should generate a QR code.

Example:

```text
Deploy
 ↓
Live URL
 ↓
Generate QR
 ↓
Scan with another phone
 ↓
Website opens
```

This is particularly useful for:

* College demonstrations
* Hackathons
* Presentations
* Client previews

---

# 19. Deployment Dashboard

The dashboard should show:

```text
Project name
Deployment status
Public URL
Local server status
Tunnel status
Request count
Data transferred
Deployment duration
```

Example:

```text
🟢 LIVE

Portfolio

URL
https://portfolio-a81f.example.dev

Requests
1,284

Traffic
24.7 MB

Uptime
02:14:32

Tunnel
🟢 Connected
```

---

# 20. Server Controls

The user must be able to:

```text
Start
Stop
Restart
Redeploy
```

A stopped deployment should immediately become unavailable publicly.

---

# 21. Android Background Operation

The server should continue running when the user minimizes the application.

V1 should use an Android Foreground Service where required.

The app should display a persistent notification:

```text
PocketServer

🟢 portfolio is live

Tap to open dashboard.
```

The app should detect:

* Network changes
* Internet loss
* Tunnel disconnection
* Server failure
* Device restart

---

# 22. Network Failure Handling

If Internet connectivity disappears:

```text
Internet lost
      ↓
Tunnel disconnected
      ↓
Show:
⚠ Connection lost
      ↓
Retry automatically
```

When connectivity returns:

```text
Internet restored
      ↓
Reconnect tunnel
      ↓
Deployment becomes LIVE
```

The application should use exponential backoff to avoid aggressive reconnection.

---

# 23. Security Requirements

Security is a major V1 requirement.

The application must:

* Never expose arbitrary phone storage.
* Serve only the user-selected directory.
* Authenticate tunnel connections.
* Encrypt tunnel communication.
* Validate deployment ownership.
* Prevent directory traversal.
* Prevent access to parent directories.
* Sanitize file paths.
* Reject malformed HTTP requests where appropriate.

For example, requests like:

```text
../../../../DCIM/
```

must never escape the hosted project directory.

---

# 24. Authentication

V1 should support basic account authentication.

Possible options:

```text
Email + password
```

or:

```text
Google Sign-In
```

The account is associated with:

```text
User
 ├── Devices
 ├── Projects
 └── Deployments
```

---

# 25. Device Registration

Each Android installation should receive a unique device identifier.

Example:

```text
Device
dev_8a73f2
```

The device must authenticate before establishing a tunnel.

---

# 26. Cloud Architecture

The system should consist of four primary components.

```text
                 INTERNET
                    │
                    ▼
             ┌─────────────┐
             │   Gateway   │
             └──────┬──────┘
                    │
              Tunnel Router
                    │
                    ▼
             ┌─────────────┐
             │   Android   │
             │    App      │
             └──────┬──────┘
                    │
              Local HTTP
                 Server
                    │
                    ▼
              Website Files
```

Additional control-plane services:

```text
Authentication
Project Management
Deployment Registry
Tunnel Registry
Analytics
```

---

# 27. Backend Components

V1 cloud infrastructure should contain:

### API Server

Responsible for:

```text
Authentication
Device registration
Project creation
Deployment creation
URL generation
Deployment status
```

### Tunnel Gateway

Responsible for:

```text
Incoming HTTPS
Hostname routing
Tunnel management
Request forwarding
```

### Database

Stores:

```text
Users
Devices
Projects
Deployments
Tunnel metadata
```

Possible V1 choice:

```text
PostgreSQL
```

---

# 28. Android Architecture

Recommended:

```text
Kotlin
Jetpack Compose
Coroutines
Foreground Service
Android Storage Access Framework
```

Architecture:

```text
UI
 │
 ▼
ViewModel
 │
 ▼
Repository
 │
 ├── API Client
 ├── Tunnel Manager
 ├── Web Server
 ├── Project Manager
 └── Deployment Manager
```

---

# 29. Suggested Android Components

```text
PocketServer
│
├── MainActivity
│
├── Dashboard
│
├── ProjectPicker
│
├── DeploymentManager
│
├── LocalWebServer
│
├── TunnelManager
│
├── ForegroundServerService
│
├── NetworkMonitor
│
└── StorageManager
```

---

# 30. Suggested Cloud Stack

A practical V1 stack:

### Gateway/API

```text
Node.js
TypeScript
Fastify / Express
```

### Database

```text
PostgreSQL
```

### Tunnel

```text
WebSocket
```

### Reverse Proxy

```text
Nginx
```

or an equivalent modern proxy.

### Hosting

A small VPS is sufficient for the initial prototype.

---

# 31. Deployment Data Model

Example:

```text
User
 ├── id
 ├── email
 └── created_at

Device
 ├── id
 ├── user_id
 ├── device_name
 └── last_seen

Project
 ├── id
 ├── user_id
 ├── name
 └── created_at

Deployment
 ├── id
 ├── project_id
 ├── device_id
 ├── public_hostname
 ├── status
 ├── created_at
 └── stopped_at

Tunnel
 ├── id
 ├── device_id
 ├── deployment_id
 ├── status
 └── last_seen
```

---

# 32. Deployment States

A deployment can have:

```text
CREATING
    ↓
CONNECTING
    ↓
LIVE
    ↓
DISCONNECTED
    ↓
RECONNECTING
    ↓
LIVE
```

or:

```text
STOPPED
FAILED
```

---

# 33. Error Handling

### No Internet

```text
No Internet connection.

Connect to the Internet and try again.
```

### Tunnel failure

```text
Unable to connect to PocketServer.

Retrying...
```

### Invalid project

```text
No index.html found.
```

### Server crash

```text
Local server stopped unexpectedly.

[ Restart ]
```

### Phone storage unavailable

```text
The selected project directory is no longer accessible.
```

---

# 34. Performance Requirements

V1 should target:

### Static files

```text
< 100 MB recommended project size
```

This is a product recommendation rather than a hard technical limitation.

### Concurrent users

Initial target:

```text
5–20 simultaneous users
```

per phone deployment.

The actual limit depends heavily on:

* Phone hardware
* Network bandwidth
* Mobile carrier
* File sizes
* Number of requests

PocketServer should NOT promise production-scale traffic in V1.

---

# 35. Battery Requirements

The app should minimize battery usage.

Requirements:

* Avoid unnecessary polling.
* Maintain one persistent tunnel.
* Avoid continuous CPU-intensive tasks.
* Stop server when deployment is stopped.
* Warn users that public hosting consumes battery/data.

Example:

```text
⚠ Your phone is currently hosting a public website.

This may increase:
• Battery usage
• Mobile data usage
• Device temperature
```

---

# 36. Mobile Data Protection

The app should display traffic statistics:

```text
Data transferred today
234 MB
```

V1 should provide a configurable warning threshold.

Example:

```text
⚠ 500 MB data used
```

Future versions can support:

```text
Wi-Fi only
Mobile data allowed
Data limit
```

---

# 37. Analytics

V1 basic analytics:

```text
Total requests
Bandwidth
Deployment uptime
Current connections
```

Optional:

```text
HTTP status codes
404 count
```

V1 should avoid collecting unnecessary visitor personal information.

---

# 38. UI Screens

V1 should contain:

### 1. Splash

```text
PocketServer
Your phone. Your server.
```

### 2. Authentication

```text
Login
Create account
```

### 3. Dashboard

```text
Projects
Deployments
Devices
```

### 4. New Deployment

```text
Select folder
Project name
Deploy
```

### 5. Deployment Details

```text
Status
URL
Statistics
QR code
Controls
```

### 6. Settings

```text
Account
Network
Battery
Data usage
Notifications
About
```

---

# 39. V1 UX Principle

The primary workflow must require **as few steps as possible**.

Target:

```text
Open
 ↓
Select folder
 ↓
Deploy
 ↓
Copy URL
```

Maximum target:

**< 30 seconds** from opening the app to receiving the public URL on a good network.

---

# 40. MVP Success Criteria

V1 is considered technically successful when:

### Test 1

A React/Vite production build can be selected on Android.

### Test 2

PocketServer starts a local HTTP server.

### Test 3

Android establishes a tunnel to the relay.

### Test 4

The relay assigns a public URL.

### Test 5

A completely different network can access that URL.

Example:

```text
Android phone
    │
   5G
    │
    ▼
PocketServer

Laptop
    │
   Wi-Fi
    │
    ▼
https://project.example.dev
```

### Test 6

Website assets load correctly.

### Test 7

Stopping the deployment makes the URL unavailable.

### Test 8

Temporary network loss automatically reconnects the deployment.

---

# 41. Acceptance Criteria

A deployment is considered successful if:

```text
Given:
A valid static website directory

When:
The user selects Deploy

Then:
A local HTTP server starts

And:
A secure tunnel is established

And:
A public URL is generated

And:
The website is accessible from an external network

And:
The user can stop the deployment
```

---

# 42. V1 Security Acceptance Criteria

The system must pass:

```text
✓ Directory traversal test
✓ Unauthorized tunnel test
✓ Invalid authentication test
✓ Deployment ownership test
✓ Malformed path test
✓ Access outside project directory test
✓ Token leakage test
```

---

# 43. V1 Limitations

Users should clearly understand that:

> PocketServer V1 is intended for development, demonstration and temporary hosting.

It is NOT intended for:

```text
High traffic production websites
Financial applications
Critical infrastructure
Large databases
Heavy backend workloads
24/7 production hosting
```

---

# 44. Future Roadmap

## V1

```text
Static websites
Public URL
Reverse tunnel
HTTPS
QR sharing
Basic analytics
```

## V2

```text
Multiple projects
Custom domains
WebSockets
Improved analytics
GitHub import
Deployment history
```

## V3

```text
Node.js
Express
Python
Flask
FastAPI
```

## V4

```text
Databases
Background workers
Scheduled tasks
Persistent services
```

## V5

```text
Containerized workloads
AI inference
IoT services
Personal cloud
```

---

# 45. Product Differentiator

PocketServer's central differentiator is:

> **The server is physically owned by the developer.**

Traditional deployment:

```text
Developer
    ↓
Cloud
    ↓
Website
```

PocketServer:

```text
Developer
    ↓
📱 Personal device
    ↓
Website
```

The cloud infrastructure exists primarily to provide connectivity and routing rather than hosting the user's application.

---

# 46. V1 Product Positioning

### Short description

> **PocketServer turns your Android phone into a public web server. Deploy static websites directly from your phone and share them through a public URL.**

### One-line pitch

> **Your phone. Your website. Your server.**

### Developer-focused pitch

> **Deploy your static website from Android and make it publicly accessible in seconds.**

---

# 47. V1 Development Priority

The implementation order should be:

```text
1. Local Android HTTP server
        ↓
2. Static file serving
        ↓
3. Android foreground service
        ↓
4. Cloud API
        ↓
5. Device authentication
        ↓
6. Persistent tunnel
        ↓
7. Public gateway
        ↓
8. Public URL
        ↓
9. Deployment dashboard
        ↓
10. QR sharing
        ↓
11. Analytics
        ↓
12. Security hardening
```

Do **not** start by building the beautiful UI.

The first technical milestone should be:

> **A website running from an Android phone is accessible from a completely different Internet connection.**

Once that works, the rest is product engineering around a proven core.

---

# 48. Definition of Done

PocketServer V1 is ready for public MVP testing when a new developer can:

```text
Install PocketServer
        ↓
Sign in
        ↓
Select React/Vite build
        ↓
Tap Deploy
        ↓
Wait for connection
        ↓
Receive public HTTPS URL
        ↓
Share URL
        ↓
Someone on another network opens it
        ↓
Website loads
```

without manually configuring:

```text
Port forwarding
Router settings
DNS records
VPS configuration
SSH
SSL certificates
Firewall rules
```

That is the core product promise of PocketServer V1.
