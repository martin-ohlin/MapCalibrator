package com.Martin.MapCalibrator.fragments;

import java.io.File;

import com.Martin.MapCalibrator.R;
import com.Martin.MapCalibrator.misc.DBAdapter;
import com.Martin.MapCalibrator.misc.Util;
import com.Martin.MapCalibrator.misc.Util.OnFileFoundListener;

import android.app.Activity;
import android.support.v4.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class MapListFragment extends ListFragment {
	
	private static final int ACTIVITY_REQUEST_CODE_TAKE_PICTURE   = 1;
	private static final int ACTIVITY_REQUEST_CODE_SELECT_PICTURE = 2;
	
	private static String CAMERA_REQUEST_FILE = "CAMERA_REQUEST_FILE";
	
	private static File mPath = new File(Environment.getExternalStorageDirectory() + "//MapCalibrator//");
	
	private DBAdapter mDbHelper;
	private OnMapSelectedListener mOnMapSelectedListener;
	
	private File mActivityCameraRequestFile;
	
	/* Must be implemented by host activity */
    public interface OnMapSelectedListener {
        public void onMapSelected(long id);
    }
	
    
    public void onCreate (Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	setHasOptionsMenu(true);

    	// Using this, our fragment will be reused if we change the orientation of the device
    	setRetainInstance(true);
    }
    
	@Override
	public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.map_list_fragment, container);
		
		if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
			String temp = savedInstanceState.getString(CAMERA_REQUEST_FILE);
			if (temp != null)
				mActivityCameraRequestFile = new File(temp);
        }
		
		return view;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		mDbHelper = new DBAdapter(getActivity());
		fillData();
	}
	
	@Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        if (mActivityCameraRequestFile != null)
        	outState.putString(CAMERA_REQUEST_FILE, mActivityCameraRequestFile.getAbsolutePath());        
    }
	
	public void onCreateOptionsMenu (Menu menu, MenuInflater inflater) {    	
		inflater.inflate(R.menu.menu_map_list_fragment, menu);
    }
    
    public boolean onOptionsItemSelected (MenuItem item) {
    	// Handle item selection
		switch (item.getItemId()) {
		case R.id.menu_new_from_file:
			Intent fileIntent = new Intent();
			fileIntent.setType("image/*");
			fileIntent.setAction(Intent.ACTION_GET_CONTENT);
	        startActivityForResult(Intent.createChooser(fileIntent, "Select a Map"), ACTIVITY_REQUEST_CODE_SELECT_PICTURE);
			return true;
		case R.id.menu_new_from_camera: {
			SharedPreferences settings = this.getActivity().getPreferences(Context.MODE_PRIVATE);
			int cameraIDCounter = settings.getInt("cameraFileCounter", 1);
			SharedPreferences.Editor editor = settings.edit();
			editor.putInt("cameraFileCounter", cameraIDCounter + 1);
			editor.commit();
			String fileName = "camera_" + cameraIDCounter + ".jpg";

			Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
			mActivityCameraRequestFile =  new File(mPath.getAbsolutePath() + File.separatorChar + fileName);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mActivityCameraRequestFile));
			startActivityForResult(intent, ACTIVITY_REQUEST_CODE_TAKE_PICTURE);

			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
    }
        
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_REQUEST_CODE_TAKE_PICTURE && resultCode == Activity.RESULT_OK){
        	saveMapToDatabase(mActivityCameraRequestFile);
        } else if (requestCode == ACTIVITY_REQUEST_CODE_SELECT_PICTURE && resultCode == Activity.RESULT_OK) {
        	Util.handleSelectedFile(data, this.getActivity(), new OnFileFoundListener() {
				
				@Override
				public void OnFileFound(File file) {
					saveMapToDatabase(file);
				}
			});
        }
    }
    
    private void saveMapToDatabase(File file) {
		if (file.exists()) {
			mDbHelper.open();
			long lMapKey = mDbHelper.getMapKey(file.getAbsolutePath());
			
			if (lMapKey != -1) {
				Toast.makeText(this.getActivity(),
						"The selected map already exists in the database. It can not be added twice.", Toast.LENGTH_LONG)
						.show();
			} else {
				lMapKey = mDbHelper.insertMap(file.getName(), file.getAbsolutePath());
				Cursor cursor = mDbHelper.getAllMaps();
				if (this.getListAdapter() != null) {
					((SimpleCursorAdapter) this.getListAdapter()).changeCursor(cursor);
				} else {
					fillData();
				}
			}
			mDbHelper.close();
		} else {
			Toast.makeText(this.getActivity(),
					"It looks as if the file you selected does not exist.", Toast.LENGTH_LONG)
					.show();
		}
    }
	
	private void fillData() {
		mDbHelper.open();
		Cursor c = mDbHelper.getAllMaps();

		String[] from = new String[] {DBAdapter.KEY_MAP_FILENAME, DBAdapter.KEY_MAP_FILEPATH, DBAdapter.KEY_MAP_COMMENT, DBAdapter.KEY_NBR_OF_POINTS};
		int[] to = new int[] {R.id.map_list_file_name, R.id.map_list_file_path, R.id.map_list_file_comment ,R.id.map_list_nbr_of_points};

		if (c != null) {
			SimpleCursorAdapter maps = new MySimpleCursorAdapter(getActivity(), R.layout.map_row, c, from, to);
			setListAdapter(maps);
		}
		mDbHelper.close();
	}

	@Override
	public void onListItemClick (ListView l, View v, int position, long id) {
		// TODO: Change to database id
		mOnMapSelectedListener.onMapSelected(id);
	}
	
	@Override
	public void onAttach (Activity activity) {
		super.onAttach(activity);
		
		mOnMapSelectedListener = (OnMapSelectedListener) activity;
	}
	
	class MySimpleCursorAdapter extends SimpleCursorAdapter {

		public MySimpleCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
			super(context, layout, c, from, to);
		}

		public void setViewText(TextView v, String text) {
			if (v.getId() == R.id.latitude || v.getId() == R.id.longitude) {
				text = Location.convert(Double.parseDouble(text), Location.FORMAT_MINUTES);
			}
			v.setText(text);
		}
	}
	
}
