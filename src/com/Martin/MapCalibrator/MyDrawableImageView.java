package com.Martin.MapCalibrator;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ZoomButtonsController;

/**
 * @author mad
 *
 */
public class MyDrawableImageView extends ImageView{
	// We can be in one of these 4 states
	static final int NONE = 0;
	static final int DRAG = 1;
	static final int ZOOM = 2;
	static final int MOVE = 3;
	
	private File m_mapFile;       // The file containing the map image
	private int  m_mapSampleSize; // The current sample size of the map image
	private Rect m_mapRect;       // The total rectangle of the map image on disk 
	private Rect m_mapRectRead;   // The current portion  of the map image read from the disk
	
	private mapSaveData m_SavableData = new mapSaveData();;

	private Drawable m_CalibrationDrawable;
	private Drawable m_PositionDrawable;
	
	// Remember some things for zooming
	private PointF start = new PointF();
	private PointF mid = new PointF();
	private float oldDist = 1f;
	
	private ZoomButtonsController zoomButtons;
	
	private Boolean largeMapSupport = false;
	
	protected mapSaveData getSaveableData() {
		return m_SavableData;
	}
	
	protected void setSaveableData(mapSaveData SaveableData) {
		this.m_SavableData = SaveableData;
	}
	
	public MyDrawableImageView(Context context) {
		super(context);
		
		if (this.isInEditMode())
			return;
			
		zoomButtons = new ZoomButtonsController(this);
		zoomButtons.setOnZoomListener(new MyZoomController(this));
		Initialize();
	}

	public MyDrawableImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		if (this.isInEditMode())
			return;
		
		zoomButtons = new ZoomButtonsController(this);
		zoomButtons.setOnZoomListener(new MyZoomController(this));
		Initialize();
	}

	private void Initialize() {
		this.setImageURI(null);
		
		// Work around a Cupcake bug
		m_SavableData.currentMapTileToDisplayMatrix.setTranslate(1f, 1f);
		m_SavableData.currentGlobalMapToDisplayMatrix.setTranslate(1f, 1f);
		
		this.setImageMatrix(m_SavableData.currentMapTileToDisplayMatrix);
		
		m_SavableData.isTrackingPosition = false;
		m_SavableData.isCalibrating      = false;
		
		m_CalibrationDrawable = null; // TODO: Do I really have to reset these?
		m_PositionDrawable    = null;
	}
	
	protected void setSupportLargeMaps(Boolean flag)
	{
		this.largeMapSupport = flag;
	}
	
	// Called from the gps
	protected void makeUseOfNewLocation(Location location) {
		m_SavableData.lastLocation = location;
		
		if (!m_SavableData.isTrackingPosition || m_SavableData.gpsToMapMapMatrix == null)
			return;
		
		if (m_SavableData.m_PositionMapPosition == null || m_PositionDrawable == null)
			createNewPositionPoint();
		
		float[] gpsCoordinate = {(float)location.getLatitude(), (float)location.getLongitude()};
		float[] mapCoordinate = new float[2];		
		m_SavableData.gpsToMapMapMatrix.mapPoints(mapCoordinate, gpsCoordinate);
		
		float newLeft = (float) (mapCoordinate[0] - m_SavableData.m_PositionMapPosition.width() / 2.0);
		float newTop = (float) (mapCoordinate[1] - m_SavableData.m_PositionMapPosition.height() / 2.0);
		m_SavableData.m_PositionMapPosition.offsetTo(newLeft, newTop); // Move to our new position
				
		RectF mapPositionRect = new RectF(m_SavableData.m_PositionMapPosition);		
		m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(mapPositionRect); // coordinates on our probably moved image
					
		// The coordinates may change, but we want to keep the shape in its original size
		float dx =  (float)((mapPositionRect.width() - m_SavableData.m_PositionMapPosition.width()) / 2.0);
		float dy = (float)((mapPositionRect.height() - m_SavableData.m_PositionMapPosition.height()) / 2.0);
		mapPositionRect.inset(dx, dy);
		Rect positionScreenPosition = new Rect();
		mapPositionRect.round(positionScreenPosition);
		m_PositionDrawable.setBounds(positionScreenPosition);
		
		//mDrawable.setBounds(19, 19, 200, 200);
		
		// Make the view redraw itself after we have changed our position on the map
		postInvalidate();
	}
	
	protected void setMapMatrix(Matrix mapMatrix) {
		m_SavableData.gpsToMapMapMatrix = mapMatrix;
		m_SavableData.isTrackingPosition = true;
		//TODO:For debugging only
		//Location temp = new Location(LocationManager.NETWORK_PROVIDER);
		//temp.setLatitude(56.90153);
		//temp.setLongitude(14.73982);
		//makeUseOfNewLocation(temp); //Draw out position on the map.
		if (m_SavableData.lastLocation != null)
			makeUseOfNewLocation(m_SavableData.lastLocation); //Draw out position on the map.
	}
	
	/*
	 * Resets the map calibration.
	 */
	protected void resetMapMatrix() {
		m_SavableData.gpsToMapMapMatrix = new Matrix(); //Needs to be something when we parcel it.
		m_SavableData.isTrackingPosition = false;
		// Make the view redraw itself so that a possible position point is removed
		postInvalidate();		
	}
	
	protected void setMap(File bitmapFile) {
		// TODO: If we are called here upon reawakening, then we might have to do som stuff in a different way
		// E.g., we shouldn't clear the image matrices
		
		// Remove old calibration points and generally reset everything.
		Initialize();
		
		// If we have previously loaded an image, then we should reset it in order
		// to minimize our memory consumption
		//http://stackoverflow.com/questions/6033645/mutithreaded-cached-imageview-loading
		//http://evancharlton.com/thoughts/lazy-loading-images-in-a-listview/
		//http://stackoverflow.com/questions/541966/android-how-do-i-do-a-lazy-load-of-images-in-listview
		Drawable d = this.getDrawable();
		if(d != null){
		     ((BitmapDrawable) d).getBitmap().recycle();
		     this.setImageBitmap(null);
		}
		
		// The BitmapRegionDecoder only supports .png and .jpg files
		// So we have to temporarily disable it in the other cases.
		if (!bitmapFile.getAbsolutePath().toLowerCase().endsWith(".png") &&
				!bitmapFile.getAbsolutePath().toLowerCase().endsWith(".jpg"))
			setSupportLargeMaps(false);

		// Load the new image
		BitmapFactory.Options options = new BitmapFactory.Options();		
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(bitmapFile.getAbsolutePath(), options);
		m_mapRect = new Rect(0, 0, options.outWidth, options.outHeight);
		m_mapRectRead = new Rect(m_mapRect);
		m_mapFile = bitmapFile;		
		m_mapSampleSize = 1;
		options.inJustDecodeBounds = false;
		options.inSampleSize = m_mapSampleSize;		
		Bitmap temp = null;		
		while (true)
		{
			try {
				if (largeMapSupport == true)
				{
					try	{
						//m_mapRectRead = new Rect(m_mapRect);
						//m_mapRectRead = new Rect(0, 0, 400, 400); // TODO: Only temporary.
						// Read the new part of the image from file
						BitmapRegionDecoder regionDecoder = BitmapRegionDecoder.newInstance(m_mapFile.getAbsolutePath(), true);
						options.inSampleSize = m_mapSampleSize;
						temp = regionDecoder.decodeRegion(m_mapRectRead, options);
					} catch (IOException e)
					{
						// This should not happen as we only allow JPG and PNG images					
					}	
				}
				else
				{
					temp = BitmapFactory.decodeFile(bitmapFile.getAbsolutePath(), options);
				}
				
				// We got us a bitmap, now center it on the display to make it look good
				float diffX = this.getWidth() / (float)2.0 - temp.getWidth() / (float)2.0;
				float diffY = this.getHeight() / (float)2.0 - temp.getHeight() / (float)2.0;				
				m_SavableData.currentMapTileToDisplayMatrix.postTranslate(diffX, diffY);
				
				// We might not have read the upper left corner, so we must move this matrix according to
				// what portion of the map that we have read.
				diffX = this.getWidth() / (float)2.0 - m_mapRectRead.exactCenterX();
				diffY = this.getHeight() / (float)2.0 - m_mapRectRead.exactCenterY();
				m_SavableData.currentGlobalMapToDisplayMatrix.postTranslate(diffX, diffY);
				// We might have sampled the image to be able to read it, that must be added to the globalMapToDisplayConversionMatrix
				Rect tempRect = new Rect();
				this.getDrawingRect(tempRect);
				mid.x = tempRect.centerX();
				mid.y = tempRect.centerY();
				m_SavableData.currentGlobalMapToDisplayMatrix.postScale(1 / (float)m_mapSampleSize, 1 / (float)m_mapSampleSize, mid.x, mid.y);
								
				this.setImageMatrix(m_SavableData.currentMapTileToDisplayMatrix);				
				this.setImageBitmap(temp);
				
				if (largeMapSupport == true)
					Toast.makeText(
							this.getContext(),
							"Support for large maps is enabled.\n"
									+ "Original image is " + m_mapRect.width()
									+ " x " + m_mapRect.height()
									+ ". The part read is "
									+ m_mapRectRead.width() + " x "
									+ m_mapRectRead.height() + ".", Toast.LENGTH_LONG)
							.show();
				else
					Toast.makeText(
							this.getContext(),
							"Original image is "
									+ m_mapRect.width()
									+ " x "
									+ m_mapRect.height()
									+ ". The image is down sampled with a factor of "
									+ m_mapSampleSize
									+ ". Enable support for large maps in the preferences if full resolution is needed.",
							Toast.LENGTH_LONG).show();

				break;
			} catch(OutOfMemoryError e) {
				if (temp != null)
					temp.recycle();
				
				if (largeMapSupport == true)
				{
					// We could nor read the full image, so make it a quarter of the size
					m_mapRectRead.inset(m_mapRectRead.width() / 4, m_mapRectRead.height() / 4);
				}
				else
				{
					// If we can't read a smaller part, then we must sample the image.
					m_mapSampleSize *= 2; // powers of 2 are more efficient
					options.inSampleSize = m_mapSampleSize;
				}
			}
		}
	}	
		
	private void reReadImageFromDisk() {
		if (m_mapFile == null) // Zooming wothout a map?
			return;
		
		
		// If we have previously loaded an image, then we should reset it in
		// order
		// to minimize our memory consumption
		// http://stackoverflow.com/questions/6033645/mutithreaded-cached-imageview-loading
		// http://evancharlton.com/thoughts/lazy-loading-images-in-a-listview/
		// http://stackoverflow.com/questions/541966/android-how-do-i-do-a-lazy-load-of-images-in-listview
		Drawable d = this.getDrawable();
		if (d != null) {
			((BitmapDrawable) d).getBitmap().recycle();
			this.setImageBitmap(null);
		}

		try	{
			// Find the new part of the map to read in from disk
			RectF displayRectF = new RectF(0, 0, this.getWidth(), this.getHeight()); // Get the original display coordinates
			
			//For testing only
			//int x = 240;
			//displayRectF.offset(x, 0);
			
			Matrix displayToMapConversionMatrix = new Matrix();
			m_SavableData.currentGlobalMapToDisplayMatrix.invert(displayToMapConversionMatrix); // This better work or... 
			RectF displayOnMapRectF = new RectF(displayRectF);
			displayToMapConversionMatrix.mapRect(displayOnMapRectF, displayRectF);			
			int diffXMap = (int) displayOnMapRectF.centerX() - m_mapRectRead.centerX(); // X-axis is to the right
			int diffYMap = (int) displayOnMapRectF.centerY() - m_mapRectRead.centerY(); // Y-axis is downwards
			Rect newMapRectRead = new Rect(m_mapRectRead);
			newMapRectRead.offset(diffXMap, diffYMap); // This should be the map part which center corresponds to the center of the display
			// Bump it inside the m_mapRect so that we get valid coordinates to read from.
			if (newMapRectRead.left < 0)
				newMapRectRead.offset(newMapRectRead.left * -1, 0);
			if (newMapRectRead.top < 0)
				newMapRectRead.offset(0, newMapRectRead.top * -1);
			if (newMapRectRead.right > m_mapRect.right)
				newMapRectRead.offset(m_mapRect.right - newMapRectRead.right, 0);
			if (newMapRectRead.bottom > m_mapRect.bottom)
				newMapRectRead.offset(0, m_mapRect.bottom - newMapRectRead.bottom);

			// 
			RectF newMapTileOnDisplayRectF = new RectF();
			m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(newMapTileOnDisplayRectF, new RectF(newMapRectRead));
			RectF currentMapTileOnDisplayRectF = new RectF(m_mapRectRead);
			m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(currentMapTileOnDisplayRectF); // Move the image rect to display coordinates
			float diffX = newMapTileOnDisplayRectF.centerX() - currentMapTileOnDisplayRectF.centerX();
			float diffY = newMapTileOnDisplayRectF.centerY() - currentMapTileOnDisplayRectF.centerY();			
			
			m_SavableData.currentMapTileToDisplayMatrix.postTranslate(diffX, diffY);

			// Read the new part of the image from file
			BitmapRegionDecoder regionDecoder = BitmapRegionDecoder.newInstance(m_mapFile.getAbsolutePath(), true);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = m_mapSampleSize;			
			Bitmap bitmap = regionDecoder.decodeRegion(newMapRectRead, options);
			
			// Set some data
			m_mapRectRead = newMapRectRead;
			this.setImageBitmap(bitmap);
			this.setImageMatrix(m_SavableData.currentMapTileToDisplayMatrix);						
		} catch (IOException e)
		{
			// This should not happen as we only allow JPG and PNG images					
		}
	}
	
	// Adds a new calibration point for the user to place on the map.
	protected void showCalibrationPoint()
	{
		m_SavableData.isCalibrating = true;
		createNewCalibrationPoint();
		postInvalidate();
	}

	// Removes the calibration point from the map.
	protected void hideCalibrationPoint()
	{
		m_SavableData.isCalibrating = false;		
		postInvalidate();
	}
	
	private void createNewCalibrationPoint(){
		int x = 15;
		int y = 15; // TODO: recalculate this to map coordinates.
		int height = 58;//mDrawable.getIntrinsicHeight();
		int width = 58;//mDrawable.getIntrinsicWidth();
		
		if (m_CalibrationDrawable == null)
			m_CalibrationDrawable = getResources().getDrawable(R.drawable.calibration_point);
		
		//mDrawable = new ShapeDrawable(new OvalShape());
		//mDrawable.setAlpha(55);
		
		m_SavableData.m_CalibrationPositionOnDisplay = new Rect(x, y, x + width, y + height);
		m_CalibrationDrawable.setBounds(m_SavableData.m_CalibrationPositionOnDisplay);
		m_SavableData.m_calibrationPositionOnGlobalMap = new RectF(m_SavableData.m_CalibrationPositionOnDisplay);
		
		//When we have moved the image or zoomed in and place a calibration point, 
		//we want it to be placed at the correct place.
		// convert from map coordinates --> gps coordinates
		Matrix displayToGlobalMapMatrix = new Matrix();
		boolean invertResult = m_SavableData.currentGlobalMapToDisplayMatrix.invert(displayToGlobalMapMatrix);
		if (invertResult == true)
			displayToGlobalMapMatrix.mapRect(m_SavableData.m_calibrationPositionOnGlobalMap);
	}
	
	
	/**
	 * Gets the coordinates of the calibration point. The coordinate system used is the one for the
	 * original map.
	 * @return The coordinates of the calibration point.
	 */
	protected PointF getCalibrationMapCoordinates() {
		return new PointF(
				m_SavableData.m_calibrationPositionOnGlobalMap.centerX(),
				m_SavableData.m_calibrationPositionOnGlobalMap.centerY());
	}	
	
	/**
	 * Gets the current sample size for the currently loaded map.
	 * I.e., the value inSampleSize used in the BitmapFactory.
	 * @return The current sample size of the loaded map.
	 */
	protected int getMapSampleSize() {
		return m_mapSampleSize;
	}
	
	private void createNewPositionPoint() {
		int x = 15;
		int y = 15; // TODO: recalculate this to map coordinates.
		
		//m_PositionDrawable = new ShapeDrawable(new OvalShape());
		//m_PositionDrawable.setAlpha(55);
		m_PositionDrawable = getResources().getDrawable(R.drawable.position_point);
		
		int height = 58;//mDrawable.getIntrinsicHeight();
		int width = 58;//mDrawable.getIntrinsicWidth();
		Rect PositionScreenPosition = new Rect(x, y, x + width, y + height);
		
		m_PositionDrawable.setBounds(PositionScreenPosition);
		m_SavableData.m_PositionMapPosition = new RectF(PositionScreenPosition);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (m_SavableData.isCalibrating && m_CalibrationDrawable != null)
			m_CalibrationDrawable.draw(canvas);
		if (m_SavableData.isTrackingPosition && m_PositionDrawable != null)
			m_PositionDrawable.draw(canvas);
	}

	@Override
	protected void onDetachedFromWindow () {
		if (zoomButtons != null)
			zoomButtons.setVisible(false);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		zoomButtons.setVisible(true);
		
		// Handle touch events here...
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			if (m_SavableData.isCalibrating && m_SavableData.m_CalibrationPositionOnDisplay.contains((int) event.getX(), (int) event.getY())) {
				m_SavableData.savedMapTileToDisplayMatrix.set(m_SavableData.currentMapTileToDisplayMatrix);
				m_SavableData.savedGlobalMapToDisplayMatrix.set(m_SavableData.currentGlobalMapToDisplayMatrix);				
				start.set(event.getX(), event.getY());
				m_SavableData.mode = MOVE;
			} else {
				m_SavableData.savedMapTileToDisplayMatrix.set(m_SavableData.currentMapTileToDisplayMatrix);     // TODO: Not needed?
				m_SavableData.savedGlobalMapToDisplayMatrix.set(m_SavableData.currentGlobalMapToDisplayMatrix); //TODO: Not needed
				start.set(event.getX(), event.getY());
				m_SavableData.mode = DRAG;
			}
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			oldDist = spacing(event);
			if (oldDist > 10f) {
				m_SavableData.savedMapTileToDisplayMatrix.set(m_SavableData.currentMapTileToDisplayMatrix);
				m_SavableData.savedGlobalMapToDisplayMatrix.set(m_SavableData.currentGlobalMapToDisplayMatrix);
				midPoint(mid, event);
				m_SavableData.mode = ZOOM;
			}
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_POINTER_UP:
			m_SavableData.mode = NONE;
			break;
		case MotionEvent.ACTION_MOVE:
			if (m_SavableData.mode == DRAG) {
				float diffX = event.getX() - start.x;
				float diffY = event.getY() - start.y;
				
				 //reset to where we put down the first finger, no need to add calculation errors on top of each other
				m_SavableData.currentMapTileToDisplayMatrix.set(m_SavableData.savedMapTileToDisplayMatrix);
				m_SavableData.currentGlobalMapToDisplayMatrix.set(m_SavableData.savedGlobalMapToDisplayMatrix);
				
				m_SavableData.currentMapTileToDisplayMatrix.postTranslate(diffX, diffY);
				m_SavableData.currentGlobalMapToDisplayMatrix.postTranslate(diffX, diffY);
				
				// Drag the calibration point
				if (m_SavableData.isCalibrating)
				{
					RectF newCalibrationPositionOnDisplay = new RectF(m_SavableData.m_calibrationPositionOnGlobalMap);
					m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(newCalibrationPositionOnDisplay);
					newCalibrationPositionOnDisplay.round(m_SavableData.m_CalibrationPositionOnDisplay);				
					m_CalibrationDrawable.setBounds(m_SavableData.m_CalibrationPositionOnDisplay);
				}
				
				// Drag the GPS position point
				if (m_SavableData.isTrackingPosition && m_PositionDrawable != null)
				{
					RectF newPositionPositionOnDisplayRectF = new RectF(m_SavableData.m_PositionMapPosition);
					m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(newPositionPositionOnDisplayRectF);
					Rect newPositionPositionOnDisplayRect = new Rect();
					newPositionPositionOnDisplayRectF.round(newPositionPositionOnDisplayRect);				
					m_PositionDrawable.setBounds(newPositionPositionOnDisplayRect);
				}
				
				this.setImageMatrix(m_SavableData.currentMapTileToDisplayMatrix);
				
				// TODO: Investigate why it does not work to have the call here.
				//reReadImageFromDisk();
				
			} else if (m_SavableData.mode == ZOOM) {
				float newDist = spacing(event);
				if (newDist > 10f) {
					m_SavableData.currentMapTileToDisplayMatrix.set(m_SavableData.savedMapTileToDisplayMatrix);
					m_SavableData.currentGlobalMapToDisplayMatrix.set(m_SavableData.savedGlobalMapToDisplayMatrix);
					float scale = newDist / oldDist;
					m_SavableData.currentMapTileToDisplayMatrix.postScale(scale, scale, mid.x, mid.y);
					m_SavableData.currentGlobalMapToDisplayMatrix.postScale(scale, scale, mid.x, mid.y);
					
					// The coordinates change due to scaling, but we want to keep the shape in its original size
					if (m_SavableData.isCalibrating)
					{
						RectF newCalibrationPositionOnDisplay = new RectF(m_SavableData.m_calibrationPositionOnGlobalMap);
						m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(newCalibrationPositionOnDisplay);					
						float dx =  (float)((newCalibrationPositionOnDisplay.width() - m_SavableData.m_calibrationPositionOnGlobalMap.width()) / 2.0);
						float dy = (float)((newCalibrationPositionOnDisplay.height() - m_SavableData.m_calibrationPositionOnGlobalMap.height()) / 2.0);
						newCalibrationPositionOnDisplay.inset(dx, dy);
						newCalibrationPositionOnDisplay.round(m_SavableData.m_CalibrationPositionOnDisplay);					
						m_CalibrationDrawable.setBounds(m_SavableData.m_CalibrationPositionOnDisplay);
					}
					
					// The coordinates change due to scaling, but we want to keep the shape in its original size
					if (m_SavableData.isTrackingPosition && m_PositionDrawable != null)
					{
						RectF newPositionPositionOnDisplayRectF = new RectF(m_SavableData.m_PositionMapPosition);
						m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(newPositionPositionOnDisplayRectF);					
						float dx =  (float)((newPositionPositionOnDisplayRectF.width() - m_SavableData.m_PositionMapPosition.width()) / 2.0);
						float dy = (float)((newPositionPositionOnDisplayRectF.height() - m_SavableData.m_PositionMapPosition.height()) / 2.0);
						newPositionPositionOnDisplayRectF.inset(dx, dy);
						Rect newPositionPositionOnDisplayRect = new Rect();
						newPositionPositionOnDisplayRectF.round(newPositionPositionOnDisplayRect);					
						m_PositionDrawable.setBounds(newPositionPositionOnDisplayRect);
					}
					
					this.setImageMatrix(m_SavableData.currentMapTileToDisplayMatrix);
				}
			} else if (m_SavableData.mode == MOVE) { // The user moves the calibration point on the screen by dragging it.
				// The MOVE only affects the calibration point, not the gps position.
				
				// Must calculate the position from the originalPosition as position is just integers
				// and there it can the rounding problems.
				
				// The offset must be moved from display coordinates to map coordinates
				// before we can use it.
				Matrix displayToGlobalMapMatrix = new Matrix();
				if(m_SavableData.currentGlobalMapToDisplayMatrix.invert(displayToGlobalMapMatrix))
				{
					float[] points = {start.x, start.y, event.getX(), event.getY()};
					displayToGlobalMapMatrix.mapPoints(points);
					m_SavableData.m_calibrationPositionOnGlobalMap.offset(points[2] - points[0], points[3] - points[1]);
				}
				
				//originalPosition.offset(diffX, diffY);
				RectF calibrationPositionOnDisplay = new RectF(m_SavableData.m_calibrationPositionOnGlobalMap);
				m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(calibrationPositionOnDisplay);
							
				// The coordinates change, but we want to keep the shape in its original size
				float dx =  (float)((calibrationPositionOnDisplay.width() - m_SavableData.m_calibrationPositionOnGlobalMap.width()) / 2.0);
				float dy = (float)((calibrationPositionOnDisplay.height() - m_SavableData.m_calibrationPositionOnGlobalMap.height()) / 2.0);
				calibrationPositionOnDisplay.inset(dx, dy);								
				calibrationPositionOnDisplay.round(m_SavableData.m_CalibrationPositionOnDisplay);
				if (m_SavableData.isCalibrating)
					m_CalibrationDrawable.setBounds(m_SavableData.m_CalibrationPositionOnDisplay);
								
				start.set(event.getX(), event.getY());
				invalidate();				
			}
			break;
		}
		
		return true; // indicate event was handled
	}

	private class MyZoomController implements ZoomButtonsController.OnZoomListener {
		private ImageView OwnerView;
		
		public MyZoomController(ImageView OwnerView) {
			this.OwnerView = OwnerView;
		}
		
		public void onVisibilityChanged (boolean visible) {
			if (largeMapSupport == true && visible == false)
			{
				reReadImageFromDisk();
			}
		}
		
		public void onZoom (boolean zoomIn) {
			float scale = (float) 0.75;
			if (zoomIn == true)
				scale = 1 / scale;
			 			
			Rect tempRect = new Rect();
			OwnerView.getDrawingRect(tempRect);
			mid.x = tempRect.centerX();
			mid.y = tempRect.centerY();
			
			m_SavableData.currentMapTileToDisplayMatrix.postScale(scale, scale, mid.x, mid.y);
			m_SavableData.currentGlobalMapToDisplayMatrix.postScale(scale, scale, mid.x, mid.y);
				
			// The coordinates change due to scaling, but we want to keep the shape in its original size
			if (m_SavableData.isCalibrating)
			{
				RectF newCalibrationPositionOnDisplay = new RectF(m_SavableData.m_calibrationPositionOnGlobalMap);
				m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(newCalibrationPositionOnDisplay);					
				float dx =  (float)((newCalibrationPositionOnDisplay.width() - m_SavableData.m_calibrationPositionOnGlobalMap.width()) / 2.0);
				float dy = (float)((newCalibrationPositionOnDisplay.height() - m_SavableData.m_calibrationPositionOnGlobalMap.height()) / 2.0);
				newCalibrationPositionOnDisplay.inset(dx, dy);
				newCalibrationPositionOnDisplay.round(m_SavableData.m_CalibrationPositionOnDisplay);					
				m_CalibrationDrawable.setBounds(m_SavableData.m_CalibrationPositionOnDisplay);
			}
				
			// The coordinates change due to scaling, but we want to keep the shape in its original size
			if (m_SavableData.isTrackingPosition && m_PositionDrawable != null)
			{
				RectF newPositionPositionOnDisplay = new RectF(m_SavableData.m_PositionMapPosition);
				m_SavableData.currentGlobalMapToDisplayMatrix.mapRect(newPositionPositionOnDisplay);
				float dx =  (float)((newPositionPositionOnDisplay.width() - m_SavableData.m_PositionMapPosition.width()) / 2.0);
				float dy = (float)((newPositionPositionOnDisplay.height() - m_SavableData.m_PositionMapPosition.height()) / 2.0);
				newPositionPositionOnDisplay.inset(dx, dy);
				Rect newPositionPositionOnDisplayRect = new Rect();
				newPositionPositionOnDisplay.round(newPositionPositionOnDisplayRect);
				m_PositionDrawable.setBounds(newPositionPositionOnDisplayRect);
			}
					
			
			//TODO:Add a call to the BitmapRegionDecoder and put it in a try catch
			// Calculate what portion of the map we should read in from disk.
			// We need a size.
			// Center that size on the center of the imageView (getWidth(), getHeight())
			// 
			// We have three cases.
			// 1. Image has been moved to the side and is not completely covering the imageView.
			//    Read a new part of the map from disk using the same mapSampleSize.
			// 2. Image has been zoomed out so far that it does not cover the imageView.
			//    increase mapSampleSize and read a new part of the map from disk.
			//    Alternative: Read a new part of the image from disk. If it is not covering the display
			//                 completely, then increase mapSampleSize.
			// 3. Image has been zoomed in so far that we are interpolating to display it.
			//
			// After changing the mapSampleSize, the Matrix must be updated so that the 
			// image zoom level looks correct. (.postScale)
			//
			// 1 and 2 should only be performed if the wanted part of the image exists in the map on disk.
			
			if (false && !isImageCompletelyCoveringImageView())
			{
				if (isImageCompletelyContainedInImageView())
				{					
					// Decrease the mapSampleSize
					// Update the Matrix with scale value
					// Update the part that we read from the image so that it shows correct values					
					
					Matrix scaleMatrix = new Matrix();
					scaleMatrix.postScale(1 / (float) 2.0, 1 / (float) 2.0, mid.x, mid.y);
					RectF mapRectFRead = new RectF(m_mapRectRead);
					scaleMatrix.mapRect(mapRectFRead);

					m_mapSampleSize *= 2;

					mapRectFRead.round(m_mapRectRead);						
					// TODO: I have a rect before and after the changes, why not use that to compute the scaling and
					// translation instead of doing it with hardcoded values?
					// Could be solved using setRectToRect(RectF src, RectF dst, Matrix.ScaleToFit stf)
					m_SavableData.currentMapTileToDisplayMatrix.postScale(1 / (float) 2.0, 1 / (float) 2.0, mid.x, mid.y);					
				}
				
				// Read a new part of the map from disk
				// Update the Matrix with translation, as we move the center of the read image
				RectF imageRectF = new RectF(OwnerView.getDrawable().getBounds());
				m_SavableData.currentMapTileToDisplayMatrix.mapRect(imageRectF); // Move the image rect to display coordinates
				float diffX = OwnerView.getWidth() / (float)2.0 - imageRectF.centerX();
				float diffY = OwnerView.getHeight() / (float)2.0 - imageRectF.centerY();
				Matrix translationMatrix = new Matrix();
				translationMatrix.postTranslate(diffX, diffY);
				RectF mapRectFRead = new RectF(m_mapRectRead);
				translationMatrix.mapRect(mapRectFRead);
				mapRectFRead.round(m_mapRectRead);				
				// TODO: Should I translate the Matrix in m_saveable as well?
				try	{
					BitmapRegionDecoder regionDecoder = BitmapRegionDecoder.newInstance(m_mapFile.getAbsolutePath(), true);
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inSampleSize = m_mapSampleSize;
					Rect decodeRegion = new Rect();
					decodeRegion.setIntersect(m_mapRectRead, m_mapRect); // decodeRegion will stay (0,0,0,0) if no intersection
					Bitmap bitmap = regionDecoder.decodeRegion(decodeRegion, options);
					OwnerView.setImageBitmap(bitmap);
				} catch (IOException e)
				{
					// This should not happen as we only allow JPG and PNG images					
				}
				
			}
			
			OwnerView.setImageMatrix(m_SavableData.currentMapTileToDisplayMatrix);
		}		
	}
	
	/*
	This seems to center the image, now we need to decide on the zoom amount as well i.e. m_mapSampleSize and scale...
	private void reReadImageFromDisk() {		
		// Offset the wantedImageRect so that it shares the center with the display.
		RectF imageRectF = new RectF(this.getDrawable().getBounds());
		m_SavableData.matrix.mapRect(imageRectF); // Move the image rect to display coordinates
		int diffX = this.getWidth() / 2 - (int) imageRectF.centerX();
		int diffY = this.getHeight() / 2 - (int) imageRectF.centerY();
				
		//Matrix transformationMatrix = new Matrix();
		//transformationMatrix.setRectToRect(currentImageRect, wantedImageRect, Matrix.ScaleToFit.CENTER);		
		m_mapRectRead.offset(diffX, diffY);
		m_SavableData.matrix.postTranslate(diffX, diffY);
		
		try	{
			BitmapRegionDecoder regionDecoder = BitmapRegionDecoder.newInstance(m_mapFile.getAbsolutePath(), true);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = m_mapSampleSize;
			Rect decodeRegion = new Rect();
			decodeRegion.setIntersect(m_mapRectRead, m_mapRect); // decodeRegion will stay (0,0,0,0) if no intersection
			Bitmap bitmap = regionDecoder.decodeRegion(decodeRegion, options);
			this.setImageBitmap(bitmap);
		} catch (IOException e)
		{
			// This should not happen as we only allow JPG and PNG images					
		}
			
		this.setImageMatrix(m_SavableData.matrix);
	}
*/
	
	/**
	 * 
	 * @return True if the image is covering the whole imageView, false otherwise.
	 */
	private boolean isImageCompletelyCoveringImageView() {
		RectF imageRectF = new RectF(getDrawable().getBounds());
		m_SavableData.currentMapTileToDisplayMatrix.mapRect(imageRectF); // Move the image rect to display coordinates
		boolean coversDisplay = imageRectF.contains(0, 0, getWidth(), getHeight());
		return coversDisplay;
	}
	
	/**
	 * 
	 * @return True if the image is contained in the imageView, false otherwise.
	 */
	private boolean isImageCompletelyContainedInImageView() {
		RectF imageRectF = new RectF(getDrawable().getBounds());
		m_SavableData.currentMapTileToDisplayMatrix.mapRect(imageRectF); // Move the image rect to display coordinates
		RectF imageViewRectF = new RectF(0, 0, getWidth(), getHeight());
		boolean containsImage = imageViewRectF.contains(imageRectF);
		return containsImage;
	}
	
	/** Determine the space between the first two fingers */
	private float spacing(MotionEvent event) {
		// ...
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return FloatMath.sqrt(x * x + y * y);
	}

	/** Calculate the mid point of the first two fingers */
	private void midPoint(PointF point, MotionEvent event) {
		// ...
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		point.set(x / 2, y / 2);
	}
}

class mapSaveData implements Parcelable{
	int mode = 0;
	boolean isTrackingPosition;  // true if we are marking the GPS on the map
	boolean isCalibrating; 		 // true if we are calibrating now
	
	RectF m_PositionMapPosition; //position on the map/image
	
	Rect m_CalibrationPositionOnDisplay;  //position on the screen.   Used for finger press detection
	RectF m_calibrationPositionOnGlobalMap;   //position on the map/image
	
	Location lastLocation; //To be able to draw out position if we don't get a new location after the map is set.

	// These matrices will be used to move and zoom the current part of the image
	Matrix currentMapTileToDisplayMatrix = new Matrix();
	Matrix savedMapTileToDisplayMatrix = new Matrix();
	
	// These matrices are used to map the complete map to the display (as opposed to the ones above)
	Matrix currentGlobalMapToDisplayMatrix = new Matrix();
	Matrix savedGlobalMapToDisplayMatrix = new Matrix();

	//Used for converting gps coordinates to map coordinates
	Matrix gpsToMapMapMatrix = new Matrix(); //Needs to be something when we parcel it.
	
	
	public int describeContents() {
        return 0;
    }
	
	public static final Parcelable.Creator<mapSaveData> CREATOR
		= new Parcelable.Creator<mapSaveData>() {
		public mapSaveData createFromParcel(Parcel in) {
			return new mapSaveData(in);
		}

		public mapSaveData[] newArray(int size) {
			return new mapSaveData[size];
		}
	};

	//Anropa den här metoden från MapCalibrator istället för att göra det här objektet Parcelable
	//Detta eftersom jag inte vet hur jag skulle skapa det innifrån sig själv. Konstruktorn kräver parametrar.
	//Måste lägga till bilden också. Eller en referens till den, filnamnet kanske.
	public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mode);
        out.writeBooleanArray(new boolean[]{isTrackingPosition, isCalibrating});
        if (isTrackingPosition)
        	out.writeParcelable(m_PositionMapPosition, flags);
        if (isCalibrating)
        {
        	out.writeParcelable(m_CalibrationPositionOnDisplay, flags);
        	out.writeParcelable(m_calibrationPositionOnGlobalMap, flags);
        }
        out.writeParcelable(lastLocation, flags);
        
        float[] temp = new float[9];
        currentMapTileToDisplayMatrix.getValues(temp);
        out.writeFloatArray(temp);
        
        temp = new float[9];
        savedMapTileToDisplayMatrix.getValues(temp);
        out.writeFloatArray(temp);
        
        temp = new float[9];
        currentGlobalMapToDisplayMatrix.getValues(temp);
        out.writeFloatArray(temp);
        
        temp = new float[9];
        savedGlobalMapToDisplayMatrix.getValues(temp);
        out.writeFloatArray(temp);
        
        temp = new float[9];
        gpsToMapMapMatrix.getValues(temp);
        out.writeFloatArray(temp);        
    }
	
	private mapSaveData(Parcel in) {        
		this.mode = in.readInt();
		boolean bTemp[] = new boolean[2];
		in.readBooleanArray(bTemp);
		this.isTrackingPosition = bTemp[0];
		this.isCalibrating = bTemp[1];
		        
        if (isTrackingPosition)
        	m_PositionMapPosition = in.readParcelable(Drawable.class.getClassLoader());
        if (isCalibrating)
        {
        	m_CalibrationPositionOnDisplay = in.readParcelable(Rect.class.getClassLoader());
        	m_calibrationPositionOnGlobalMap = in.readParcelable(RectF.class.getClassLoader());
        }
        
        lastLocation = in.readParcelable(Location.class.getClassLoader());
        
        float[] temp = new float[9];
        in.readFloatArray(temp);
        currentMapTileToDisplayMatrix.setValues(temp);
        
        in.readFloatArray(temp);
        savedMapTileToDisplayMatrix.setValues(temp);
        
        in.readFloatArray(temp);
        currentGlobalMapToDisplayMatrix.setValues(temp);
        
        in.readFloatArray(temp);
        savedGlobalMapToDisplayMatrix.setValues(temp);
        
        in.readFloatArray(temp);
        gpsToMapMapMatrix.setValues(temp);        
    }
	
	public mapSaveData(){}
}
