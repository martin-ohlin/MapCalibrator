package com.Martin.MapCalibrator;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;

public class MyLocationListener implements LocationListener{
	LocationManager locationManager;
	private MyDrawableImageView view;
	private boolean GPS = false;
	private Location lastLocation;
	private MapCalibrator owner;
	
	protected MyLocationListener(LocationManager locationManager, MapCalibrator owner, MyDrawableImageView view) {
		this.view = view;
		this.locationManager = locationManager;
		this.owner = owner;
	}

	@Override
	public void onLocationChanged(Location location) {
		// Called when a new location is found by the network location provider.
		if (location.getProvider().equals(LocationManager.GPS_PROVIDER) || GPS == false)
		{
			lastLocation = location;
			view.makeUseOfNewLocation(location);
			if (location.hasAccuracy() == true)
			{				
				owner.updateCustomTitleBar(Float.toString(location.getAccuracy()));
			} else {
				owner.updateCustomTitleBar("");
			}
		}	
	}
	
	protected void startListening() {		
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 5, this);
		//locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 5, this); //10 seconds 5 meters
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 2, this); // 1 second, 2 meters 
	}
	
	protected void stopListening() {		
		locationManager.removeUpdates(this);
	}
	
	protected Location getLastLocation()
	{
		if (lastLocation == null)
			lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (lastLocation == null)
			lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		
		return lastLocation;
	}
	
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		if (provider.equals(LocationManager.GPS_PROVIDER))
		{
			if (status == LocationProvider.AVAILABLE)
				GPS = true;
			else
				GPS = false;
		}
		
		//if (provider.equals(LocationManager.NETWORK_PROVIDER))
		//{
		//	if (status == LocationProvider.AVAILABLE)
		//		NETWORK = true;
		//	else
		//		NETWORK = false;
		//}
	}

	@Override
	public void onProviderEnabled(String provider) {
		
	}

	@Override
	public void onProviderDisabled(String provider) {
	}	
}
