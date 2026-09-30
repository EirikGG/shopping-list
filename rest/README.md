


### PG docker instance
docker run --name test-postgres -e POSTGRES_DB=testdb -e POSTGRES_USER=testuser -e POSTGRES_PASSWORD=testpassword -p 5432:5432 -d postgres:16


### App run command
mvn exec:java -Dexec.mainClass="no.eirikgg.shoppinglist.Main" -DDB.HOSTNAME=localhost -DDB.PORT=5432 -DDB.NAME=testdb -DDB.USERNAME=testuser -DDB.PASSWORD=testpassword -DAPP.PORT=8080