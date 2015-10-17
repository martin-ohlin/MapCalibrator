package com.Martin.MapCalibrator.misc;

import java.util.ArrayList;

import com.Martin.MapCalibrator.CoordinateMapping;

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

	private static final String KEY_MAP_ROWID = "_mapid";
	public static final String KEY_MAP_FILENAME = "filename";
	public static final String KEY_MAP_FILEPATH = "filepath";
	public static final String KEY_MAP_COMMENT = "comment";
	
	public static final String KEY_POINTS_ROWID = "_pointid";
	private static final String KEY_POINTS_MAPKEY = "mapkey";
	public static final String KEY_POINTS_LATITUDE = "latitude";
	public static final String KEY_POINTS_LONGITUDE = "longitude";
	public static final String KEY_POINTS_MAPX = "mapX";
	public static final String KEY_POINTS_MAPY = "mapY";
	
	public static final String KEY_NBR_OF_POINTS = "nbr_of_points";

	private static final int DATABASE_VERSION = 2; // Added a comment column in the maps table

	private static final String DATABASE_CREATE_TABLE_1 = "create table maps ("
			+ KEY_MAP_ROWID    + " integer primary key autoincrement,"
			+ KEY_MAP_FILENAME + " text not null,"
			+ KEY_MAP_FILEPATH + " text not null,"
			+ KEY_MAP_COMMENT  + " text);";

	private static final String DATABASE_CREATE_TABLE_2 = "create table referencepoints ("
			+ KEY_POINTS_ROWID     + " integer primary key autoincrement, "
			+ KEY_POINTS_MAPKEY    + " integer,"
			+ KEY_POINTS_LATITUDE  + " real,"
			+ KEY_POINTS_LONGITUDE + " real,"
			+ KEY_POINTS_MAPX      + " real,"
			+ KEY_POINTS_MAPY      + " real);";

	private DatabaseHelper DBHelper;
	private SQLiteDatabase db;

	public DBAdapter(Context context) {		
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
			// Add the comment column to the map table
		    if (oldVersion == 1 && newVersion == 2) {
		        db.execSQL("ALTER TABLE " + DATABASE_TABLE_1 + " ADD COLUMN " + KEY_MAP_COMMENT + " TEXT;");
		    	//db.execSQL("ALTER TABLE " + DATABASE_TABLE_1 + " DROP COLUMN " + KEY_MAP_COMMENT);
		    }
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
	 * Deletes a map and all reference points connected it.
	 * 
	 * @param mapKey
	 *            The row id of the map.
	 */
	public void deleteMap(long mapKey) {
		String whereClause = KEY_MAP_ROWID + "=?";
		String[] whereArgs = new String[] { Long.toString(mapKey) };
		db.delete(DATABASE_TABLE_1, whereClause, whereArgs);
		deleteReferencePoints(mapKey);
	}

	/**
	 * Insert a reference point into the database
	 * 
	 * @param mapkey
	 * @param latitude
	 * @param longitude
	 * @param mapX
	 * @param mapY
	 * @return the id of the inserted reference point.
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
	 * @return the id of the inserted reference point.
	 */
	public long insertReferencePoint(long mapkey, CoordinateMapping point) {
		return insertReferencePoint(mapkey, point.gpsCoordinate.x,
				point.gpsCoordinate.y, point.mapCoordinate.x,
				point.mapCoordinate.y);
	}

	/**
	 * Deletes all reference points connected to a specific map.
	 * 
	 * @param mapKey
	 *            The row id of the map.
	 */
	public void deleteReferencePoints(long mapKey) {
		String whereClause = KEY_POINTS_MAPKEY + "=?";
		String[] whereArgs = new String[] { Long.toString(mapKey) };
		db.delete(DATABASE_TABLE_2, whereClause, whereArgs);
	}
	
	/**
	 * Updates the comment for a map.
	 * 
	 * @param mapkey 
	 *         The row id of the map.
	 * @param comment
	 *         The comment to add to the map.
	 *  
	 * @return true if successful, false otherwise
	 */
	public boolean updateMapComment(long mapKey, String comment)
	{
		ContentValues updateValues = new ContentValues();
		updateValues.put(KEY_MAP_COMMENT, comment);
		String whereClause = KEY_MAP_ROWID + "=?";
		String[] whereArgs = new String[] { Long.toString(mapKey) };
		int affectedRows = db.update(DATABASE_TABLE_1, updateValues, whereClause, whereArgs);
		
		return affectedRows == 1;
	}
	
	/**
	 * Updates the file path for a map.
	 * 
	 * @param mapkey 
	 *         The row id of the map.
	 *
	 * @param file
	 *            Name of the file on disk containing the map.
	 * @param filePath
	 *            Full path to the map file on the disk.
	 *  
	 * @return true if successful, false otherwise
	 */
	public boolean updateMap(long mapKey, String file, String filePath)
	{
		ContentValues updateValues = new ContentValues();
		updateValues.put(KEY_MAP_FILENAME, file);
		updateValues.put(KEY_MAP_FILEPATH, filePath);
		String whereClause = KEY_MAP_ROWID + "=?";
		String[] whereArgs = new String[] { Long.toString(mapKey) };
		int affectedRows = db.update(DATABASE_TABLE_1, updateValues, whereClause, whereArgs);
		
		return affectedRows == 1;
	}
	
	/**
	 * Gets the comment for a map if it exists.
	 * 
	 * @param mapKey
	 *            The row id of the map.
	 * @return The comment for a map if it exists, otherwise "".
	 * @throws SQLException
	 */
	public String getMapComment(long mapKey) throws SQLException {
		String selection = KEY_MAP_ROWID + " =? ";
		String[] selectionArgs = new String[] { Long.toString(mapKey) };
		Cursor mCursor = db.query(DATABASE_TABLE_1,
				new String[] { KEY_MAP_COMMENT }, selection, selectionArgs, null, null, null);
		String mapComment = "";
		if (mCursor != null) {
			if (mCursor.moveToFirst()) {
				int columnIndex = mCursor.getColumnIndexOrThrow(KEY_MAP_COMMENT);
				mapComment = mCursor.getString(columnIndex);
				mCursor.close();
			}
		}

		return mapComment;
	}
	
	/**
	 * Gets the information connected to a map.
	 * 
	 * @param mapKey
	 *            The row id of the map.
	 * @return  if it exists, otherwise "".
	 * @throws SQLException
	 */
	public MapData getMapData(long mapKey) throws SQLException {
		String selection = KEY_MAP_ROWID + " =? ";
		String[] selectionArgs = new String[] { Long.toString(mapKey) };
		Cursor mCursor = db.query(DATABASE_TABLE_1,
				new String[] { KEY_MAP_FILENAME, KEY_MAP_FILEPATH, KEY_MAP_COMMENT }, selection, selectionArgs, null, null, null);
		
		MapData mapData = new MapData();
		if (mCursor != null) {
			if (mCursor.moveToFirst()) {
				mapData.fileName = mCursor.getString(mCursor.getColumnIndexOrThrow(KEY_MAP_FILENAME));
				mapData.filePath = mCursor.getString(mCursor.getColumnIndexOrThrow(KEY_MAP_FILEPATH));
				mapData.comment = mCursor.getString(mCursor.getColumnIndexOrThrow(KEY_MAP_COMMENT));
				mCursor.close();
			}
		}

		return mapData;
	}
	
	public class MapData {
		public String fileName;
		public String filePath;
		public String comment;
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
		
		String query = 
                "SELECT " + KEY_MAP_ROWID + " as _id, "
				+ KEY_MAP_FILENAME + ", "
                + KEY_MAP_FILEPATH + ", "
                + KEY_MAP_COMMENT + ", "
				+ "(SELECT COUNT(" + KEY_POINTS_MAPKEY + ") FROM "
				+ DATABASE_TABLE_2 + " WHERE " + KEY_POINTS_MAPKEY + "="
				+ KEY_MAP_ROWID + ") as " +KEY_NBR_OF_POINTS 
				+ " FROM " + DATABASE_TABLE_1;
		
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