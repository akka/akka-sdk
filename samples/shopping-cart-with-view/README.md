# Shopping Cart Sample (with View)
This example builds on the previous shopping cart sample by taking a look at the domain model and improving it. This sample
adds user authentication, more cleanly separates the data types across roles and responsibilities, and adds a view that can be used by users to query the read model.


## Testing the Service
Before trying to run this example, make sure that you have all of the pre-requisites installed, including a working JDK and running Docker engine.

Running and testing this service works a bit differently than the first shopping cart. This sample requires JWT authentication for the service endpoint. However, the code doesn't require any specific issuer, so as long as you've created a valid JWT that includes both an `iss` and a `sub` field and is not expired, you can authenticate against the service.

For example, here is a JWT that you can use for your testing. 

```shell
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJib2IiLCJuYW1lIjoiQm9iIEJvYmJlcnNvbiIsImlzcyI6ImFsaWNlIiwiaWF0IjoxNTE2MjM5MDIyfQ.wIxafOw2k4TgdCm2pH4abupetKRKS4ItOKlsNTY-pzc
```

In a real production scenario, you would likely want to add more claims assertions required of clients like a valid issuer or even custom claims in the token. You'll need to add this token to the `Authorization` header, as shown in the following query:

```
curl http://localhost:9000/carts/my -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJib2IiLCJuYW1lIjoiQm9iIEJvYmJlcnNvbiIsImlzcyI6ImFsaWNlIiwiaWF0IjoxNTE2MjM5MDIyfQ.wIxafOw2k4TgdCm2pH4abupetKRKS4ItOKlsNTY-pzc'
```

Assuming you've just run `mvn compile exec:java` in the sample's root directory, `curl` should return a `404` because the current user has no open shopping cart. All mutations to a shopping cart happen under the `/my` prefix, which prevents users from being able to modify carts that belong to someone else.

Use a query like the following to add an item to the current user's cart:

```
curl -i -X PUT -H "Content-Type: application/json" -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJib2IiLCJuYW1lIjoiQm9iIEJvYmJlcnNvbiIsImlzcyI6ImFsaWNlIiwiaWF0IjoxNTE2MjM5MDIyfQ.wIxafOw2k4TgdCm2pH4abupetKRKS4ItOKlsNTY-pzc" -d '{"productId": "ABC", "name": "The product", "description": "This is the product", "quantity": 2}' http://localhost:9000/carts/my/item
```

After executing this query, you can either use `curl` or use the website provided by `akka local console` to examine the state of things. After adding this one item, you will have a cart entity with an ID of `bob-1` (the bearer token in this README is for a `sub` of `bob`), you'll have a user entity called `bob`

