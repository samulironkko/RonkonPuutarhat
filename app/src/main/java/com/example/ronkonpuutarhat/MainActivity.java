package com.example.ronkonpuutarhat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.ColorUtils;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.cocoahero.android.geojson.Feature;
import com.cocoahero.android.geojson.FeatureCollection;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.events.Event;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngQuad;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.annotation.Circle;
import com.mapbox.mapboxsdk.plugins.annotation.CircleManager;
import com.mapbox.mapboxsdk.plugins.annotation.CircleOptions;
import com.mapbox.mapboxsdk.plugins.annotation.OnCircleClickListener;
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager;
import com.mapbox.mapboxsdk.style.layers.FillLayer;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.RasterLayer;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.ImageSource;
import com.mapbox.mapboxsdk.style.sources.Source;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.mapbox.mapboxsdk.style.expressions.Expression.exponential;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.stop;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GeoJsonSource indoorBuildingSource;
    FirebaseFirestore db;

    private List<List<Point>> boundingBoxList;
    private ImageView hoveringMarker;
    private Style mapStyle;
    private Layer droppedMarkerLayer;
    private MapboxMap mapboxMap;
    private CircleManager circleManager;
    private List<CircleOptions> circleOptionsList;
    private int activeMarkerId;

    BottomSheetBehavior bottomSheetBehavior;

    private ArrayList<Marker> markers = new ArrayList<>();

    private MapView mapView;
    private static final String DROPPED_MARKER_LAYER_ID = "DROPPED_MARKER_LAYER_ID";
    private static final String ID_IMAGE_SOURCE = "animated_image_source";
    private static final String ID_IMAGE_LAYER = "animated_image_layer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        Mapbox.getInstance(this, getString(R.string.access_token));

        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(MainActivity.this);

        db = FirebaseFirestore.getInstance();

        Button deleteButton = findViewById(R.id.delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                db.collection("markers").document(markers.get(activeMarkerId).getId()).delete()
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                circleManager.delete(circleManager.getAnnotations().get(activeMarkerId));
                                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                                Toast.makeText(MainActivity.this, "Marker has been deleted", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        final ConstraintLayout bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View view, int i) {

            }

            @Override
            public void onSlide(@NonNull View view, float v) {

            }
        });


        hoveringMarker = new ImageView(MainActivity.this);
        hoveringMarker.setImageResource(R.drawable.baseline_add_24);
        hoveringMarker.setVisibility(View.INVISIBLE);

        final FloatingActionButton fab = findViewById(R.id.addFAB);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hoveringMarker.getVisibility() == View.INVISIBLE) {
                    hoveringMarker.setVisibility(View.VISIBLE);
                    fab.setImageResource(R.drawable.baseline_done_24);

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
                    hoveringMarker.setLayoutParams(params);
                    mapView.addView(hoveringMarker);

                } else {

                    LatLng mapTargetLatLng = mapboxMap.getCameraPosition().target;

                    fab.setImageResource(R.drawable.baseline_add_24);
                    hoveringMarker.setVisibility(View.INVISIBLE);

                    final Marker marker = new Marker(mapTargetLatLng, "");

                    db.collection("markers")
                            .add(marker)
                            .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                @Override
                                public void onSuccess(DocumentReference documentReference) {

                                    marker.setId(documentReference.getId());
                                    markers.add(marker);

                                    CircleOptions circleOptions = new CircleOptions()
                                            .withLatLng(markers.get(markers.size() - 1).getLatLng())
                                            .withCircleRadius(8f);

                                    circleManager.create(circleOptions);
                                    Toast.makeText(MainActivity.this, "Document added", Toast.LENGTH_SHORT).show();
                                }
                            });

                    mapView.removeView(hoveringMarker);

                }

            }
        });
    }

    private void dbQuery() {
        db.collection("markers")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {

                            for (DocumentSnapshot doc : task.getResult()) {
                                Marker marker = doc.toObject(Marker.class);
                                marker.setId(doc.getId());
                                markers.add(marker);
                            }
                            drawMarkers();

                            //do something with list of pojos retrieved
                        } else {
                            Log.d("tag", "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    private void createMarkerList() {

        circleOptionsList = new ArrayList<>();
        for (int i = 0; i < markers.size(); i++) {
            circleOptionsList.add(new CircleOptions()
                    .withLatLng(markers.get(i).getLatLng())
                    .withCircleRadius(8f)
            );
        }
    }

    private void drawMarkers() {

        circleManager = new CircleManager(mapView, mapboxMap, mapStyle);
        circleManager.addClickListener(new OnCircleClickListener() {
            @Override
            public void onAnnotationClick(Circle circle) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                activeMarkerId = (int) circle.getId();
                Toast.makeText(MainActivity.this, "Circle clicked " + markers.get((int) circle.getId()).getId(), Toast.LENGTH_SHORT).show();
            }
        });

        createMarkerList();

        circleManager.create(circleOptionsList);

    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {

        mapboxMap.setStyle(Style.LIGHT, new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {

                // Add an ImageSource to the map
                MainActivity.this.mapboxMap = mapboxMap;
                mapStyle = style;
                dbQuery();
                indoorBuildingSource = new GeoJsonSource("indoor-building", loadJsonFromAsset("puutarha.geojson"));
                style.addSource(indoorBuildingSource);

                loadBuildingLayer(style);
            }
        });
    }


    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    private void loadBuildingLayer(@NonNull Style style) {

        FillLayer indoorBuildingLayer = new FillLayer("indoor-building-fill", "indoor-building").withProperties(
                fillColor(Color.parseColor("#eeeeee")),

                fillOpacity(interpolate(exponential(1f), zoom(),
                        stop(12f, 0f),
                        stop(12.5f, 0.5f),
                        stop(13f, 1f))));

        style.addLayer(indoorBuildingLayer);

        LineLayer indoorBuildingLineLayer = new LineLayer("indoor-building-line", "indoor-building").withProperties(
                lineColor(Color.parseColor("#50667f")),
                lineWidth(0.5f),
                lineOpacity(interpolate(exponential(1f), zoom(),
                        stop(12f, 0f),
                        stop(12.5f, 0.5f),
                        stop(13f, 1f))));
        style.addLayer(indoorBuildingLineLayer);
    }

    private String loadJsonFromAsset(String filename) {

        try {
            InputStream is = getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, Charset.forName("UTF-8"));

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            return;
        }
        super.onBackPressed();
    }

}
