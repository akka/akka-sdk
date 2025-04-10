There are two ways to test an Entity:

* Unit test, which only runs the Entity component with a test kit.
* Integration test, running the entire service with a test kit and the test interacting with it using a component client or over HTTP requests.

Each way has its benefits, unit tests are faster and provide more immediate feedback about success or failure but can only test a single entity at a time and in isolation. Integration tests, on the other hand, are more realistic and allow many entities to interact with other components inside and outside the service.
