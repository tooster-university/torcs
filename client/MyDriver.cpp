#include "MyDriver.h"
#include "DriverParameters.h"

CarControl MyDriver::wDrive(CarState cs){
    return SimpleDriver::wDrive(cs);
}