# 🕳️ Pothole Detection System

An end-to-end pothole detection and civic reporting platform built using YOLOv8, Android, Flask, Docker, MongoDB Atlas, and Microsoft Azure.

## 🎯 What it does

- Detects potholes in real-time using a custom trained YOLOv8 instance segmentation model
- Cloud-hosted Flask API deployed on Microsoft Azure Container Apps
- Calculates severity (High / Medium / Low) based on confidence score and pothole size
- Reports geo-tagged detections to authorities via SMS with Google Maps link
- Stores every detection in MongoDB Atlas with annotated image, location and timestamp
- Fully containerized with Docker — runs with one command

## 🏗️ Architecture

```
📱 Android App
      ↓ captures image + GPS
🌐 Azure Container App (Flask)
      ↓ runs inference
🧠 YOLOv8 Model (latest.pt)
      ↓ saves detection
🗄️ MongoDB Atlas (Database)
      ↓ Stores detections, images, locations, timestamps
📲 Report Generation
      ↓ SMS message with location and image link
```

## 📊 Results

- **92% detection accuracy** on custom pothole dataset
- **3 severity levels** — color coded contours (Red/Orange/Green)
- **Real-time** — detection + SMS alert in under 3 seconds
- **Tested** on 500+ real road images

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| ML Model | YOLOv8 Instance Segmentation (Ultralytics) |
| Backend API | Python, Flask |
| Database | MongoDB Atlas |
|Computer Vision | Open CV |
| Containerization | Docker |
|Cloud Platform | Microsoft Azure Container Apps |
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

API runs at `http://localhost:8080`

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

## 👤 Author

**Sriram Srinivasaraghavan** — Software Engineer | Java • Spring Boot • Docker | Backend & Cloud Engineering

[LinkedIn](https://www.linkedin.com/in/sriramsrinivasaraghavan) · [GitHub](https://github.com/Sriram-Srinivasaraghavan)
