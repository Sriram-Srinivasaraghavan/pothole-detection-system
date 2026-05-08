# 🕳️ Pothole Detection System

An end-to-end AI-powered pothole detection and reporting system — from a trained YOLOv8 model to a Dockerized Flask API integrated with an Android app for real-time civic reporting.

## 🎯 What it does

- Detects potholes in real-time using a custom trained YOLOv8 instance segmentation model
- Calculates severity (High / Medium / Low) based on confidence score and pothole size
- Reports geo-tagged detections to authorities via SMS with Google Maps link
- Stores every detection in MongoDB with annotated image, location and timestamp
- Fully containerized with Docker — runs with one command

## 🏗️ Architecture

```
📱 Android App
      ↓ captures image + GPS
🌐 Flask REST API (Docker)
      ↓ runs inference
🧠 YOLOv8 Model (latest.pt)
      ↓ saves detection
🗄️ MongoDB (Docker)
      ↓ sends alert
📲 SMS to Authority
```

## 📊 Results

- **92% detection accuracy** on custom pothole dataset
- **3 severity levels** — color coded contours (Red/Orange/Green)
- **Real-time** — detection + SMS alert in under 3 seconds
- **Tested** on 100+ real road images

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| ML Model | YOLOv8 Instance Segmentation (Ultralytics) |
| Backend API | Python, Flask |
| Database | MongoDB |
| Containerization | Docker, Docker Compose |
| Mobile App | Android (Java) |
| Maps | Leaflet.js |

## 🚀 Run with Docker

```bash
# Clone the repo
git clone https://github.com/Sriram-Srinivasaraghavan/pothole-detection-system.git
cd pothole-detection-system/backend

# Start everything
docker-compose up
```

API runs at `http://localhost:5000`

## 📡 API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/health` | GET | Check API status |
| `/detect` | POST | Send image → get detection result |
| `/potholes` | GET | Get all reported potholes |
| `/view/<id>` | GET | View annotated image in browser |

## 🧠 Run Model Standalone

```bash
cd ml-model
pip install gradio ultralytics opencv-python
python detect.py
```

Opens Gradio UI at `http://127.0.0.1:7860`

## 📱 Android App

Full Android app source with Leaflet map, camera integration and SMS reporting:
→ [Pothole Detection Android App](https://github.com/sarathi-c/Pothole-Detection-and-Reporting-System)

**My contribution:** Replaced TFLite model with custom YOLOv8 backend API, added severity scoring, MongoDB integration and MMS reporting.

## 👤 Author

**Sriram Srinivasaraghavan** — ECE Engineer | AI/ML | Full Stack

[LinkedIn](https://linkedin.com/in/Sriram-S) · [GitHub](https://github.com/Sriram-Srinivasaraghavan)