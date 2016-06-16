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
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int color = Color.rgb(88,44,131);
    private static final String TAG = "AndroidCameraApi";
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private TextView mLatitudeTextView;
    private TextView mLongitudeTextView;
    private TextView mLocDiff;
    private GoogleMap mMap;
    private List<LatLng> coords;
    private long picId = 0;
    private List<String> picIds;
    /*------------------------------------
    *
    * ---------------------------------*/
    private long time;
    private Polyline line;
    private LatLng myLocation;
    private boolean mapIsReady = false;
    private Marker mark;
    private GoogleApiClient mGoogleApiClient;
    private String fileName = "Hiking_Trails";
    private boolean tracking = true;
    private Button takePictureButton;
    private TextureView textureView;
    private String cameraId;
    private Size imageDimension;
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private ImageReader imageReader;
    private File file;
    private int count =0;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;

    /*-----------------------------
    * ------------------------------*/
    /**
     *A callback objects for receiving updates about the state of a camera device.
     * its instance is used while opening the camera
     * when it is open it calls the Camera preview
     **/

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };
    private HandlerThread mBackgroundThread;

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


        //---------
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: put with the location changed
                takePicture();
            }
        });

    }

    private void initTextviewsContainersClient() {
        coords = new ArrayList<>(2000);
        picIds = new ArrayList<>(2000);

        mLatitudeTextView = (TextView) findViewById((R.id.latitude_textview));
        mLongitudeTextView = (TextView) findViewById((R.id.longitude_textview));
        mLocDiff = (TextView) findViewById((R.id.loc_diff));

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

    // TODO: find a suitable difference between locations

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    /*
     * we can use these to methods to say
     * if(subtractLocations == 'OUR DEFINED DIFFERENCE')
     *      TAKE A PICTURE
     */

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
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
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

        double earthRadius_m = 6371000;
        /**
         *  Lat = Y ; Long = X
         */
        // myLocation stores the most recent previous location of the user
        double dlon = newLoc.getLongitude() - oldLoc.longitude;
        double dlat = newLoc.getLatitude() - oldLoc.latitude;

        dlon = Math.toRadians(dlon);
        dlat = Math.toRadians(dlat);

        double a = Math.pow(Math.sin(dlat),2) +
                Math.pow(Math.cos( newLoc.getLongitude()),2)* Math.pow(Math.sin(dlon),2);

        double b = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        System.out.println(earthRadius_m * b);

        return earthRadius_m *b;
    }

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
         /*   mLatitudeTextView.setText(String.valueOf(loc.getLatitude()));
            mLongitudeTextView.setText(String.valueOf(loc.getLongitude()));*/
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
        if(mapIsReady && tracking)
        {
            Log.i(TAG, "onLocationChanged: tracking ========  " + tracking);
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
    /*------------------
    * -------------*/

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
                            subtractLocations(myLocation, location) + " meters",
                    Toast.LENGTH_SHORT).show();
        }
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    protected void takePicture() {
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final File file = new File(Environment.getExternalStorageDirectory()+"/pic" +count +".jpg");
            count +=1;
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);

                        //reopening  the camera on the surface i guess

                        textureView.setSurfaceTextureListener(textureListener);

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    /**
     * called by the CallBack method
     * Gets the surfaceTexture
     * uses the imageDimension instantiate in openCamera
     * creates a capture requestCapture
     */
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by the surfaceTextureListener once it is ready
     * manager is the Camera services
     *getCameraIdList returns the list of all connected camera
     *getCameraCharacteristics queries all the capabilities of the camera
     * -------
     *StreamConfigurationMap Immutable class to store the available stream configurations to set up
     *Surfaces for creating a capture session with createCaptureSession(List, CameraCaptureSession.StateCallback, Handler).
     * more @https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap.html
     * it has all the list of all possible output formats
     * ------
     *in the end if opens the camera with the id and the StateCallBack
     **/
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
}

