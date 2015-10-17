package com.Martin.MapCalibrator;

import com.Martin.MapCalibrator.fragments.MapListFragment.OnMapSelectedListener;
import com.Martin.MapCalibrator.misc.Util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;

public class MapListActivity extends FragmentActivity implements OnMapSelectedListener{
	
	private static final int ACTIVITY_REQUEST_CODE_LOAD_MAP = 3;
    private static final int DIALOG_INFORMATION_ID 			  = 1;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_map_list);

        // Create our folder if it is not already created
        if(!Util.mPath.exists()){
            try{
                Util.mPath.mkdirs();
            }
            catch(SecurityException e){
                //Log.e(TAG, "unable to write on the sd card " + e.toString());
            }
        }

        if (Util.isNewVersion(this))
            showDialog(DIALOG_INFORMATION_ID);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch (id) {
            case DIALOG_INFORMATION_ID:
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
            default:
                dialog = null;
        }

        return dialog;
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

            Intent intent = new Intent(this, MapCalibrator.class);
    		intent.putExtra(MapCalibrator.MAP_FILE_PATH, mapFilePath);
            startActivity(intent);
        } else {
        	super.onActivityResult(requestCode, resultCode, data);
        }
    }

}
