# JustTagTask

A Spring Boot service that ingests large CSV files of ad data (up to 5 GB), loads each file into PostgreSQL once, and runs operations or aggregations on the loaded data. Every run writes its result to a new table.

## How it works

1. Upload: a single streaming multipart POST. The file is written to disk as it arrives, and its progress is tracked from the first byte. A header check then marks the file `READY` or `FAILED`.
2. Load: a Spring Batch job copies the CSV into `canonical.ad_record`. Malformed lines are skipped and counted. The load can be stopped and restarted.
3. Process: each run is a Spring Batch job that reads `canonical.ad_record` and writes its result to its own table in the `result` schema. A file can have any number of runs, one at a time. Runs can be stopped and restarted.

File status: `UPLOADING → UPLOADED → READY → LOADING → LOADED`, or `FAILED` / `STOPPED`.

Run status: `STARTING → STARTED → COMPLETED`, or `FAILED` / `STOPPED`.

### Synchronous and asynchronous work

- Upload is synchronous: the POST returns 202 once the whole file is on disk. Only the header check that follows runs in the background.
- Load and processing are asynchronous: the POST validates the request, starts the Spring Batch job on a background thread and returns 202 at once. Follow the job through its status endpoint.
- Stop and restart return immediately. A stopped job finishes its current chunk, or has its SQL statement cancelled, and then ends `STOPPED`.

### How the upload works

Spring's multipart handling is turned off, because it buffers the whole file before the controller runs. Instead, Commons FileUpload reads the `file` part as a stream and copies it to disk in 64 KB buffers, so memory use stays flat even for a 5 GB file. 

The `uploaded_file` row is created as `UPLOADING` before the first byte is written. Bytes received and the row count are saved every 5 seconds, so `GET /api/files/{fileId}` shows progress while the POST is still running. 

If the file goes over the size limit (413), the client disconnects, or the server restarts mid-upload, the file is marked `FAILED` and the partial file is deleted.

## Running

Requires Java 25 and Docker.
The app listens on `http://localhost:8080`.

## Test coverage

The latest test coverage report is in [`testCoverageReport/`](testCoverageReport/). Open `testCoverageReport/index.html` in a browser to see coverage per package and class.

| Class | Method | Branch | Line |
|---|---|---|---|
| 100% (64/64) | 99.5% (184/185) | 94.2% (98/104)* | 99.6% (521/523) |

\* JaCoCo, run by `./mvnw clean verify`, reports 96.2% branch coverage (102/106) for the same code.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/files` | Upload a CSV as the multipart part `file`. Returns 202 with `fileId`. |
| `GET` | `/api/files?status=&page=&size=` | File history, newest first, with each file's runs. Page size is 1–100. |
| `GET` | `/api/files/{fileId}` | File details, its load and its runs. |
| `POST` | `/api/files/{fileId}/load` | Start the load. The file must be `READY`. |
| `POST` | `/api/files/{fileId}/load/stop` | Stop the load. |
| `POST` | `/api/files/{fileId}/load/restart` | Restart a `STOPPED` or `FAILED` load. |
| `GET` | `/api/files/{fileId}/load` | Load status, counts and progress. |
| `POST` | `/api/files/{fileId}/process` | Start a run. The file must be `LOADED`, with no run in progress. |
| `POST` | `/api/files/{fileId}/process/{runId}/stop` | Stop a run. |
| `POST` | `/api/files/{fileId}/process/{runId}/restart` | Restart a `STOPPED` or `FAILED` run. |
| `GET` | `/api/files/{fileId}/process/{runId}` | Run status, settings and progress. |

Errors are returned as Problem Details: 
- 400 for invalid input, 
- 404 for unknown files or runs, 
- 409 for a wrong state, 
- 413 for a file over the size limit.

## Run requests

A row operation computes one result per row with `ADD`, `SUBTRACT`, `MULTIPLY` or `DIVIDE`. Dividing by zero gives `null`.

```json
{"type": "OPERATION", "leftColumn": "revenue", "rightColumn": "advertiserMargin", "operation": "MULTIPLY"}
```

An aggregation computes one result per group with `SUM`, `AVG`, `MIN`, `MAX` or `COUNT`.

```json
{"type": "AGGREGATION", "groupBy": "campaignId", "valueColumn": "revenue", "function": "SUM"}
```

Operands and value columns must be numeric: `impressions`, `clicks`, `revenue` or `advertiserMargin`. `groupBy` can be any column: `advertiserId`, `campaignId`, `creativeId`, `adBoxUUID` or one of the numeric columns.

## Examples

The examples use the sample file in `src/main/resources/sample_data/` and shell variables for the IDs.

### Upload a file

```bash
curl -F file=@src/main/resources/sample_data/sample_ads_data.csv http://localhost:8080/api/files
```

```json
{"fileId": "dcd8a8a2-3e67-42cf-af95-12201081d3da", "status": "UPLOADED"}
```

```bash
FILE=dcd8a8a2-3e67-42cf-af95-12201081d3da
curl http://localhost:8080/api/files/$FILE
```

### Load it

```bash
curl -X POST http://localhost:8080/api/files/$FILE/load
curl http://localhost:8080/api/files/$FILE/load
```

```json
{
  "status": "COMPLETED",
  "readCount": 1000,
  "writeCount": 1000,
  "skipCount": 0,
  "progressPercent": 100.0,
  "startTime": "2026-10-15T19:30:46.549",
  "endTime": "2026-10-15T19:30:47.102",
  "exitMessage": null
}
```

Stop a load in progress, then resume it from where it stopped:

```bash
curl -X POST http://localhost:8080/api/files/$FILE/load/stop
curl -X POST http://localhost:8080/api/files/$FILE/load/restart
```

### Run an operation

Click-through rate per row:

```bash
curl -H 'Content-Type: application/json' \
     -d '{"type":"OPERATION","leftColumn":"clicks","rightColumn":"impressions","operation":"DIVIDE"}' \
     http://localhost:8080/api/files/$FILE/process
```

```json
{
  "runId": "e8597810-4d1c-4b7e-9f0a-2c5d8e3b1a64",
  "type": "OPERATION",
  "settings": {"leftColumn": "clicks", "rightColumn": "impressions", "operation": "DIVIDE"},
  "status": "STARTING",
  "resultTable": "result.run_e85978104d1c4b7e9f0a2c5d8e3b1a64",
  "createdAt": "2026-10-15T17:31:02.418Z"
}
```

Check its progress, or stop and restart it:

```bash
RUN=e8597810-4d1c-4b7e-9f0a-2c5d8e3b1a64
curl http://localhost:8080/api/files/$FILE/process/$RUN
curl -X POST http://localhost:8080/api/files/$FILE/process/$RUN/stop
curl -X POST http://localhost:8080/api/files/$FILE/process/$RUN/restart
```

### Run an aggregation

Average revenue per advertiser:

```bash
curl -H 'Content-Type: application/json' \
     -d '{"type":"AGGREGATION","groupBy":"advertiserId","valueColumn":"revenue","function":"AVG"}' \
     http://localhost:8080/api/files/$FILE/process
```

Number of rows per ad box:

```bash
curl -H 'Content-Type: application/json' \
     -d '{"type":"AGGREGATION","groupBy":"adBoxUUID","valueColumn":"clicks","function":"COUNT"}' \
     http://localhost:8080/api/files/$FILE/process
```

### Browse the history

```bash
curl 'http://localhost:8080/api/files'
curl 'http://localhost:8080/api/files?status=LOADED&page=0&size=10'
curl 'http://localhost:8080/api/files?status=FAILED'
```

### Read a result table

A row operation's table holds `(row_number, result)`, and an aggregation's holds `(group_value, result)`. The table name is in the run's `resultTable` field.

```bash
docker compose exec postgres psql -U myuser mydatabase \
     -c 'SELECT * FROM result.run_e85978104d1c4b7e9f0a2c5d8e3b1a64 ORDER BY row_number LIMIT 5'
```

### Errors

Starting a run on a file that isn't loaded:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "File dcd8a8a2-3e67-42cf-af95-12201081d3da is READY, but only a LOADED file can be processed",
  "instance": "/api/files/dcd8a8a2-3e67-42cf-af95-12201081d3da/process"
}
```

Using a non-numeric column as an operand:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "leftColumn must be one of: impressions, clicks, revenue, advertiserMargin",
  "instance": "/api/files/dcd8a8a2-3e67-42cf-af95-12201081d3da/process"
}
```
