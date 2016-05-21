package com.lipata.whatsforlunch.api;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.lipata.whatsforlunch.api.yelp.YelpApi;
import com.lipata.whatsforlunch.data.AppSettings;
import com.lipata.whatsforlunch.ui.MainActivity;


/**
 * Created by jlipata on 4/2/16.
 * Class responsible for obtaining device location.
 */
public class GooglePlayApi implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String LOG_TAG = GooglePlayApi.class.getSimpleName();

    final int LOCATION_REQUEST_INTERVAL = 1000; // in milliseconds
    final int LOCATION_REQUEST_FASTEST_INTERVAL = 1000;// in milliseconds
    final int MY_PERMISSIONS_ACCESS_FINE_LOCATION_ID = 0;

    private MainActivity mMainActivity;
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    private LocationRequest mLocationRequest;
    long mLocationUpdateTimestamp; // in milliseconds

    public GooglePlayApi(MainActivity mainActivity) {
        this.mMainActivity = mainActivity;

        mGoogleApiClient = new GoogleApiClient.Builder(mMainActivity)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                 .addApi(LocationServices.API).build();

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(LOCATION_REQUEST_INTERVAL)
                .setFastestInterval(LOCATION_REQUEST_FASTEST_INTERVAL);
    }

    // Public methods

    public void showLastLocation(){
        Log.d(LOG_TAG, "showLastLocation()...");

        // Get last location & update timestamp
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        mLocationUpdateTimestamp = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG, "mLocationUpdateTimestamp = " + mLocationUpdateTimestamp);

        // If LastLocation is not null, pass to MainActivity to be displayed
        if (mLastLocation != null) {
            double latitude = mLastLocation.getLatitude();
            double longitude = mLastLocation.getLongitude();
            float accuracy = mLastLocation.getAccuracy();
            Log.d(LOG_TAG, "Success " + latitude + ", " + longitude + ", " + accuracy);

            mMainActivity.updateLocationViews(latitude, longitude, accuracy);

            stopLocationUpdates();
        } else {
            Log.d(LOG_TAG, "mLastLocation = null");
        }
    }

    public boolean isLocationStale(){
        long currentTime = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG, "currentTime = " + currentTime);
        Log.d(LOG_TAG, "mLocationUpdateTimestamp = " + mLocationUpdateTimestamp);

        if ((currentTime - mLocationUpdateTimestamp) > AppSettings.LOCATION_LIFESPAN){
            return true;
        } else {
            return false;}
    }

    public void checkPermissionAndRequestLocation() {

        // Check for Location permission
        boolean isPermissionMissing = ContextCompat.checkSelfPermission(mMainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED;
        Log.d(LOG_TAG, "isPermissionMissing = " + isPermissionMissing);

        if(isPermissionMissing) {
            // If permission is missing, we need to ask for it.  See onRequestPermissionResult() callback
            ActivityCompat.requestPermissions(mMainActivity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_ACCESS_FINE_LOCATION_ID);
        } else {

            // Else, permission has already been granted.  Proceed with requestLocationUpdates...
            if(mGoogleApiClient.isConnected()) {
                Log.d(LOG_TAG, "Google API is connected.  Requesting Location Updates...");
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            } else {
                Log.d(LOG_TAG, "Google API not connected.  Reconnecting...");
                mGoogleApiClient.connect();
            }
        }
    }

    public void requestLocationUpdates(){
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    public void stopLocationUpdates() {

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

        // For our purposes, once we have the location, we no longer need the client, so disconnect
        mGoogleApiClient.disconnect();

        Log.d(LOG_TAG, "Location updates stopped and client disconnected");
    }

    public void callYelpApi(){
        /*
        * This code is not placed in the onConnected callback because it can also be called when the
        * Google API client is already connected.
        */

        showLastLocation();

        // If getLastLocation() returned null, start a Location Request to get device location
        // Else, query yelp with existing location arguments
        if (getLastLocation() == null || isLocationStale()) {
            checkPermissionAndRequestLocation();
        } else {
            // Check for network connectivity
            ConnectivityManager cm = (ConnectivityManager)mMainActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

            // If connected to network make Yelp API call, if no network, notify user
            if(isConnected) {
                String ll = getLastLocation().getLatitude() + ","
                        + getLastLocation().getLongitude() + ","
                        + getLastLocation().getAccuracy();
                Log.d(LOG_TAG, "Querying Yelp... ll = " + ll + " Search term: " + AppSettings.SEARCH_TERM);
                new YelpApi(mMainActivity).callYelpApi(AppSettings.SEARCH_TERM, ll, Integer.toString(AppSettings.SEARCH_RADIUS));
            } else {

                // UI
                Snackbar.make(mMainActivity.getCoordinatorLayout(), "No network. Try again when you are connected to the internet.",
                        Snackbar.LENGTH_INDEFINITE).show();
                mMainActivity.stopRefreshAnimation();
            }
        }
    }

    // Callbacks for Google Play API
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(LOG_TAG, "onConnected()");
        callYelpApi();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        // https://developers.google.com/android/reference/com/google/android/gms/common/ConnectionResult

        int errorCode = result.getErrorCode();

        Log.i(LOG_TAG, "GoogleApiClient Connection failed: ConnectionResult.getErrorCode() = " + errorCode);

        switch (errorCode){
            case 1:
                mMainActivity.showSnackBarIndefinite("ERROR: Google Play services is missing on this device");
                break;
            case 2:
                mMainActivity.showSnackBarIndefinite("ERROR: The installed version of Google Play services is out of date.");
                break;
            default:
                mMainActivity.showSnackBarIndefinite("ERROR: Google API Client, error code: " + errorCode);
                break;
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(LOG_TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    // Callback method for LocationRequest
    @Override
    public void onLocationChanged(Location location) {
        Log.d(LOG_TAG, "Location Changed");
        showLastLocation();
    }

    // Getters

    public GoogleApiClient getClient(){
        return mGoogleApiClient;
    }

    public Location getLastLocation(){
        return mLastLocation;
    }

    public LocationRequest getLocationRequest(){
        return mLocationRequest;
    }

    public long getLocationUpdateTimestamp(){
        return  mLocationUpdateTimestamp;
    }

    // Setters
    public void setLocationUpdateTimestamp(long timestamp){
        mLocationUpdateTimestamp = timestamp;
    }

    // TODO Implement this
//    void isEnabledOnDevice(){
//        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
//                .addLocationRequest(mLocationRequest);
//        PendingResult<LocationSettingsResult> result =
//                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
//    }



}
