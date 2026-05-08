from flask import Flask, request, jsonify
import cv2
import os
import numpy as np
from ultralytics import YOLO
import base64
from pymongo import MongoClient
from datetime import datetime

app = Flask(__name__)

# ── MongoDB Connection ────────────────────────────────────────────────────────
MONGO_URL = os.environ.get("MONGO_URL", "mongodb://localhost:27017/")
client = MongoClient(MONGO_URL)
db = client["pothole_db"]
collection = db["potholes"]

# ── Load YOLOv8 Model ─────────────────────────────────────────────────────────
model = YOLO("latest.pt")
class_names = model.names

def calculate_severity(confidence, contour_area, image_area):
    area_ratio = (contour_area / image_area) * 100
    if confidence > 0.80 and area_ratio > 15:
        return "High"
    elif confidence > 0.60 and area_ratio > 8:
        return "Medium"
    else:
        return "Low"

def detect_potholes(image):
    h, w, _ = image.shape
    image_area = h * w
    results = model.predict(image, conf=0.45)

    pothole_count = 0
    severities = []

    for r in results:
        boxes = r.boxes
        masks = r.masks

        if masks is not None and len(masks) > 0:
            masks_data = masks.data.cpu().numpy()
            for seg, box in zip(masks_data, boxes):
                seg = cv2.resize(seg, (w, h))
                contours, _ = cv2.findContours(
                    seg.astype(np.uint8),
                    cv2.RETR_EXTERNAL,
                    cv2.CHAIN_APPROX_SIMPLE
                )
                for contour in contours:
                    d = int(box.cls)
                    c = class_names[d]
                    confidence = float(box.conf)
                    contour_area = cv2.contourArea(contour)

                    # Calculate severity
                    severity = calculate_severity(confidence, contour_area, image_area)
                    severities.append(severity)

                    # Pick color based on severity
                    color = (0, 0, 255) if severity == "High" else \
                            (0, 165, 255) if severity == "Medium" else \
                            (0, 255, 0)

                    x, y, bw, bh = cv2.boundingRect(contour)
                    cv2.polylines(image, [contour], True, color=color, thickness=2)
                    cv2.putText(image, f"{c} [{severity}]", (x, y - 10),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 2)
                    pothole_count += 1

    # Overall severity = worst one found
    overall_severity = "Low"
    if "High" in severities:
        overall_severity = "High"
    elif "Medium" in severities:
        overall_severity = "Medium"

    return image, pothole_count, overall_severity


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "model": "YOLOv8 Pothole Detection"})


@app.route("/detect", methods=["POST"])
def detect():
    if "image" not in request.files:
        return jsonify({"error": "No image provided"}), 400

    # Get location from request
    lat = request.form.get("latitude", None)
    lon = request.form.get("longitude", None)

    file = request.files["image"]
    img_bytes = file.read()
    np_arr = np.frombuffer(img_bytes, np.uint8)
    image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

    if image is None:
        return jsonify({"error": "Invalid image"}), 400

    # Run detection
    annotated_image, pothole_count, severity = detect_potholes(image)

    # Convert annotated image to base64
    _, buffer = cv2.imencode(".jpg", annotated_image)
    img_base64 = base64.b64encode(buffer).decode("utf-8")

    # Save to MongoDB if pothole detected
    print(f"Debug - lat: {lat}, lon: {lon}, count: {pothole_count}")
    if pothole_count > 0 and lat is not None and lon is not None:
        document = {
            "latitude": float(lat),
            "longitude": float(lon),
            "severity": severity,
            "pothole_count": pothole_count,
            "image_base64": img_base64,
            "timestamp": datetime.now(),
            "status": "reported"
        }
        result = collection.insert_one(document)
        doc_id = str(result.inserted_id)
        print(f"✅ Inserted with ID: {doc_id}")
        print(f"✅ Saved to MongoDB: {severity} severity at {lat}, {lon}")

    return jsonify({
        "pothole_detected": pothole_count > 0,
        "pothole_count": pothole_count,
        "severity": severity,
        "annotated_image": img_base64,
        "image_id": doc_id if pothole_count > 0 else None
    })


@app.route("/potholes", methods=["GET"])
def get_potholes():
    """Get all reported potholes — useful for map display"""
    potholes = []
    for doc in collection.find({}, {"_id": 0, "image_base64": 0}):
        doc["timestamp"] = str(doc["timestamp"])
        potholes.append(doc)
    return jsonify(potholes)

@app.route("/view/<id>", methods=["GET"])
def view_image(id):
    from bson import ObjectId
    doc = collection.find_one({"_id": ObjectId(id)})
    if not doc:
        return "Not found", 404
    img_base64 = doc["image_base64"]
    return f'''
    <html>
    <body style="background:#000;text-align:center">
        <h2 style="color:white">Severity: {doc["severity"]} | Count: {doc["pothole_count"]}</h2>
        <p style="color:gray">{doc["timestamp"]} | {doc["latitude"]}, {doc["longitude"]}</p>
        <img src="data:image/jpeg;base64,{img_base64}" style="max-width:800px"/>
    </body>
    </html>
    '''

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)