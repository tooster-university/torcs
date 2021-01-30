#pragma once

#include "SimpleDriver.h"
#include "DriverParameters.h"

class MyDriver : public SimpleDriver
{
    public:
    MyDriver(DriverParameters parameters):SimpleDriver(parameters){}
    virtual CarControl wDrive(CarState cs);
};