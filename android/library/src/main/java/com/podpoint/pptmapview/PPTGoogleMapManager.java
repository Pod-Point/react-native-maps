package com.podpoint.pptmapview;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.content.ContextCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.*;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class PPTGoogleMapManager extends SimpleViewManager<MapView> implements
        OnMapReadyCallback,
        GoogleMap.OnCameraChangeListener,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMapLongClickListener,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnMarkerDragListener,
        GoogleMap.OnMyLocationButtonClickListener
{

    /**
     * The name of the react module.
     */
    public static final String REACT_CLASS = "PPTGoogleMap";

    /**
     * Whether or not this is the first time the map has become ready.
     */
    private boolean firstMapReady = true;

    /**
     * The context of this view.
     */
    private ReactContext reactContext;

    /**
     * The google map view.
     */
    private MapView mapView;

    /**
     * The android location manager.
     */
    private LocationManager locationManager;

    /**
     * The markers which are to be added to the map.
     */
    private ReadableArray markers;

    /**
     * The the location that the map's camera should be moved to when it is next updated.
     */
    private CameraUpdate cameraUpdate;

    /**
     * Stores the user data associated with a map marker.
     */
    private Map<String, String> publicMarkerIds;

    /**
     * Whether or not the user's location marker is enabled.
     */
    private boolean showsUserLocation = false;

    /**
     * Whether scroll gestures are enabled (default) or disabled.
     */
    private boolean scrollGestures = true;

    /**
     * Whether zoom gestures are enabled (default) or disabled.
     */
    private boolean zoomGestures = true;

    /**
     * Whether tilt gestures are enabled (default) or disabled.
     */
    private boolean tiltGestures = true;

    /**
     * Whether rotate gestures are enabled (default) or disabled.
     */
    private boolean rotateGestures = true;

    /**
     * Whether the compass button is enabled or disabled.
     */
    private boolean compassButton = true;

    /**
     * Whether the my location button has been enabled.
     */
    private boolean myLocationButton = true;

    /**
     * Returns the name of the react module.
     *
     * @return String
     */
    @Override
    public String getName() {
        return REACT_CLASS;
    }

    /**
     * Implementation of the react create view instance method - returns the map view to react.
     *
     * @param context
     * @return MapView
     */
    @Override
    protected MapView createViewInstance(ThemedReactContext context) {
        mapView = new MapView(context);

        mapView.onCreate(null);
        mapView.onResume();

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        }

        reactContext = context;

        return mapView;
    }

    /**
     * Event handler for when map is ready to receive update parameters.
     *
     * @param googleMap
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        UiSettings settings = googleMap.getUiSettings();

        // Set location based flags
        if (locationManager != null) {
            settings.setMyLocationButtonEnabled(this.myLocationButton);
            googleMap.setMyLocationEnabled(this.showsUserLocation);
        }

        // Set all other flags
        settings.setScrollGesturesEnabled(this.scrollGestures);
        settings.setZoomGesturesEnabled(this.zoomGestures);
        settings.setTiltGesturesEnabled(this.tiltGestures);
        settings.setRotateGesturesEnabled(this.rotateGestures);
        settings.setCompassEnabled(this.compassButton);

        // Update the camera position
        if (cameraUpdate != null) {
            googleMap.moveCamera(cameraUpdate);
        }

        // Add the markers
        addMapMarkers(googleMap);

        // Attach the event handlers
        if (firstMapReady) {
            googleMap.setOnCameraChangeListener(this);
            googleMap.setOnMapClickListener(this);
            googleMap.setOnMapLongClickListener(this);
            googleMap.setOnMarkerClickListener(this);
            googleMap.setOnMarkerDragListener(this);
            googleMap.setOnMyLocationButtonClickListener(this);

            firstMapReady = false;
        }
    }

    /**
     * Parses the marker data received from react and add the new markers to the map.
     *
     * @param googleMap
     */
    private void addMapMarkers(GoogleMap googleMap) {
        int count = markers.size();

        publicMarkerIds =  new HashMap<>();
        Map<String, Bitmap> bitmaps = new HashMap<>();

        for (int i = 0; i < count; i++) {
            ReadableMap marker = markers.getMap(i);
            ReadableMap icon = marker.getMap("icon");
            String uri = icon.getString("uri");
            Bitmap markerIcon;

            if (bitmaps.containsKey(uri)) {
                markerIcon = bitmaps.get(uri);
            } else {
                markerIcon = loadMarkerIcon(icon);
                bitmaps.put(uri, markerIcon);
            }

            Marker googleMarker = googleMap.addMarker(
                    new MarkerOptions().position(
                            new LatLng(
                                    marker.getDouble("latitude"),
                                    marker.getDouble("longitude")
                            )
                    ).icon(
                            BitmapDescriptorFactory.fromBitmap(markerIcon)
                    )
            );

            publicMarkerIds.put(googleMarker.getId(), marker.getString("publicId"));
        }
    }

    /**
     * Loads the map marker from react.
     *
     * @param icon
     * @return
     */
    private Bitmap loadMarkerIcon(ReadableMap icon) {
        try {
            URL url = new URL(icon.getString("uri"));

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            InputStream input = connection.getInputStream();
            Bitmap markerIcon = BitmapFactory.decodeStream(input);

            return markerIcon;
        } catch (IOException ex) {
            return null;
        }
    }

    // Map Properties

    /**
     * Sets the user's location marker, if it has been enabled.
     *
     * @param map
     * @param cameraPosition
     */
    @ReactProp(name = "cameraPosition")
    public void setCameraPosition(MapView map, ReadableMap cameraPosition) {
        float zoom = (float) cameraPosition.getDouble("zoom");

        if (cameraPosition.hasKey("auto") && cameraPosition.getBoolean("auto")) {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (location == null) {
                return;
            }

            cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()),
                    zoom
            );

            map.getMapAsync(this);
        } else {
            cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    new LatLng(
                            cameraPosition.getDouble("latitude"),
                            cameraPosition.getDouble("longitude")
                    ), zoom
            );

            map.getMapAsync(this);
        }
    }

    /**
     * Adds marker icons to the map.
     *
     * @param map
     * @param markers
     */
    @ReactProp(name = "markers")
    public void setMarkers(MapView map, ReadableArray markers) {
        this.markers = markers;

        map.getMapAsync(this);
    }

    /**
     * Sets the user's location marker, if it has been enabled.
     *
     * @param showsUserLocation
     */
    @ReactProp(name = "showsUserLocation")
    public void setShowsUserLocation(MapView map, boolean showsUserLocation) {
        this.showsUserLocation = showsUserLocation;

        map.getMapAsync(this);
    }

    /**
     * Controls whether scroll gestures are enabled (default) or disabled.
     *
     * @param map
     * @param scrollGestures
     */
    @ReactProp(name = "scrollGestures")
    public void setScrollGestures(MapView map, boolean scrollGestures) {
        this.scrollGestures = scrollGestures;

        map.getMapAsync(this);
    }

    /**
     * Controls whether zoom gestures are enabled (default) or disabled.
     *
     * @param map
     * @param zoomGestures
     */
    @ReactProp(name = "zoomGestures")
    public void setZoomGestures(MapView map, boolean zoomGestures) {
        this.zoomGestures = zoomGestures;

        map.getMapAsync(this);
    }

    /**
     * Controls whether tilt gestures are enabled (default) or disabled.
     *
     * @param map
     * @param zoomGestures
     */
    @ReactProp(name = "tiltGestures")
    public void setTiltGestures(MapView map, boolean tiltGestures) {
        this.tiltGestures = tiltGestures;

        map.getMapAsync(this);
    }

    /**
     * Controls whether rotate gestures are enabled (default) or disabled.
     *
     * @param map
     * @param rotateGestures
     */
    @ReactProp(name = "rotateGestures")
    public void setRotateGestures(MapView map, boolean rotateGestures) {
        this.rotateGestures = rotateGestures;

        map.getMapAsync(this);
    }

    /**
     * Controls whether gestures by users are completely consumed by the map view when gestures are enabled (default YES).
     *
     * @param map
     * @param consumesGesturesInView
     */
    @ReactProp(name = "consumesGesturesInView")
    public void setConsumesGesturesInView(MapView map, boolean consumesGesturesInView) {
        // Do nothing - this is an iOS feature that we're only implementing so that the Android
        // map package doesn't break.
    }

    /**
     * Enables or disables the compass.
     *
     * @param mapView
     * @param compassButton
     */
    @ReactProp(name = "compassButton")
    public void setCompassButton(MapView map, boolean compassButton) {
        this.compassButton = compassButton;

        map.getMapAsync(this);
    }

    /**
     * Enables or disables the My Location button.
     *
     * @param map
     * @param myLocationButton
     */
    @ReactProp(name = "myLocationButton")
    public void setMyLocationButton(MapView map, boolean myLocationButton) {
        this.myLocationButton = myLocationButton;

        map.getMapAsync(this);
    }

    // Map Events

    /**
     * Called repeatedly during any animations or gestures on the map (or once, if the camera is
     * explicitly set). This may not be called for all intermediate camera positions. It is always
     * called for the final position of an animation or gesture.
     *
     * @param cameraPosition
     */
    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        WritableMap event = Arguments.createMap();
        WritableMap data = Arguments.createMap();

        data.putDouble("latitude", cameraPosition.target.latitude);
        data.putDouble("longitude", cameraPosition.target.longitude);
        data.putDouble("zoom", cameraPosition.zoom);

        event.putString("event", "didChangeCameraPosition");
        event.putMap("data", data);

        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                mapView.getId(),
                "topChange",
                event
        );
    }

    /**
     * Called after a tap gesture at a particular coordinate, but only if a marker was not tapped.
     *
     * @param latLng
     */
    @Override
    public void onMapClick(LatLng latLng) {
        WritableMap event = Arguments.createMap();
        WritableMap data = Arguments.createMap();

        data.putDouble("latitude", latLng.latitude);
        data.putDouble("longitude", latLng.longitude);

        event.putString("event", "didTapAtCoordinate");
        event.putMap("data", data);

        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                mapView.getId(),
                "topChange",
                event
        );
    }

    /**
     * Called after a long-press gesture at a particular coordinate.
     *
     * @param latLng
     */
    @Override
    public void onMapLongClick(LatLng latLng) {
        WritableMap event = Arguments.createMap();
        WritableMap data = Arguments.createMap();

        data.putDouble("latitude", latLng.latitude);
        data.putDouble("longitude", latLng.longitude);

        event.putString("event", "didLongPressAtCoordinate");
        event.putMap("data", data);

        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                mapView.getId(),
                "topChange",
                event
        );
    }

    /**
     * Called after a marker has been tapped.
     *
     * @param marker
     * @return
     */
    @Override
    public boolean onMarkerClick(Marker marker) {
        handleMarkerEvent("didBeginDraggingMarker", marker);

        return false;
    }

    /**
     * Called when dragging has been initiated on a marker.
     *
     * @param marker
     */
    @Override
    public void onMarkerDragStart(Marker marker) {
        handleMarkerEvent("didBeginDraggingMarker", marker);
    }

    /**
     * Called while a marker is dragged.
     *
     * @param marker
     */
    @Override
    public void onMarkerDrag(Marker marker) {
        handleMarkerEvent("didDragMarker", marker);
    }

    /**
     * Called after dragging of a marker ended.
     *
     * @param marker
     */
    @Override
    public void onMarkerDragEnd(Marker marker) {
        handleMarkerEvent("didEndDraggingMarker", marker);
    }

    /**
     * Handles marker events by emitting react events.
     *
     * @param eventName
     * @param marker
     */
    private void handleMarkerEvent(String eventName, Marker marker) {
        WritableMap event = Arguments.createMap();
        WritableMap data = Arguments.createMap();

        data.putDouble("latitude", marker.getPosition().latitude);
        data.putDouble("longitude", marker.getPosition().longitude);
        data.putString("publicId", publicMarkerIds.get(marker.getId()));

        event.putString("event", "didTapMarker");
        event.putMap("data", data);

        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                mapView.getId(),
                "topChange",
                event
        );
    }

    /**
     * Called when the My Location button is tapped. Returns YES if the listener has consumed the
     * event (i.e., the default behavior should not occur), NO otherwise (i.e., the default behavior
     * should occur). The default behavior is for the camera to move such that it is centered on the
     * user location.
     *
     * @return
     */
    @Override
    public boolean onMyLocationButtonClick() {
        WritableMap event = Arguments.createMap();

        event.putString("event", "didTapMyLocationButtonForMapView");

        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                mapView.getId(),
                "topChange",
                event
        );

        return false;
    }
}
