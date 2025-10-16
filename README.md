


















## Run the project

To run the project locally, you need to have a local PostgreSQL database running. You can use Docker to run a PostgreSQL
container.

Run the following command to run the PostgreSQL container in local docker

```bash
docker run --name ct-postgres -e POSTGRES_PASSWORD=password -e POSTGRES_USER=user -e POSTGRES_DB=ct-db -p 5432:5432 -d postgres:14.18
```

## Setup and Running Instructions
1. Navigate to the project directory:
   ```bash
   cd coding_test
   ```
2. Install dependencies:
   ```bash
   go mod tidy
   ```
3. Run the application, it will run the DB migration automatically and start the server at port 8080:
   ```bash
   go run main.go
   ```