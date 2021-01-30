#pragma once

struct DriverParameters{
	/* Gear Changing Constants*/
	// RPM values to change gear 
	int gearUp[6] = {5000,6000,6000,6500,7000,0};
	int gearDown[6] = {0,2500,3000,3000,3500,3500};
		
	/* Stuck constants*/
	
	// How many time steps the controller wait before recovering from a stuck position
	int stuckTime = 25;
	// When car angle w.r.t. track axis is grather tan stuckAngle, the car is probably stuck
	float stuckAngle = .523598775; //PI/6
	
	/* Steering constants*/
	
	// Angle associated to a full steer command
	float steerLock = 0.366519;	
	// Min speed to reduce steering command 
	float steerSensitivityOffset = 80.0;
	// Coefficient to reduce steering command at high speed (to avoid loosing the control)
	float wheelSensitivityCoeff = 1;
	
	/* Accel and Brake Constants*/
	
	// max speed allowed
	float maxSpeed = 150;
	// Min distance from track border to drive at  max speed
	float maxSpeedDist = 70;

	
	/* ABS Filter Constants */
	
	// Radius of the 4 wheels of the car
	float wheelRadius[4] = {0.3306,0.3306,0.3276,0.3276};
	// min slip to prevent ABS
	float absSlip = 2.0;						
	// range to normalize the ABS effect on the brake
	float absRange = 3.0;
	// min speed to activate ABS
	float absMinSpeed = 3.0;

	/* Clutch constants */
	float clutchMax = 0.5;
	float clutchDelta = 0.05;
	float clutchRange = 0.82;
	float clutchDeltaTime = 0.02;
	float clutchDeltaRaced = 10;
	float clutchDec = 0.01;
	float clutchMaxModifier = 1.3;
	float clutchMaxTime = 1.5;
};