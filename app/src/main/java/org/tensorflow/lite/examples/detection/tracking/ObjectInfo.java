package org.tensorflow.lite.examples.detection.tracking;

public class ObjectInfo {
    public String position = "";
    public float areaSize = 0f;
    public float percentage = 0f;

    public ObjectInfo(String position, float areaSize, float percentage) {
        this.position = position;
        this.areaSize = areaSize;
        this.percentage = percentage;
    }
}
