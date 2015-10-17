package com.Martin.MapCalibrator;

import com.Martin.MapCalibrator.fragments.MapDetailsFragment;
import com.Martin.MapCalibrator.fragments.MapDetailsFragment.OnLoadMapListener;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.MenuItem;

public class MapDetailsActivity extends FragmentActivity implements OnLoadMapListener {
	public static String MAP_ID = "map_id";
		
	private long mapID;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
				
		setContentView(R.layout.map_details_activity);
		
		mapID = getIntent().getExtras().getLong(MAP_ID);
		
		MapDetailsFragment mapDetailsFragment = (MapDetailsFragment) getSupportFragmentManager().findFragmentById(R.id.mapDetailsFragment);
		mapDetailsFragment.changeMap(mapID);

        getActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public void onLoadMapSelected(String mapFilePath) {
		Intent intent = new Intent();
		intent.putExtra(MapCalibrator.MAP_FILE_PATH, mapFilePath);
		setResult(RESULT_OK, intent);
		finish();
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
