package com.Martin.MapCalibrator;

import android.app.ListActivity;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class CoordinateList extends ListActivity {

	private DBAdapter mDbHelper;
	private long m_iMapKey;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null)
			m_iMapKey = (Long) savedInstanceState.getSerializable("MAP_KEY");
		else {
			Bundle extras = getIntent().getExtras();
			m_iMapKey = extras.getLong("MAP_KEY");
		}

		setContentView(R.layout.coordinate_list);
		mDbHelper = new DBAdapter(this);
		mDbHelper.open();
		fillData();
	}

	/*
	 * @Override public boolean onCreateOptionsMenu(Menu menu) { boolean result
	 * = super.onCreateOptionsMenu(menu); menu.add(0, INSERT_ID, 0,
	 * R.string.menu_insert); return result; }
	 * 
	 * @Override public boolean onOptionsItemSelected(MenuItem item) { switch
	 * (item.getItemId()) {
	 * 
	 * case INSERT_ID: createNote(); return true; } return
	 * super.onOptionsItemSelected(item); }
	 * 
	 * private void createNote() { String noteName = "Note " + mNoteNumber++;
	 * mDbHelper.createNote(noteName, ""); fillData(); }
	 */
	private void fillData() {
		// Get all of the notes from the database and create the item list
		Cursor c = mDbHelper.getAllReferencePoints(m_iMapKey);
		startManagingCursor(c);

		String[] from = new String[] {
				"_id", // Renamed
				DBAdapter.KEY_POINTS_LATITUDE, DBAdapter.KEY_POINTS_LONGITUDE,
				DBAdapter.KEY_POINTS_MAPX, DBAdapter.KEY_POINTS_MAPY };
		int[] to = new int[] { R.id.row_id, R.id.latitude, R.id.longitude,
				R.id.mapx, R.id.mapy };

		if (c != null) {
			if (c.moveToFirst()) {
				// Now create an array adapter and set it to display using our
				// row
				SimpleCursorAdapter notes = new MySimpleCursorAdapter(this,
						R.layout.coordinates_row, c, from, to);
				setListAdapter(notes);
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putSerializable("MAP_KEY", m_iMapKey);
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
