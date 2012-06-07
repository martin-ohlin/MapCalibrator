package com.Martin.MapCalibrator;

import java.io.File;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
import android.text.Html;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class MapList extends ListActivity {

	private DBAdapter mDbHelper;
	private static final int OPEN_MAP_ID = 1;
	private static final int DIALOG_MAP_DOES_NOT_EXIST	= 0;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.map_list);
		registerForContextMenu(getListView());
		mDbHelper = new DBAdapter(this);
		mDbHelper.open();
		fillData();
	}

	@Override
	public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
	    super.onCreateContextMenu(menu, v, menuInfo);
	    menu.add(0, OPEN_MAP_ID, 0, R.string.str_open_map);
	}

	@Override
	protected void onListItemClick (ListView l, View v, int position, long id)
	{
		super.onListItemClick(l, v, position, id);
		
		// The list is fed from a cursor, so the id above is the id in the cursor
		openMap(id);
	}
	
	private void openMap(long id)
	{
		String mapPath = mDbHelper.getMapPath(id);
		//fillData();

		File temp = new File(mapPath);
		if (temp.exists())
		{
			Intent intent = new Intent();			
			intent.putExtra("MAP_FILE_PATH", mapPath);
			setResult(RESULT_OK, intent);
			finish();			
		}
		else
		{
			showDialog(DIALOG_MAP_DOES_NOT_EXIST);
		}
	}
	
	@Override
	public boolean onContextItemSelected (MenuItem item) {
		switch (item.getItemId()) {
		case OPEN_MAP_ID:
		{			
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();			
			openMap(info.id);			
			return true;			
		}			
		default:
			return super.onContextItemSelected(item);
		}
	}
	
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {		
		case DIALOG_MAP_DOES_NOT_EXIST:
		{
			AlertDialog.Builder builder = new Builder(this);
			builder.setTitle("File does not exist");			
			builder.setMessage(Html.fromHtml("The chosen map does not exist on disk anymore."))
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
	
	
	private void fillData() {
		Cursor c = mDbHelper.getAllMaps();
		startManagingCursor(c);

		String[] from = new String[] {DBAdapter.KEY_MAP_FILENAME, DBAdapter.KEY_MAP_FILEPATH, "nbr_of_points"};
		int[] to = new int[] {R.id.map_list_file_name, R.id.map_list_file_path, R.id.map_list_nbr_of_points};

		if (c != null) {
			if (c.moveToFirst()) {
				// Now create an array adapter and set it to display using our row
				SimpleCursorAdapter maps = new MySimpleCursorAdapter(this,
						R.layout.map_row, c, from, to);
				setListAdapter(maps);
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);		
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	class MySimpleCursorAdapter extends SimpleCursorAdapter {

		public MySimpleCursorAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to) {
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