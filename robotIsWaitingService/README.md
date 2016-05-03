# Industrial Robot Check if IsWaiting Service
A service that listens to RAPID pointer change events from robot-path-service and extends them with a boolean value indicating whether the robot is moving or not.

## Quickstart
Setup the correct configurations in the config file under src/main/resources. Then execute
```
sbt
```
to enter sbt.
## Starting the service
```
sbt run
```
## Packaging the service
See the SBT assembly plugin for more info. To package as zip file run
```
sbt universal:packageBin
```
## Running tests
```
sbt clean test
```
