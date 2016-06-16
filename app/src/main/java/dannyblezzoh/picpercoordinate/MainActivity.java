/*
 * Created by Daniel Evans and Blaise Iradukunda on June 15, 2016
 *
 * This application is being built to map the hiking trails of our university,
 * Sewanee: The University of the South. It will record the coordinates of the
 * trails and take pictures of the trail programmatically.
 *
 * It's creation is a necessity to build our next application SewaneeMaps, which
 * will provide detailed location and direction data for the user while they
 * are hiking in Sewanee. The pictures taken by this app will provide an immersive
 * "street view" preview of each trail in the SewaneeMaps application. Our belief is that
 * the data collected by this app and provided in the SewaneeMaps application will help
 * make Sewanee a safer place to hike and enjoy
 *
 * Finally, each installation of the SewaneeMaps application we come with a full map of
 * each hiking trail in Sewanee so as to curb the threat of losing service in the wilderness
 */

package dannyblezzoh.picpercoordinate;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends FragmentActivity
        implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener

{

    // LOCDIFFERENCE will be the necessary difference between the
    // myLocation variable and the user's newest location before
    // the application takes a picture
    private static final double LOCDIFFERENCE = .0000010;
    private static final String TAG="MainActivity";
    private static final int color = Color.rgb(88,44,131);
    private TextView mLatitudeTextView;
    private TextView mLongitudeTextView;
    private GoogleMap mMap;
    private List<LatLng> coords;
    private long picId = 0;
    private List<String> picIds;
    private Polyline line;
    private LatLng myLocation;
    private boolean mapIsReady = false;
    private Marker mark;
    private GoogleApiClient mGoogleApiClient;
    private String fileName = "Hiking_Trails";
    private boolean tracking = true;

    private String makeFileText(List<LatLng> coords, List<String> picId) {
        String fileText = "";

        /**
         *      the data will be stored in the format:
         *      lat/lng/picId
         */

        for (int i = 0; i < coords.size() && i < picId.size(); ++i) {
            // if not second to last
            if (i <= coords.size() - 1) {
                fileText += coords.get(i).latitude;
                fileText += "/";
                fileText += coords.get(i).longitude;
                fileText += "/";
                fileText += picIds.get(i).toString();
                fileText += "/";
            }
        }
        return fileText;
    }

    /**
     *
     * @param coords the coordinates tracked by the location service
     * @param picIds the ids (names) of the picture taken at each coordinate
     */
    public void saveFileToInternalStorage(List<LatLng> coords, List<String> picIds) {
        String fileText;
        FileOutputStream outputStream;

        fileText = makeFileText(coords,picIds);

        try {
            outputStream = openFileOutput(fileName, Context.MODE_PRIVATE);
            outputStream.write(fileText.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * reads the coordinate data and picture ids from the file
     */
    public void readFile() {
        String line = null;
        StringBuilder sb = null;
        try {
        FileInputStream fis = openFileInput(fileName);
        InputStreamReader isr = new InputStreamReader(fis);
        BufferedReader bufferedReader = new BufferedReader(isr);
        sb = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(sb != null)
            Log.i(TAG, "readFile: " + sb.toString());
    }

    /**
     * disconnects from the location service
     * saves the coordinates and picture ids to storage
     * and reads the data from the saved file
     */
    public void stopLocationSaveAndRead() {
        mGoogleApiClient.disconnect();
        saveFileToInternalStorage(coords,picIds);
        readFile();
    }

    /**
     * attached to a button's onclick
     * starts tracking service if it is stopped
     * else it stops the tracking service and saves the data
     * @param v button that was clicked
     */
    public void clickToSaveAndRead(View v) {
        Button btn = (Button) v;
        if(tracking) {
            btn.setText("Restart tracking");
            stopLocationSaveAndRead();
        } else {
            btn.setText("stop, save, and read");
            mGoogleApiClient.connect();
        }
        tracking = !tracking;
    }


    /**
     * initializes map fragment, textviews, location service client, and the
     * picture and coordinates data containers
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // ideally will lock the orientation
        this.setRequestedOrientation(getRequestedOrientation());

        // initializes everything else
        initTextviewsContainersClient();

    }

    private void initTextviewsContainersClient() {
        coords = new ArrayList<>(2000);
        picIds = new ArrayList<>(2000);

        mLatitudeTextView = (TextView) findViewById((R.id.latitude_textview));
        mLongitudeTextView = (TextView) findViewById((R.id.longitude_textview));

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }


    /**
     * initializes the map and indicates when it is ready, so
     * that we can begin getting location data and updating the map
     * with that data
     * @param googleMap reference to the map loaded on screen
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        mapIsReady = true;
    }

    /**
     *
     * checks if the device can access google play services, connects to it if so
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        int REQUEST_CODE_RECOVER_PLAY_SERVICES = 200;
        if (requestCode == REQUEST_CODE_RECOVER_PLAY_SERVICES) {

            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleApiClient.isConnecting() &&
                        !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Google Play Services must be installed.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed. Error: " + connectionResult.getErrorCode());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    /**
     * creates the location request object to begin gathering location data
     * we want the lowest interval so as to get the most accurate data for
     * mapping the hiking trails
     * @param bundle
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(0);
        mLocationRequest.setFastestInterval(0);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi
                .requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    /**
     * @param newLoc current location of the user
     * @return the absolute value of the difference in the locations by getting
     * the straight line difference between them
     */
    private double subtractLocations(LatLng oldLoc, Location newLoc) {
        /**
         *  Lat = Y ; Long = X
         */
        // myLocation stores the most recent previous location of the user
        double xDiff = Math.abs(newLoc.getLongitude() - oldLoc.longitude);
        double yDiff = Math.abs(newLoc.getLatitude() - oldLoc.latitude);

        return pythThrm(xDiff,yDiff);
    }

    /**
     * overload in case we need to use two locations instead of a LatLng and Location
     * @param oldLoc the most recent location that the user was previously at
     * @param newLoc the current location of the user
     * @return the difference between oldLoc and newLoc as a straight line difference
     * Note that the return value is still in terms of coordinate degrees
     */
    private double subtractLocations(Location oldLoc, Location newLoc) {
        /**
         * Lat = Y ; Long = X
         */
        // myLocation stores the most recent previous location of the user
        double xDiff = Math.abs(newLoc.getLongitude() - oldLoc.getLongitude());
        double yDiff = Math.abs(newLoc.getLatitude() - oldLoc.getLatitude());


        return pythThrm(xDiff,yDiff);
    }

    /*
     * we can use these to methods to say
     * if(subtractLocations == 'OUR DEFINED DIFFERENCE')
     *      TAKE A PICTURE
     */
    /**
     * @param x the x part of the triangle
     * @param y the y part of the triangle
     * @return the length of the hypotenuse of the triangle
     */
    private double pythThrm(double x, double y) {
        double hyp;

        //  hyp^2 = x^2 + y^2

        hyp = Math.sqrt((Math.pow(x,2)) + (Math.pow(y,2)));

        return hyp;
    }

    // helper methods for updating the text views with the most recent coordinates
    private void changeTextViews(Location loc) {
            mLatitudeTextView.setText(String.valueOf(loc.getLatitude()));
            mLongitudeTextView.setText(String.valueOf(loc.getLongitude()));
    }

    /**
     * updates the text views that display the lat and lng coordinates
     * updates the marker on the map to the user's new location
     * toasts the difference between the user's old location and his/her current location
     * saves the coordinate and picture id
     * @param location new location of the user
     */

    // TODO: take a picture and save it to the galleries with the respective picId as its name
    @Override
    public void onLocationChanged(Location location) {
        // if user's location has changed
        if(mapIsReady)
        {
            changeTextViews(location);
            // remove the marker
            if(mark != null) mark.remove();

            toastLocationDifference(location);

            myLocation = new LatLng(location.getLatitude(),location.getLongitude());
            Log.i(TAG, "onLocationChanged: " + myLocation);


            mark = mMap.addMarker(new MarkerOptions()
                    .title("Start").position(myLocation).visible(true));

            mMap.moveCamera(CameraUpdateFactory.newLatLng(myLocation));
            mMap.moveCamera(CameraUpdateFactory.zoomTo(19));

            setPolylinePoints(location);
        }
    }

    /*
    helper method to update the trace of the polyline
    also saves the coordinates and pic ids
     */
    private void setPolylinePoints(Location location) {
        // if this is the first time we are building the line
        if(line == null)
            line = mMap.addPolyline(new PolylineOptions()
                    .add(myLocation)
                    .width(2)
                    .color(color));
        List<LatLng> points = line.getPoints();
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        points.add(latLng);
        line.setPoints(points);
        // these are the coords and picIds that will be saved to the file
        saveCoordsAndPicIds(latLng);
    }
    private void saveCoordsAndPicIds(LatLng latLng) {
        coords.add(latLng);
        picIds.add("picName_" + (++picId));
    }
    // toasts the diff between myLocation and location
    private void toastLocationDifference(Location location) {
        if (myLocation != null) {
            Toast.makeText(this, "Difference was " +
                            subtractLocations(myLocation, location),
                    Toast.LENGTH_SHORT).show();
        }
    }
}

