import gradio as gr
from ultralytics import YOLO
import cv2
import numpy as np


model = YOLO("C:\\Users\\91988\\Desktop\\pothole\\latest.pt")
class_names = model.names

def detect_potholes(image):
    
    image = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
    h, w, _ = image.shape

    
    results = model.predict(image, conf=0.45)

    for r in results:
        boxes = r.boxes 
        masks = r.masks  

        if masks is not None and len(masks) > 0:
            masks = masks.data.cpu().numpy()
            for seg, box in zip(masks, boxes):
                seg = cv2.resize(seg, (w, h))
                contours, _ = cv2.findContours(seg.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

                for contour in contours:
                    d = int(box.cls)
                    c = class_names[d]
                    x, y, bw, bh = cv2.boundingRect(contour)
                    cv2.polylines(image, [contour], True, color=(0, 0, 255), thickness=2)
                    cv2.putText(image, c, (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 2)

    
    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    return image


interface = gr.Interface(fn=detect_potholes, inputs="image", outputs="image", title="Pothole Detection")


interface.launch()
