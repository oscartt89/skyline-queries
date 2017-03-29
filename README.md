# Skyline queries developed with akka and scala
author: Oscar Torreno Tirado

Example CURL commands to add points and to initiate the query:
* `curl -v -H "Content-Type: application/json" -X POST http://localhost:9000/skyline/add-point -d '{"data":[5,7]}'`
* `curl -v -H "Content-Type: application/json" -X POST http://localhost:9000/skyline/add-point -d '{"data":[6,4]}'`
* `curl -v -H "Content-Type: application/json" -X POST http://localhost:9000/skyline/add-point -d '{"data":[7,3]}'`
* `curl -v -X GET http://localhost:9000/skyline/query`
* `curl -v -H "Content-Type: application/json" -X POST http://localhost:9000/skyline/add-point -d '{"data":[8,2]}'`
* `curl -v -H "Content-Type: application/json" -X POST http://localhost:9000/skyline/add-point -d '{"data":[9,1]}'`
