package com.example.ronkonpuutarhat;

import com.mapbox.mapboxsdk.geometry.LatLng;

public class Marker {
    private LatLng latLng;
    private String text;
    private String id;

    public Marker(){}

    public Marker(LatLng latLng, String text) {
        this.latLng = latLng;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LatLng getLatLng() {
        return latLng;
    }

    public void setLatLng(LatLng latLng) {
        this.latLng = latLng;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
