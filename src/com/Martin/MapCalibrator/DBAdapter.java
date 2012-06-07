package com.Martin.MapCalibrator;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.PointF;

/**
 * @author mad
 *
 */
/**
 * @author mad
 * 
 */
public class DBAdapter {
	private static final String DATABASE_NAME = "maps_database";

	private static final String DATABASE_TABLE_1 = "maps";
	private static final String DATABASE_TABLE_2 = "referencepoints";

	public static final String KEY_MAP_ROWID = "_mapid";
	public static final String KEY_MAP_FILENAME = "filename";
	public static final String KEY_MAP_FILEPATH = "filepath";
	public static final String KEY_POINTS_ROWID = "_pointid";
	public static final String KEY_POINTS_MAPKEY = "mapkey";
	public static final String KEY_POINTS_LATITUDE = "latitude";
	public static final String KEY_POINTS_LONGITUDE = "longitude";
	public static final String KEY_POINTS_MAPX = "mapX";
	public static final String KEY_POINTS_MAPY = "mapY";

	private static final int DATABASE_VERSION = 1;

	private static final String DATABASE_CREATE_TABLE_1 = "create table maps ("
			+ KEY_MAP_ROWID + " integer primary key autoincrement,"
			+ "filename text not null," + "filepath text not null);";

	private static final String DATABASE_CREATE_TABLE_2 = "create table referencepoints ("
			+ KEY_POINTS_ROWID
			+ " integer primary key autoincrement, "
			+ "mapkey integer,"
			+ "latitude real,"
			+ "longitude real,"
			+ "mapX real," + "mapY real);";

	private final Context context;

	private DatabaseHelper DBHelper;
	private SQLiteDatabase db;

	public DBAdapter(Context context) {
		this.context = context;
		DBHelper = new DatabaseHelper(context);
	}

	private static class DatabaseHelper extends SQLiteOpenHelper {
		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DATABASE_CREATE_TABLE_1);
			db.execSQL(DATABASE_CREATE_TABLE_2);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// No upgrade yet.
		}
	}

	// ---opens the database---
	public DBAdapter open() throws SQLException {
		db = DBHelper.getWritableDatabase();
		return this;
	}

	/**
	 * Closes the database.
	 */
	public void close() {
		DBHelper.close();
	}

	/**
	 * Inserts a map into the database
	 * 
	 * @param file
	 *            Name of the file on disk containing the map.
	 * @param filePath
	 *            Full path to the map file on the disk.
	 * @return The row ID of the newly inserted row, or -1 if an error occurred.
	 */
	public long insertMap(String file, String filePath) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_MAP_FILENAME, file);
		initialValues.put(KEY_MAP_FILEPATH, filePath);
		return db.insert(DATABASE_TABLE_1, null, initialValues);
	}

	/**
	 * Insert a reference point into the database
	 * 
	 * @param mapkey
	 * @param latitude
	 * @param longitude
	 * @param mapX
	 * @param mapY
	 * @return
	 */
	private long insertReferencePoint(long mapkey, float latitude,
			float longitude, float mapX, float mapY) {
		ContentValues initialValues = new ContentValues();
		initialValues.put(KEY_POINTS_MAPKEY, mapkey);
		initialValues.put(KEY_POINTS_LATITUDE, latitude);
		initialValues.put(KEY_POINTS_LONGITUDE, longitude);
		initialValues.put(KEY_POINTS_MAPX, mapX);
		initialValues.put(KEY_POINTS_MAPY, mapY);
		return db.insert(DATABASE_TABLE_2, null, initialValues);
	}

	/**
	 * @param mapkey
	 * @param point
	 * @return
	 */
	public long insertReferencePoint(long mapkey, CoordinateMapping point) {
		return insertReferencePoint(mapkey, point.gpsCoordinate.x,
				point.gpsCoordinate.y, point.mapCoordinate.x,
				point.mapCoordinate.y);
	}

	/**
	 * Deletes all reference points connected to a specific map.
	 * 
	 * @param mapkey
	 *            The row id of the map.
	 */
	public void deleteReferencePoints(long mapkey) {
		db.delete(DATABASE_TABLE_2, KEY_POINTS_MAPKEY + "=" + mapkey, null);
	}

	// ---retrieves all reference points for a given map
	public ArrayList<CoordinateMapping> getAllReferencePointsForMap(long mapkey) {
		Cursor referencePointsCursor = db.query(DATABASE_TABLE_2, new String[] {
				KEY_POINTS_LATITUDE, KEY_POINTS_LONGITUDE, KEY_POINTS_MAPX,
				KEY_POINTS_MAPY }, KEY_POINTS_MAPKEY + "=" + mapkey, null,
				null, null, null, null);

		ArrayList<CoordinateMapping> referencePoints = new ArrayList<CoordinateMapping>();

		if (referencePointsCursor != null) {
			boolean rowsLeft = referencePointsCursor.moveToFirst();

			while (rowsLeft) {
				referencePoints.add(new CoordinateMapping(new PointF(
						referencePointsCursor.getFloat(referencePointsCursor
								.getColumnIndex(KEY_POINTS_MAPX)),
						referencePointsCursor.getFloat(referencePointsCursor
								.getColumnIndex(KEY_POINTS_MAPY))), new PointF(
						referencePointsCursor.getFloat(referencePointsCursor
								.getColumnIndex(KEY_POINTS_LATITUDE)),
						referencePointsCursor.getFloat(referencePointsCursor
								.getColumnIndex(KEY_POINTS_LONGITUDE)))));

				rowsLeft = referencePointsCursor.moveToNext();
			}
			referencePointsCursor.close();
		}

		return referencePoints;
	}

	/**
	 * Return a Cursor over the list of all reference points in the database
	 * 
	 * @return Cursor over all reference points
	 */
	public Cursor getAllReferencePoints(long mapkey) {
		// Tables need to have a column called _id for CursorAdapter to work. Therefore I need
		// a raw sql call instead of the convenience functions.
		
		/*
		 * return db.query(DATABASE_TABLE_2, new String[] {KEY_POINTS_ROWID,
		 * KEY_POINTS_MAPKEY, KEY_POINTS_LATITUDE, KEY_POINTS_LONGITUDE,
		 * KEY_POINTS_MAPX, KEY_POINTS_MAPY}, KEY_POINTS_MAPKEY + "=" + mapkey,
		 * null, null, null, null, null);
		 */
		/*
		String[] columns = new String[] {
        		KEY_POINTS_LATITUDE,
        		KEY_POINTS_LONGITUDE,
        		KEY_POINTS_MAPX,
        		KEY_POINTS_MAPY};
		*/
		String query = "SELECT " + KEY_POINTS_ROWID + " as _id, " +
				KEY_POINTS_LATITUDE  + ", " +
				KEY_POINTS_LONGITUDE + ", " +
				KEY_POINTS_MAPX +      ", " +
				KEY_POINTS_MAPY +
				" FROM " + DATABASE_TABLE_2 + " WHERE " + KEY_POINTS_MAPKEY + "=" + mapkey;
		return db.rawQuery(query, null);
	}

	public boolean mapHasReferencePoints(long mapkey) {
		Cursor referencePointsCursor = db.query(DATABASE_TABLE_2, new String[] {
				KEY_POINTS_LATITUDE, KEY_POINTS_LONGITUDE, KEY_POINTS_MAPX,
				KEY_POINTS_MAPY }, KEY_POINTS_MAPKEY + "=" + mapkey, null,
				null, null, null, null);

		boolean rowsLeft = false;
		if (referencePointsCursor != null) {
			rowsLeft = referencePointsCursor.moveToFirst();
			referencePointsCursor.close();
		}

		return rowsLeft;
	}

	/**
	 * 
	 * @return A cursor containing all maps in the database.
	 */
	public Cursor getAllMaps() {
		/*
		String query = "SELECT " + KEY_MAP_ROWID + " as _id, " +
				KEY_MAP_FILENAME  + ", " +
				KEY_MAP_FILEPATH +
				" FROM " + DATABASE_TABLE_1;
			*/	
		
		String query = "SELECT " + KEY_MAP_ROWID + " as _id, " +
				KEY_MAP_FILENAME  + ", " +
				KEY_MAP_FILEPATH + ", " +
				"(SELECT COUNT("+ KEY_POINTS_MAPKEY + ") FROM " 
				+ DATABASE_TABLE_2 + " WHERE " + KEY_POINTS_MAPKEY + "=" + KEY_MAP_ROWID + ") as nbr_of_points" +
				" FROM " + DATABASE_TABLE_1;
				
		
		/*
		String query = "SELECT " + KEY_MAP_ROWID + " as _id, " +
				KEY_MAP_FILENAME  + ", " +
				KEY_MAP_FILEPATH + "," +
				"COUNT( DISTINCT " + KEY_POINTS_MAPKEY + ") as nbr_of_points" +
				" FROM " + DATABASE_TABLE_1 + " LEFT JOIN " + DATABASE_TABLE_2;
		*/
		/*
		String query = "SELECT " + KEY_MAP_ROWID + " as _id, " +
				KEY_MAP_FILENAME  + ", " +
				KEY_MAP_FILEPATH +
				" FROM " + DATABASE_TABLE_1 + " LEFT OUTER JOIN " + DATABASE_TABLE_2;
		*/
		return db.rawQuery(query, null);
	}

	/**
	 * Gets the row id of a map if it exists.
	 * 
	 * @param filePath
	 *            Full file path for the map.
	 * @return The row ID of the map, or -1 if it is not present in the
	 *         database.
	 * @throws SQLException
	 */
	public long getMapKey(String filePath) throws SQLException {
		String where = KEY_MAP_FILEPATH + " like " + "\"" + filePath + "\"";
		Cursor mCursor = db.query(DATABASE_TABLE_1,
				new String[] { KEY_MAP_ROWID }, where, null, null, null, null);
		long rowid = -1;
		if (mCursor != null) {
			if (mCursor.moveToFirst()) {
				int columnIndex = mCursor.getColumnIndexOrThrow(KEY_MAP_ROWID);
				rowid = mCursor.getLong(columnIndex);
				mCursor.close();
			}
		}

		return rowid;
	}
	
	/**
	 * Gets the complete path of a map if it exists.
	 * 
	 * @param filePath
	 *            Full file path for the map.
	 * @return The complete path of the map, or "" if it is not present in the
	 *         database.
	 * @throws SQLException
	 */
	public String getMapPath(long mapkey) throws SQLException {
		String where = KEY_MAP_ROWID + " = " + mapkey;
		Cursor mCursor = db.query(DATABASE_TABLE_1,
				new String[] { KEY_MAP_FILEPATH }, where, null, null, null, null);
		String mapPath = "";
		if (mCursor != null) {
			if (mCursor.moveToFirst()) {
				int columnIndex = mCursor.getColumnIndexOrThrow(KEY_MAP_FILEPATH);
				mapPath = mCursor.getString(columnIndex);
				mCursor.close();
			}
		}

		return mapPath;
	}
}