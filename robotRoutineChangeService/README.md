# Industrial Robot Check for RAPID Routine Changes Service
A service that listens to events from the *robotIsWaitingService* and checks for RAPID routine changes. Whenever a
new routine is enabled the service publishes a stop event for the previous routine and a start event for the new event
on the bus. It is also possible to add certain "wait" routines to a config file, which will cause the service to not
publish events for those routines. These events are useful for the path-time chart service.