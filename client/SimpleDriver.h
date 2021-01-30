/***************************************************************************
 
    file                 : SimpleDriver.h
    copyright            : (C) 2007 Daniele Loiacono
 
 ***************************************************************************/

/***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
#ifndef SIMPLEDRIVER_H_
#define SIMPLEDRIVER_H_

#include <iostream>
#include <cmath>
#include "BaseDriver.h"
#include "DriverParameters.h"
#include "CarState.h"
#include "CarControl.h"
#include "SimpleParser.h"
#include "WrapperBaseDriver.h"

#define PI 3.14159265

using namespace std;

class SimpleDriver : public WrapperBaseDriver
{
public:
	
	// Constructor
	SimpleDriver():stuck(0),clutch(0) {}
	SimpleDriver(DriverParameters parameters):stuck(0),clutch(0),parameters(parameters){
	}

	// SimpleDriver implements a simple and heuristic controller for driving
	virtual CarControl wDrive(CarState cs);

	// Print a shutdown message 
	virtual void onShutdown();
	
	// Print a restart message 
	virtual void onRestart();

	// Initialization of the desired angles for the rangefinders
	virtual void init(float *angles);

private:
	// pre-computed sin5
	const static float sin5;
	// pre-computed cos5
	const static float cos5;
	// counter of stuck steps
	int stuck;
	
	// current clutch
	float clutch;

	const DriverParameters parameters;

	// Solves the gear changing subproblems
	int getGear(CarState &cs);

	// Solves the steering subproblems
	float getSteer(CarState &cs);
	
	// Solves the gear changing subproblems
	float getAccel(CarState &cs);
	
	// Apply an ABS filter to brake command
	float filterABS(CarState &cs,float brake);

	// Solves the clucthing subproblems
	void clutching(CarState &cs, float &clutch);
};

#endif /*SIMPLEDRIVER_H_*/
