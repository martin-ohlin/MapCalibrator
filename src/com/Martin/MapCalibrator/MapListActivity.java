package com.Martin.MapCalibrator;

import com.Martin.MapCalibrator.fragments.MapListFragment.OnMapSelectedListener;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class MapListActivity extends FragmentActivity implements OnMapSelectedListener{
	
	private static final int ACTIVITY_REQUEST_CODE_LOAD_MAP = 3;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_map_list);		
	}

	@Override
	public void onMapSelected(long id) {
		Intent intent = new Intent(this, MapDetailsActivity.class);
		intent.putExtra(MapDetailsActivity.MAP_ID, id);
		startActivityForResult(intent, ACTIVITY_REQUEST_CODE_LOAD_MAP);
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ACTIVITY_REQUEST_CODE_LOAD_MAP && resultCode == Activity.RESULT_OK){
        	String mapFilePath = data.getExtras().getString(MapCalibrator.MAP_FILE_PATH);
        	Intent intent = new Intent();
    		intent.putExtra(MapCalibrator.MAP_FILE_PATH, mapFilePath);
    		setResult(RESULT_OK, intent);
    		finish();
        } else {
        	super.onActivityResult(requestCode, resultCode, data);
        }
    }

}
