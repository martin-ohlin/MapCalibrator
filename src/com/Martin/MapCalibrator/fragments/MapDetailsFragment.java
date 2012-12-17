package com.Martin.MapCalibrator.fragments;

import java.io.File;

import android.support.v4.app.Fragment;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import com.Martin.MapCalibrator.R;
import com.Martin.MapCalibrator.misc.DBAdapter;
import com.Martin.MapCalibrator.misc.DBAdapter.MapData;

public class MapDetailsFragment extends Fragment {

	private static String MAP_ID = "MAP_ID";
	
	private OnLoadMapListener mOnLoadMapListener;	

	private long mID;
	
	/* Must be implemented by host activity */
    public interface OnLoadMapListener {
        public void onLoadMapSelected(String mapFilePath);
    }
    
    public void onCreate (Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	// Using this, our fragment will be reused if we change the orientation of the device
    	setRetainInstance(true);
    }
        
	@Override
	public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.map_details_fragment, container);
	
		if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
			mID = savedInstanceState.getLong(MAP_ID);
        }
		
		// Add a listener to save changes to the comment field
		((EditText) view.findViewById(R.id.map_details_comment)).addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				DBAdapter dbHelper = new DBAdapter(getActivity());
				dbHelper.open();
				dbHelper.updateMapComment(mID, s.toString());					
				dbHelper.close();							
			}
		});
		
		// Add a click listener to the load button
		Button button = (Button) view.findViewById(R.id.map_details_openMapButton);
		button.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				DBAdapter dbHelper = new DBAdapter(getActivity());
				dbHelper.open();
				String filePath = dbHelper.getMapPath(mID);
				dbHelper.close();
				
				File mapFile = new File(filePath);
				if (mapFile.exists())
				{
					mOnLoadMapListener.onLoadMapSelected(filePath);
				}
				else
				{
					Toast.makeText(getActivity(),
							"It looks as if the file you selected does not exist.", Toast.LENGTH_LONG)
							.show();
				}
			}
		});
		
		return view;
	}
	
	@Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        outState.putLong(MAP_ID, mID);        
    }
		
	public void changeMap(long id) {
		this.mID = id;
		
		DBAdapter dbHelper = new DBAdapter(getActivity());
		dbHelper.open();
		MapData mapData = dbHelper.getMapData(mID);
		dbHelper.close();
		
		View view = this.getView();
		((TextView) view.findViewById(R.id.map_details_file_name)).setText(mapData.fileName);
		((TextView) view.findViewById(R.id.map_details_file_path)).setText(mapData.filePath);
		((EditText) view.findViewById(R.id.map_details_comment)).setText(mapData.comment);
		
		File mapFile = new File(mapData.filePath);
		if (mapFile.exists())
		{	
			Resources r = getResources();
			// 300dp is specified in the xml
			float imageViewHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, r.getDisplayMetrics());
			
			BitmapFactory.Options options = new BitmapFactory.Options();			
			options.inJustDecodeBounds = true;			
			options.inSampleSize = 1;
			BitmapFactory.decodeFile(mapFile.getAbsolutePath(), options);
			
			while (options.outHeight > imageViewHeight) {
				options.inSampleSize *= 2;
				BitmapFactory.decodeFile(mapFile.getAbsolutePath(), options);
			}
			
			options.inJustDecodeBounds = false;
			Bitmap bitmap = BitmapFactory.decodeFile(mapFile.getAbsolutePath(), options);
			
			ImageView imageView = (ImageView) view.findViewById(R.id.map_details_map_view);
			imageView.setImageBitmap(bitmap);
		}
		else
		{
			// TODO: Add a text that the file is missing.
		}
	}
	
	@Override
	public void onAttach (Activity activity) {
		super.onAttach(activity);
		
		mOnLoadMapListener = (OnLoadMapListener) activity;
	}
}
