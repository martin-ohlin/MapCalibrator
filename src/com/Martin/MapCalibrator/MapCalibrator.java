package com.Martin.MapCalibrator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.ListIterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Html;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MapCalibrator extends Activity {
	private SaveData m_SavableData;
	
	private static final int DIALOG_COORDINATE_VERIFICATION_ID 	= 0;
	private static final int DIALOG_INFORMATION_ID 				= 1;
	private static final int DIALOG_CONVERSION_ERROR 			= 2;
	private static final int DIALOG_RESTORE_REFERENCE_POINTS	= 3;
	private static final int DIALOG_MAP_IS_CALIBRATED			= 4;
	private static final int DIALOG_MAP_FAILED_TO_CALIBRATE		= 5;
	
	private static final int TAKE_PICTURE   = 0;
	private static final int SELECT_PICTURE = 2;
	private static final int SELECT_MAP     = 3;
	//private static final String TAG = "MapCalibrator";
	
	private MyLocationListener locationListener;
	private boolean customTitleSupported;
	
	private DBAdapter database;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Create our folder if it is not already created
		if(!mPath.exists()){
			try{
				mPath.mkdirs();
			}
			catch(SecurityException e){
				//Log.e(TAG, "unable to write on the sd card " + e.toString());		  
			}
		}		  
		
		//check if custom title is supported BEFORE setting the content view!
		//Must be before setContentView
		//TODO:Make the custom title look better so that we can use it.
		//customTitleSupported = requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
				
		setContentView(R.layout.main);		
				
		//test();
		
		//Must be after the call to setContentView
		//setCustomTitleBar("MapCalibrator", "±400m");
		
		if(savedInstanceState != null && !savedInstanceState.isEmpty())
		{
			m_SavableData = (SaveData) savedInstanceState.getParcelable("laststate");
			m_SavableData.mapView = (MyDrawableImageView) findViewById(R.id.imageView);
			m_SavableData.mapView.setSaveableData(m_SavableData.mapViewSaveData);
			
			if (m_SavableData.m_bMapIsLoaded == true)
			{
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
				Boolean supportLargeMaps = preferences.getBoolean("supportLargeMaps", false);
				m_SavableData.mapView.setSupportLargeMaps(supportLargeMaps); // Must be called before setMap(
				m_SavableData.mapView.setMap(m_SavableData.mapFile);
			}
			
			if (m_SavableData.m_bIsTrackingPosition == true)
			{
				LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
				locationListener = new MyLocationListener(locationManager, this, m_SavableData.mapView);
				locationListener.startListening();
			}
		}
		else
		{
			m_SavableData = new SaveData();
			//findViewById is only visible in Activity and View, therefore I can not create it in my object
			m_SavableData.mapView = (MyDrawableImageView) findViewById(R.id.imageView);
			
			// Restore preferences
			SharedPreferences settings = getPreferences(MODE_PRIVATE);
			int oldVersion = settings.getInt("version", 0);			
			int currentVersion = 0;
			PackageInfo pInfo = null;			
			try {
				pInfo = getPackageManager().getPackageInfo("com.Martin.MapCalibrator", PackageManager.GET_META_DATA);			
			} catch (NameNotFoundException e) {				
			}
			if (pInfo != null)
				currentVersion = pInfo.versionCode;
			
			if (currentVersion > oldVersion) {
				showDialog(DIALOG_INFORMATION_ID);
				SharedPreferences.Editor editor = settings.edit();
				editor.putInt("version", currentVersion);
				editor.commit();
			} else {
				Toast.makeText(
						this.getApplicationContext(),
						"Use the menu button to see all available options.", Toast.LENGTH_LONG)
						.show();
			}
		}
		
		database = new DBAdapter(this);
	}
	
	/*
	private void setCustomTitleBar(String left, String right) {
		if (right.length() > 20) right = right.substring(0, 20);
		// set up custom title
		if (customTitleSupported) {
			getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.customtitlebar);
			TextView titleTvLeft = (TextView) findViewById(R.id.titleTvLeft);
			TextView titleTvRight = (TextView) findViewById(R.id.titleTvRight);
			titleTvLeft.setText(left);
			titleTvRight.setText(right);	
		}
	}
	*/
	
	protected void updateCustomTitleBar(String right) {
		if (right.length() > 20) right = right.substring(0, 20);
		// set up custom title
		if (customTitleSupported) {			
			TextView titleTvRight = (TextView) findViewById(R.id.titleTvRight);			
			titleTvRight.setText(right);	
		}
	}
	
	@Override
	//Not always called before onPause
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);                
        outState.putParcelable("laststate", m_SavableData);
    }
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (m_SavableData.m_bIsTrackingPosition == true)
			locationListener.startListening();
	}
	
	@Override
	protected void onPause ()
	{
		super.onPause();
		
		if (m_SavableData.m_bIsTrackingPosition == true)
        	locationListener.stopListening();
		
		try {//We could try to keep track of wether it is displayed, but this is easier.
			// Dialogs should be dismissed in OnPause to avoid problems. 
			dismissDialog(DIALOG_COORDINATE_VERIFICATION_ID);
		} catch(IllegalArgumentException e)
		{
			//The dialog was not currently displayed.
		}	
	}
	
	@Override
	protected void onDestroy () {
		super.onDestroy();
		
		if (isFinishing() && m_SavableData.m_bIsTrackingPosition)
        	locationListener.stopListening();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		menu.findItem(R.id.new_calibration_point).setEnabled(m_SavableData.m_bMapIsLoaded);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (m_SavableData.m_bMapIsLoaded && !m_SavableData.m_bIsCalibrating)
			menu.findItem(R.id.new_calibration_point).setEnabled(true);
		else
			menu.findItem(R.id.new_calibration_point).setEnabled(false);
		
		menu.findItem(R.id.use_calibration_point).setEnabled(m_SavableData.m_bIsCalibrating);				
		return true;
	}
	
	private void resetForNewMap() {
		m_SavableData.coordinateMappingList.clear();
		m_SavableData.m_bMapIsLoaded = true;
		m_SavableData.m_bIsCalibrating = false;

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		Boolean supportLargeMaps = preferences.getBoolean("supportLargeMaps", false);
		m_SavableData.mapView.setSupportLargeMaps(supportLargeMaps); // Must be called before setMap(
		
		m_SavableData.mapView.setMap(m_SavableData.mapFile);
		
		database.open();
		m_SavableData.m_iMapKey = database.getMapKey(m_SavableData.mapFile.getAbsolutePath());
		if (m_SavableData.m_iMapKey != -1)
		{
			if (database.mapHasReferencePoints(m_SavableData.m_iMapKey))
			{
				database.close();
				showDialog(DIALOG_RESTORE_REFERENCE_POINTS);
			}			
		}
		else
		{
			m_SavableData.m_iMapKey = database.insertMap(
					m_SavableData.mapFile.getName(), 
					m_SavableData.mapFile.getAbsolutePath());
			database.close();
		}
		
		if (locationListener == null)
		{
			m_SavableData.m_bIsTrackingPosition = true; //TODO:We really don't need this until we have added a reference point
			LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
			locationListener = new MyLocationListener(locationManager, this, m_SavableData.mapView);
		}			
		locationListener.startListening();
	}
	
	private void loadAndUseOldReferencePoints() {
		if (m_SavableData.m_iMapKey != -1)
		{
			database.open();
			m_SavableData.coordinateMappingList = database.getAllReferencePointsForMap(m_SavableData.m_iMapKey);
			database.close();
			tryToCalibrateMap();
		}
	}
	
	private void deleteOldReferencePoints() {
		if (m_SavableData.m_iMapKey != -1)
		{
			database.open();
			database.deleteReferencePoints(m_SavableData.m_iMapKey);
			database.close();
		}
	}
	
	private void newFromFile() {
		//showDialog(DIALOG_LOAD_FILE);
		Intent fileIntent = new Intent();
		fileIntent.setType("image/*");
		fileIntent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(fileIntent, "Select a Map"), SELECT_PICTURE);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.new_calibration_point:
			m_SavableData.mapView.showCalibrationPoint();
			m_SavableData.m_bIsCalibrating = true;
			return true;
		case R.id.use_calibration_point:			 
			// Display the point to the user for verification and then possibly save
			showDialog(DIALOG_COORDINATE_VERIFICATION_ID);
			return true;
		case R.id.new_from_file:
			newFromFile();
			return true;
		case R.id.new_from_camera:
		{
			SharedPreferences settings = getPreferences(MODE_PRIVATE);
			int cameraIDCounter = settings.getInt("cameraFileCounter", 1);
			SharedPreferences.Editor editor = settings.edit();
			editor.putInt("cameraFileCounter", cameraIDCounter + 1);
			editor.commit();
			String fileName = "camera_" + cameraIDCounter + ".jpg";
			m_SavableData.mapFile = new File(mPath.getAbsolutePath() + File.separatorChar + fileName);
						
			//In pre 2.0 there was a bug resulting in the image being 4 times smaller than it should have been
			//http://code.google.com/p/android/issues/detail?id=1480			
			//Comment 78  by russelljryan, Sep 15, 2010
			//johnyma22:
			//(I'm not a Google employee or contributor to Android)
			//Are all handsets running less than 1.6 troubled by this bug? YES
			//Are all handsets running v2+ free of this bug? SOME
			//The bug was fixed in plain vanilla Android 2.0 (e.g. on an N1).
			//Part of the confusion is that the HTC Sense UI and the Samsung and Motorola custom UIs re-introduce this bug because they provide their own custom Camera application, which often times does not even accept the EXTRA_OUTPUT intent, and if it does, returns a tiny image as in Android 1.5. 
			//As in Comment #64, barking at Google about this is pointless. The person you need to be hounding is the creator of the impaired UI that came with your phone. 
			//As an Android developer, this is easily the worst part of Android.
			
            Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(m_SavableData.mapFile));
            startActivityForResult(intent, TAKE_PICTURE);

			return true;
		}
		case R.id.information:
			showDialog(DIALOG_INFORMATION_ID);
			return true;
		case R.id.reference_points:
		{
			Intent intent = new Intent(this, CoordinateList.class);
			long iMapKey = -1;
			
			if (m_SavableData.mapFile != null)
			{
				database.open();
				iMapKey = database.getMapKey(m_SavableData.mapFile.getAbsolutePath());
				database.close();
			}
			
			intent.putExtra("MAP_KEY", iMapKey);
			startActivity(intent);
			return true;
		}
		case R.id.new_from_previous:
		{
			Intent intent = new Intent(this, MapList.class);			
			startActivityForResult(intent, SELECT_MAP);
			return true;
		}
		case R.id.preferences:
		{
			Intent intent = new Intent(this, MyPreferencesActivity.class);			
			startActivity(intent);
			return true;
		}
		
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case DIALOG_COORDINATE_VERIFICATION_ID:
		{
			dialog = new Dialog(this);
			dialog.setContentView(R.layout.verify_coordinate_dialog);
			dialog.setTitle("Calibration Point");

			// Map coordinates
			EditText mapX = (EditText) dialog.findViewById(R.id.mapX);
			EditText mapY = (EditText) dialog.findViewById(R.id.mapY);
			mapX.setEnabled(false); // TODO:Move to xml
			mapY.setEnabled(false); // TODO:Move to xml
			mapX.setFocusable(false); // TODO:Move to xml
			mapY.setFocusable(false); // TODO:Move to xml

			final Button button = (Button) dialog.findViewById(R.id.button);
			button.setOnClickListener(new SaveReferencePointClickListener());
		}
			break;
		case DIALOG_INFORMATION_ID:
		{
			AlertDialog.Builder builder = new Builder(this);
			builder.setTitle("Information");			
			builder.setMessage(Html.fromHtml(getString(R.string.str_menu_information_3_points)))
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				                dialog.dismiss();
				           }
				       });			
			dialog = builder.show();
		}			
		break;
		case DIALOG_CONVERSION_ERROR:
		{
			AlertDialog.Builder builder = new Builder(this);
			builder.setTitle("Conversion Error");			
			builder.setMessage(Html.fromHtml(getString(R.string.str_gps_conversion_error)))
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				                dialog.dismiss();
				           }
				       });			
			dialog = builder.show();
		}
		break;
		case DIALOG_RESTORE_REFERENCE_POINTS:
		{
			AlertDialog.Builder builder = new Builder(this);
			builder.setTitle("Restore Reference Points");			
			builder.setMessage(Html.fromHtml(getString(R.string.str_restore_reference_points)))
			 .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss(); //TODO:Load the reference points and possibly calibrate the map.
					loadAndUseOldReferencePoints();					
				}
			 })
			 .setNegativeButton("No", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					deleteOldReferencePoints();
				}
			 });
			dialog = builder.show();
		}
		break;
		case DIALOG_LOAD_FILE: //TODO:Remove, not used
		{
			loadFileList();
			AlertDialog.Builder builder = new Builder(this);
			builder.setTitle("Choose your map file");
			if(mFileList == null){
				//Log.e(TAG, "Showing file picker before loading the file list");
			    dialog = builder.create();
			    return dialog;
			}
			builder.setItems(mFileList, new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int which){
					mChosenFile = mFileList[which];					
					File file = new File(mPath.getAbsolutePath() + File.separatorChar + mChosenFile);
					if (file.isDirectory())
					{
						// TODO: Show files in the directory somehow.
					} else
					{					
						resetForNewMap();						
					}
			    }
			});
			dialog = builder.show();
		}
		break;
		case DIALOG_MAP_IS_CALIBRATED:
		{
			AlertDialog.Builder builder = new Builder(this);
			builder.setTitle("Successfully Calibrated");			
			builder.setMessage(Html.fromHtml(getString(R.string.str_successfylly_calibrated)))
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				                dialog.dismiss();
				           }
				       });			
			dialog = builder.show();
		}
		break;
		case DIALOG_MAP_FAILED_TO_CALIBRATE:
		{
			AlertDialog.Builder builder = new Builder(this);
			builder.setTitle("Calibration Failed");			
			builder.setMessage(Html.fromHtml(getString(R.string.str_failed_to_calibrate)))
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				                dialog.dismiss();
				           }
				       });			
			dialog = builder.show();
		}
		break;		
		
		default:
			dialog = null;
		}
		return dialog;
	}
		
	protected void  onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		
		switch (id) {
		case DIALOG_COORDINATE_VERIFICATION_ID:						
			// GPS coordinates
			EditText gpsLatitude = (EditText) dialog.findViewById(R.id.gpsLatitude);
			EditText gpsLongitude = (EditText) dialog.findViewById(R.id.gpsLongitude);
			String strLatitude;
			String strLongitude;
			Location gpsLocation = locationListener.getLastLocation();
			if (gpsLocation != null) {
				strLatitude = Location.convert(gpsLocation.getLatitude(), Location.FORMAT_MINUTES);
				strLongitude = Location.convert(gpsLocation.getLongitude(), Location.FORMAT_MINUTES);
				TextView gps_accuracy = (TextView) dialog.findViewById(R.id.gps_accuracy);				
				gps_accuracy.setText("Accuracy: ±" + Integer.toString((int) gpsLocation.getAccuracy()) + "m");
				if (gpsLocation.getAccuracy() > 10)
					gps_accuracy.setError("The accuracy of the measurement is very low.");
				
				TextView location_age = (TextView) dialog.findViewById(R.id.location_age);
				Time now = new Time();
				now.setToNow();
				int ageInSeconds = (int) (now.toMillis(true) - gpsLocation.getTime()) / 1000;
				location_age.setText("Age: " + Integer.toString(ageInSeconds) + " s");
				if (ageInSeconds >= 5)
					location_age.setError("The measurement value is very old.");
			} else {
				strLatitude = "Unknown";
				strLongitude = "Unknown";
			}
			gpsLatitude.setText(strLatitude, TextView.BufferType.EDITABLE);
			gpsLongitude.setText(strLongitude, TextView.BufferType.EDITABLE);

			// Map coordinates
			PointF mapCoordinates = m_SavableData.mapView.getCalibrationMapCoordinates();
			EditText mapX = (EditText) dialog.findViewById(R.id.mapX);
			EditText mapY = (EditText) dialog.findViewById(R.id.mapY);
			mapX.setText(Float.toString(mapCoordinates.x),
					TextView.BufferType.NORMAL);
			mapY.setText(Float.toString(mapCoordinates.y),
					TextView.BufferType.NORMAL);
			break;
		}
	}	
	
	private class SaveReferencePointClickListener implements View.OnClickListener {
		public void onClick(View v) {
			boolean conversionError = true;
			switch (v.getId()) {
			case R.id.button:
				//Get possibly updated coordinates from the dialog.
				View parent = (View) v.getParent();
				EditText gpsLatitude = (EditText) parent.findViewById(R.id.gpsLatitude);
				EditText gpsLongitude = (EditText) parent.findViewById(R.id.gpsLongitude);
				String strLatitude = gpsLatitude.getText().toString();
				String strLongitude = gpsLongitude.getText().toString();
				// The "." may in some locales be replaced with a "," and that is not handled well by android
				// when converting back again. http://code.google.com/p/android/issues/detail?id=5734
				strLatitude = strLatitude.replace(',', '.');
				strLongitude = strLongitude.replace(',', '.');
				try
				{
					double dLatitude = Location.convert(strLatitude);
					double dLongitude = Location.convert(strLongitude);
					// If the coordinates are badly formatted, we get an exception in the convert above.
					
					conversionError = false;
					
					// If everything went well, we hide the calibration point
					m_SavableData.mapView.hideCalibrationPoint();
					m_SavableData.m_bIsCalibrating = false;
										
					//save the point and possibly calculate a new matrix
					CoordinateMapping coordinate = new CoordinateMapping(
							m_SavableData.mapView.getCalibrationMapCoordinates(),
							new PointF((float)dLatitude, (float)dLongitude));
					m_SavableData.coordinateMappingList.add(coordinate);
					
					database.open();
					database.insertReferencePoint(m_SavableData.m_iMapKey, coordinate);
					database.close();
					
					tryToCalibrateMap();
				} catch (IllegalArgumentException e)
				{
					//Log.e(TAG, "unable to convert gps coordinates " + e.toString());
					conversionError = true;
				}				
				break;
			default:
				break;
			}
			dismissDialog(DIALOG_COORDINATE_VERIFICATION_ID);
			if (conversionError == true)
				showDialog(DIALOG_CONVERSION_ERROR);
		}
	}
	
	/*
	 * Tries to calibrate the loaded map using the 3 first stored coordinates.
	 */
	private void tryToCalibrateMap() {
		if (m_SavableData.coordinateMappingList.size() < 3)
			return;
		
		//GPS stuff
		int nbr = m_SavableData.coordinateMappingList.size();
		int MAX = 3; // It does not seem to work with 4 coordinates when we try to map. The mappings get totally wrong.
		nbr = (nbr > MAX ? MAX : nbr); 					// We only handle a maximum of MAX coordinates
		Matrix mapMatrix = new Matrix();
		boolean result = false;
		
		int iSampleSize = m_SavableData.mapView.getMapSampleSize();
		
		float[] gpsCoordinates = new float[nbr * 2];
		float[] mapCoordinates = new float[nbr * 2];
		ListIterator<CoordinateMapping> iter = m_SavableData.coordinateMappingList.listIterator();
		int index = 0;
		while (iter.hasNext() && iter.nextIndex() < MAX)
		{
			CoordinateMapping temp = iter.next(); 
			gpsCoordinates[index] = temp.gpsCoordinate.x;
			gpsCoordinates[index + 1] = temp.gpsCoordinate.y;
			mapCoordinates[index] = temp.mapCoordinate.x / iSampleSize;
			mapCoordinates[index + 1] = temp.mapCoordinate.y / iSampleSize;														
			index += 2;
		}									

		result = mapMatrix.setPolyToPoly(gpsCoordinates, 0, mapCoordinates, 0, nbr);
				
		if (result) {
			m_SavableData.mapView.setMapMatrix(mapMatrix);
			showDialog(DIALOG_MAP_IS_CALIBRATED);
		}
		else
		{
			showDialog(DIALOG_MAP_FAILED_TO_CALIBRATE);
		}
	}
		
	private String[] mFileList;	
	private File mPath = new File(Environment.getExternalStorageDirectory() + "//MapCalibrator//");
	private String mChosenFile;	
	private static final int DIALOG_LOAD_FILE = 1000;
	
	private void loadFileList(){
	  try{
	     mPath.mkdirs();
	  }
	  catch(SecurityException e){
	     //Log.e(TAG, "unable to write on the sd card " + e.toString());		  
	  }
	  if(mPath.exists()){
	     FilenameFilter filter = new FilenameFilter(){
	         public boolean accept(File dir, String filename){
	        	 //File sel = new File(dir, filename);
	             return filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg");// || sel.isDirectory();
	         }
	     };
	     mFileList = mPath.list(filter);
	  }
	  else{
	    mFileList= new String[0];
	  }
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TAKE_PICTURE && resultCode == Activity.RESULT_OK){
        	resetForNewMap();
        } else if (requestCode == SELECT_PICTURE && resultCode == Activity.RESULT_OK) {
        	Uri selectedImage = data.getData();
        	
        	// Uri:s should be of type "content://" according to the documentation for ACTION_GET_CONTENT
        	// The Astro file manager returns an Uri of Type "file://" so we must handle that as well.
        	if (selectedImage.toString().toLowerCase().startsWith("content://"))
        	{
        		String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                if (cursor != null) {
                	cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String filePath = cursor.getString(columnIndex); // file path of selected image
                    cursor.close();
                    
                    m_SavableData.mapFile = new File(filePath);
                	
                	resetForNewMap();
                }
        	} else if (selectedImage.toString().toLowerCase().startsWith("file://")) {                
            	// Handle stuff from the ASTRO file manager
            	String filePath = selectedImage.getPath();
            	m_SavableData.mapFile = new File(filePath);            	
            	resetForNewMap();
            } else {
            	// TODO: Well, what do we do?
            	//Show a dialog perhaps?
            }
        } else if (requestCode == SELECT_MAP && resultCode == Activity.RESULT_OK)
        {
        	String filePath = data.getExtras().getString("MAP_FILE_PATH");
        	m_SavableData.mapFile = new File(filePath);
        	resetForNewMap();
        }
    }
}

class CoordinateMapping implements Parcelable{
	public PointF mapCoordinate;
	public PointF gpsCoordinate; // This is the final gps coordinate after possible change and verification by the user
	
	public int describeContents() {
        return 0;
    }

	public void writeToParcel(Parcel out, int flags) {
        out.writeFloatArray(new float[]{mapCoordinate.x, mapCoordinate.y});
        out.writeFloatArray(new float[]{gpsCoordinate.x, gpsCoordinate.x});
    }
	
	public static final Parcelable.Creator<CoordinateMapping> CREATOR
		= new Parcelable.Creator<CoordinateMapping>() {
		public CoordinateMapping createFromParcel(Parcel in) {
			return new CoordinateMapping(in);
		}

		public CoordinateMapping[] newArray(int size) {
			return new CoordinateMapping[size];
		}
	};

	private CoordinateMapping(Parcel in) {
		float[] fTemp = new float[2];
		in.readFloatArray(fTemp);
		this.mapCoordinate = new PointF(fTemp[0], fTemp[1]);
		
		fTemp = new float[2];
		in.readFloatArray(fTemp);
		this.gpsCoordinate = new PointF(fTemp[0], fTemp[1]);
	}

	
	public CoordinateMapping(PointF mapCoordinate, PointF gpsCoordinate) {
		this.mapCoordinate = mapCoordinate;		
		this.gpsCoordinate = gpsCoordinate;
	}	
}

class SaveData implements Parcelable {
    public SaveData() {}
    
    MyDrawableImageView mapView; //Always != null created in the Activity
    mapSaveData mapViewSaveData; // Used to restore the mapView after pausing
	ArrayList<CoordinateMapping> coordinateMappingList = new ArrayList<CoordinateMapping>();
	File mapFile;
	boolean m_bMapIsLoaded = false;
	boolean m_bIsCalibrating = false;
	boolean m_bIsTrackingPosition = false;
	long m_iMapKey = -1; //Key in the database for the current map.
		
	public int describeContents() {
        return 0;
    }

	public void writeToParcel(Parcel out, int flags) {
		CoordinateMapping[] temp = new CoordinateMapping[coordinateMappingList.size()];
		temp = coordinateMappingList.toArray(temp);
		out.writeParcelableArray(temp, flags);
			
		out.writeString(mapFile == null ? null : mapFile.toString());
		out.writeBooleanArray(new boolean[]{m_bMapIsLoaded, m_bIsCalibrating, m_bIsTrackingPosition});
		
		out.writeLong(m_iMapKey);
		
		out.writeParcelable(mapView.getSaveableData(),flags);		
    }
	
	public static final Parcelable.Creator<SaveData> CREATOR
		= new Parcelable.Creator<SaveData>() {
		public SaveData createFromParcel(Parcel in) {
			return new SaveData(in);
		}

		public SaveData[] newArray(int size) {
			return new SaveData[size];
		}
	};

	private SaveData(Parcel in) {
		// In Java it is illegal to cast an array of a supertype into a subtype. That will throw an
		// java.lang.ClassCastException
		Parcelable[] temp;
		temp = in.readParcelableArray(CoordinateMapping.class.getClassLoader());
		for (int i=0; i<temp.length; i++)		
			this.coordinateMappingList.add((CoordinateMapping) temp[i]);
		
		String tempString = in.readString();
		if (tempString != null)
		{
			this.mapFile = new File(tempString);			
		}
		
		boolean bTemp[] = new boolean[3];
		in.readBooleanArray(bTemp);
		this.m_bMapIsLoaded = bTemp[0];
		this.m_bIsCalibrating = bTemp[1];
		this.m_bIsTrackingPosition = bTemp[2];
		
		this.m_iMapKey = in.readLong();
		
		mapViewSaveData = in.readParcelable(mapSaveData.class.getClassLoader());
	}
}