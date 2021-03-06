# Last value price service

Service for keeping track of the last price for financial instruments.
Producers will use the service to publish prices and consumers will use it to obtain them.

## Domain

This section describes domain objects.

### Price data

The price data consists of records with the following fields:
* `id`: a string field to indicate which instrument this price refers to.
* `asOf`: a date time field to indicate when the price was determined.
* `payload`: the price data itself, which is a flexible data structure.

### Price producer

Producers should be able to provide prices in batch runs.
The sequence of uploading a batch run is as follows:
1. The producer indicates that a batch run is started
2. The producer uploads the records in the batch run in multiple chunks of 1000 records.
3. The producer completes or cancels the `batch run`.

### Batch run

Batch run is a session of uploading chunks.

### Price consumer

Consumers can request the last price record for a given id.
The last value is determined by the asOf time, as set by the producer.
On completion, all prices in a batch run should be made available at the same time.
Batch runs which are cancelled can be discarded.

## Nonfunctional requirements.

The service should be resilient against producers which call the service methods in an incorrect order,
or clients which call the service while a batch is being processed.

## System design

System implements simple client/server protocol based on Aeron.
The protocol was discussed here: https://github.com/real-logic/aeron/issues/652

This implementation also introduce concept of independent local clients which can be used in separate threads.
When client process is started it creates Gateway (`AbstractServiceGateway`) to access service.
Gateway manages connection to server as pair of Aeron Piblication and Subscription.

Clients (see AbstractServiceClient) sends requests to server writing them through the Gateway's publication (Concurrent Publication is used).
Gateway runs single read cycle (see `com.xxx.core.client.AbstractServiceGateway.ReadCycleTask`) in separate thread to read responces from the server (Aeron Subscription).
When `ReadCycleTask` receives a message from server and redirects it to right local client writing to client's `OneToOneRingBuffer`.

Server use dedicated channel for all incomming connections (at the moment admin messages are also running there).

Server works in single thread mode. Main loop recieves messages from the Aeron Subscription, react on them (business logic or administration) and replies to clients.

All application level messages have header with the following fields:
* connectionId - client sets it in each request, using this number server can define to which send message back. Generated by server.
* clientId - used to redirect responses from main Gateway's read thread to the right client object (I know may be it's confusing name, could not create better)
* correlationId - unique id per request/response pair generated by client.

All components are trying to avoid memory allocations during the whole message processing path.
All messages and internal structures use `Flyweight` pattern wraping direct buffers.

## Data model

Server does not save all records client sends in chunks. Instead of it updates state of the batch run.
Batch run (`com.xxx.service.lastprice.BatchRun`) presents aggregation view of all previous successful chunk uploading operations.
BatchRun objects are managed by repository which tracks last batch usage time, it allows to delete abandoned batch runs and return them to the repository's pool.

If client decides to cancel batch run it is just returned to the repository pool and can be reused.
If batch is completed it is merging to the similar structure `MarketState`. All read operations will be done from market state.

Process of chunk uploading uses additional operational batch run structure (com.xxx.service.lastprice.LastPriceServiceHandler.operationalBatchRun) to ensure all chunk records will be writen or none of them.
When operational batch run is filled it is merged to the right batch run.

## How to run

Examples of client/service communications are presented in `samples` module as performance tests.
Tests assume clients and server will be run on the same machine.

To run a perfomance test three steps are needed to complete:
1. Run external Aeron Media Driver on the host. Package has low latency media driver runner implementation `LowLatencyMediaDriver`
2. Start the service running `LastPriceServiceRunner`
3. Start one of the performance test you need to check results for

If service and clients are on different hosts, then each host should run own media driver process.

## Performance tests

Several scenarious were implemented to test system perfomance in terms of request/responce latency.

### Start and Cancel batch run

This test is implemeted in `StartCancelBatchRunLatencyTest` class.
Test measures latency of **two** conséquent operations: creation and cancelation of batches.
Results in the table (results are in **micro**seconds):

```
       Value     Percentile TotalCount 1/(1-Percentile)

      32.543 0.000000000000          1           1.00
      43.039 0.100000000000      10047           1.11
      46.015 0.200000000000      20015           1.25
      48.095 0.300000000000      30243           1.43
      49.439 0.400000000000      40066           1.67
      50.655 0.500000000000      50230           2.00
      51.327 0.550000000000      55055           2.22
      52.159 0.600000000000      60155           2.50
      53.023 0.650000000000      65015           2.86
      54.527 0.700000000000      70041           3.33
      56.575 0.750000000000      75051           4.00
      57.823 0.775000000000      77502           4.44
      59.359 0.800000000000      80023           5.00
      61.215 0.825000000000      82523           5.71
      63.391 0.850000000000      85003           6.67
      66.239 0.875000000000      87502           8.00
      68.223 0.887500000000      88784           8.89
      70.271 0.900000000000      90017          10.00
      72.767 0.912500000000      91257          11.43
      75.647 0.925000000000      92504          13.33
      79.103 0.937500000000      93772          16.00
      81.023 0.943750000000      94387          17.78
      83.327 0.950000000000      95015          20.00
      85.823 0.956250000000      95632          22.86
      89.023 0.962500000000      96264          26.67
      92.927 0.968750000000      96877          32.00
      95.551 0.971875000000      97190          35.56
      99.199 0.975000000000      97502          40.00
     104.063 0.978125000000      97814          45.71
     110.015 0.981250000000      98128          53.33
     118.911 0.984375000000      98439          64.00
     124.799 0.985937500000      98596          71.11
     131.967 0.987500000000      98752          80.00
     142.591 0.989062500000      98907          91.43
     159.103 0.990625000000      99064         106.67
     179.839 0.992187500000      99220         128.00
     193.791 0.992968750000      99297         142.22
     214.143 0.993750000000      99375         160.00
     241.279 0.994531250000      99454         182.86
     279.295 0.995312500000      99533         213.33
     341.247 0.996093750000      99610         256.00
     371.199 0.996484375000      99649         284.44
     410.623 0.996875000000      99688         320.00
     456.447 0.997265625000      99727         365.71
     510.207 0.997656250000      99766         426.67
     600.575 0.998046875000      99805         512.00
     674.303 0.998242187500      99826         568.89
     712.703 0.998437500000      99844         640.00
     816.639 0.998632812500      99864         731.43
     900.095 0.998828125000      99883         853.33
    1003.519 0.999023437500      99903        1024.00
    1142.783 0.999121093750      99913        1137.78
    1224.703 0.999218750000      99922        1280.00
    1339.391 0.999316406250      99932        1462.86
    1538.047 0.999414062500      99942        1706.67
    1674.239 0.999511718750      99952        2048.00
    1762.303 0.999560546875      99957        2275.56
    1899.519 0.999609375000      99961        2560.00
    2285.567 0.999658203125      99966        2925.71
    2502.655 0.999707031250      99971        3413.33
    3004.415 0.999755859375      99976        4096.00
    3557.375 0.999780273438      99979        4551.11
    3764.223 0.999804687500      99981        5120.00
    4689.919 0.999829101563      99983        5851.43
    5533.695 0.999853515625      99986        6826.67
    6008.831 0.999877929688      99988        8192.00
    6348.799 0.999890136719      99990        9102.22
    6664.191 0.999902343750      99991       10240.00
    7491.583 0.999914550781      99992       11702.86
    8228.863 0.999926757813      99993       13653.33
    9076.735 0.999938964844      99994       16384.00
   12320.767 0.999945068359      99995       18204.44
   14376.959 0.999951171875      99996       20480.00
   14376.959 0.999957275391      99996       23405.71
   16203.775 0.999963378906      99997       27306.67
   16203.775 0.999969482422      99997       32768.00
   18153.471 0.999972534180      99998       36408.89
   18153.471 0.999975585938      99998       40960.00
   18153.471 0.999978637695      99998       46811.43
   22298.623 0.999981689453      99999       54613.33
   22298.623 0.999984741211      99999       65536.00
   22298.623 0.999986267090      99999       72817.78
   22298.623 0.999987792969      99999       81920.00
   22298.623 0.999989318848      99999       93622.86
   24838.143 0.999990844727     100000      109226.67
   24838.143 1.000000000000     100000
#[Mean    =       59.587, StdDeviation   =      171.029]
#[Max     =    24838.143, Total count    =       100000]
#[Buckets =           24, SubBuckets     =         2048]

```

### Upload max size chunks

This test measures latency of chunk uploading operation.
On each iteration test creates new batch run, new chunk of size 1000, upload the chunk and the complete the batch.
Test measures performance of each operation separately. Results are in the following tables.

#### START_BATCH_HISTOGRAM
```
       Value     Percentile TotalCount 1/(1-Percentile)

      16.039 0.000000000000          1           1.00
      19.503 0.100000000000       1025           1.11
      20.383 0.200000000000       2014           1.25
      21.391 0.300000000000       3005           1.43
      22.399 0.400000000000       4004           1.67
      23.151 0.500000000000       5024           2.00
      23.423 0.550000000000       5512           2.22
      23.759 0.600000000000       6006           2.50
      24.271 0.650000000000       6514           2.86
      24.703 0.700000000000       7005           3.33
      25.263 0.750000000000       7506           4.00
      25.679 0.775000000000       7751           4.44
      26.623 0.800000000000       8000           5.00
      27.839 0.825000000000       8252           5.71
      29.247 0.850000000000       8501           6.67
      30.447 0.875000000000       8750           8.00
      31.103 0.887500000000       8875           8.89
      31.935 0.900000000000       9000          10.00
      32.959 0.912500000000       9126          11.43
      34.015 0.925000000000       9250          13.33
      35.231 0.937500000000       9378          16.00
      35.999 0.943750000000       9438          17.78
      36.575 0.950000000000       9501          20.00
      37.599 0.956250000000       9565          22.86
      38.815 0.962500000000       9625          26.67
      40.991 0.968750000000       9689          32.00
      41.919 0.971875000000       9720          35.56
      42.463 0.975000000000       9750          40.00
      43.359 0.978125000000       9782          45.71
      44.383 0.981250000000       9814          53.33
      45.375 0.984375000000       9844          64.00
      46.239 0.985937500000       9862          71.11
      46.847 0.987500000000       9875          80.00
      47.679 0.989062500000       9891          91.43
      48.447 0.990625000000       9907         106.67
      49.151 0.992187500000       9922         128.00
      49.407 0.992968750000       9930         142.22
      50.015 0.993750000000       9938         160.00
      50.687 0.994531250000       9947         182.86
      51.871 0.995312500000       9954         213.33
      53.695 0.996093750000       9961         256.00
      54.463 0.996484375000       9965         284.44
      56.223 0.996875000000       9970         320.00
      59.903 0.997265625000       9973         365.71
      65.247 0.997656250000       9977         426.67
      73.343 0.998046875000       9981         512.00
      79.551 0.998242187500       9983         568.89
      83.327 0.998437500000       9985         640.00
      86.143 0.998632812500       9987         731.43
     117.247 0.998828125000       9989         853.33
     126.271 0.999023437500       9991        1024.00
     128.639 0.999121093750       9992        1137.78
     130.623 0.999218750000       9993        1280.00
     181.503 0.999316406250       9994        1462.86
     290.047 0.999414062500       9995        1706.67
     291.839 0.999511718750       9996        2048.00
     291.839 0.999560546875       9996        2275.56
     319.231 0.999609375000       9997        2560.00
     319.231 0.999658203125       9997        2925.71
     383.231 0.999707031250       9998        3413.33
     383.231 0.999755859375       9998        4096.00
     383.231 0.999780273438       9998        4551.11
     419.583 0.999804687500       9999        5120.00
     419.583 0.999829101563       9999        5851.43
     419.583 0.999853515625       9999        6826.67
     419.583 0.999877929688       9999        8192.00
     419.583 0.999890136719       9999        9102.22
     599.551 0.999902343750      10000       10240.00
     599.551 1.000000000000      10000
#[Mean    =       24.825, StdDeviation   =       11.291]
#[Max     =      599.551, Total count    =        10000]
#[Buckets =           24, SubBuckets     =         2048]
```

#### UPLOAD_CHUNK_HISTOGRAM
```
       Value     Percentile TotalCount 1/(1-Percentile)

     232.831 0.000000000000          1           1.00
     276.991 0.100000000000       1016           1.11
     286.207 0.200000000000       2013           1.25
     293.631 0.300000000000       3027           1.43
     299.519 0.400000000000       4018           1.67
     306.943 0.500000000000       5000           2.00
     311.039 0.550000000000       5533           2.22
     315.391 0.600000000000       6008           2.50
     321.535 0.650000000000       6506           2.86
     327.935 0.700000000000       7000           3.33
     334.079 0.750000000000       7507           4.00
     338.687 0.775000000000       7762           4.44
     344.063 0.800000000000       8002           5.00
     352.511 0.825000000000       8253           5.71
     361.215 0.850000000000       8503           6.67
     372.991 0.875000000000       8751           8.00
     380.927 0.887500000000       8879           8.89
     389.887 0.900000000000       9001          10.00
     401.919 0.912500000000       9127          11.43
     423.423 0.925000000000       9250          13.33
     455.679 0.937500000000       9375          16.00
     499.199 0.943750000000       9438          17.78
     547.839 0.950000000000       9501          20.00
     595.967 0.956250000000       9563          22.86
     614.399 0.962500000000       9627          26.67
     630.783 0.968750000000       9689          32.00
     639.487 0.971875000000       9720          35.56
     646.655 0.975000000000       9751          40.00
     654.847 0.978125000000       9782          45.71
     665.087 0.981250000000       9813          53.33
     675.327 0.984375000000       9844          64.00
     684.031 0.985937500000       9860          71.11
     690.175 0.987500000000       9876          80.00
     697.343 0.989062500000       9891          91.43
     703.487 0.990625000000       9908         106.67
     706.559 0.992187500000       9922         128.00
     709.631 0.992968750000       9930         142.22
     712.703 0.993750000000       9938         160.00
     716.799 0.994531250000       9947         182.86
     720.895 0.995312500000       9954         213.33
     724.479 0.996093750000       9961         256.00
     729.599 0.996484375000       9965         284.44
     731.647 0.996875000000       9969         320.00
     735.743 0.997265625000       9973         365.71
     777.215 0.997656250000       9977         426.67
     859.135 0.998046875000       9981         512.00
     875.519 0.998242187500       9983         568.89
    1139.711 0.998437500000       9985         640.00
    1223.679 0.998632812500       9987         731.43
    1401.855 0.998828125000       9989         853.33
    1503.231 0.999023437500       9991        1024.00
    1606.655 0.999121093750       9992        1137.78
    2024.447 0.999218750000       9993        1280.00
    2136.063 0.999316406250       9994        1462.86
    2420.735 0.999414062500       9995        1706.67
    2666.495 0.999511718750       9996        2048.00
    2666.495 0.999560546875       9996        2275.56
    2818.047 0.999609375000       9997        2560.00
    2818.047 0.999658203125       9997        2925.71
    3096.575 0.999707031250       9998        3413.33
    3096.575 0.999755859375       9998        4096.00
    3096.575 0.999780273438       9998        4551.11
    4069.375 0.999804687500       9999        5120.00
    4069.375 0.999829101563       9999        5851.43
    4069.375 0.999853515625       9999        6826.67
    4069.375 0.999877929688       9999        8192.00
    4069.375 0.999890136719       9999        9102.22
    4304.895 0.999902343750      10000       10240.00
    4304.895 1.000000000000      10000
#[Mean    =      333.507, StdDeviation   =      117.132]
#[Max     =     4304.895, Total count    =        10000]
#[Buckets =           24, SubBuckets     =         2048]
```

#### COMPLETE_BATCH_HISTOGRAM:
```
       Value     Percentile TotalCount 1/(1-Percentile)

      17.727 0.000000000000          1           1.00
      20.895 0.100000000000       1007           1.11
      22.143 0.200000000000       2015           1.25
      22.895 0.300000000000       3021           1.43
      23.439 0.400000000000       4027           1.67
      24.047 0.500000000000       5019           2.00
      24.399 0.550000000000       5517           2.22
      24.719 0.600000000000       6009           2.50
      25.151 0.650000000000       6511           2.86
      25.791 0.700000000000       7000           3.33
      26.895 0.750000000000       7501           4.00
      27.599 0.775000000000       7764           4.44
      28.351 0.800000000000       8008           5.00
      29.407 0.825000000000       8251           5.71
      30.575 0.850000000000       8506           6.67
      31.935 0.875000000000       8753           8.00
      32.543 0.887500000000       8875           8.89
      33.375 0.900000000000       9005          10.00
      33.983 0.912500000000       9131          11.43
      34.751 0.925000000000       9252          13.33
      36.191 0.937500000000       9376          16.00
      37.119 0.943750000000       9439          17.78
      38.559 0.950000000000       9500          20.00
      39.903 0.956250000000       9564          22.86
      41.919 0.962500000000       9625          26.67
      43.871 0.968750000000       9688          32.00
      44.575 0.971875000000       9719          35.56
      45.055 0.975000000000       9751          40.00
      46.271 0.978125000000       9783          45.71
      47.839 0.981250000000       9813          53.33
      49.087 0.984375000000       9844          64.00
      49.695 0.985937500000       9861          71.11
      50.399 0.987500000000       9876          80.00
      50.815 0.989062500000       9891          91.43
      51.551 0.990625000000       9907         106.67
      52.671 0.992187500000       9922         128.00
      53.183 0.992968750000       9930         142.22
      53.951 0.993750000000       9939         160.00
      55.583 0.994531250000       9946         182.86
      59.039 0.995312500000       9954         213.33
      60.575 0.996093750000       9961         256.00
      63.199 0.996484375000       9965         284.44
      66.047 0.996875000000       9969         320.00
      71.487 0.997265625000       9973         365.71
      79.615 0.997656250000       9977         426.67
      89.599 0.998046875000       9981         512.00
     101.887 0.998242187500       9983         568.89
     114.367 0.998437500000       9985         640.00
     117.183 0.998632812500       9987         731.43
     138.495 0.998828125000       9989         853.33
     211.199 0.999023437500       9991        1024.00
     351.999 0.999121093750       9992        1137.78
     386.303 0.999218750000       9993        1280.00
     416.511 0.999316406250       9994        1462.86
     562.175 0.999414062500       9995        1706.67
     676.863 0.999511718750       9996        2048.00
     676.863 0.999560546875       9996        2275.56
     808.447 0.999609375000       9997        2560.00
     808.447 0.999658203125       9997        2925.71
    1612.799 0.999707031250       9998        3413.33
    1612.799 0.999755859375       9998        4096.00
    1612.799 0.999780273438       9998        4551.11
    2383.871 0.999804687500       9999        5120.00
    2383.871 0.999829101563       9999        5851.43
    2383.871 0.999853515625       9999        6826.67
    2383.871 0.999877929688       9999        8192.00
    2383.871 0.999890136719       9999        9102.22
    2392.063 0.999902343750      10000       10240.00
    2392.063 1.000000000000      10000
#[Mean    =       26.899, StdDeviation   =       39.841]
#[Max     =     2392.063, Total count    =        10000]
#[Buckets =           24, SubBuckets     =         2048]
```

### Last price request

```
       Value     Percentile TotalCount 1/(1-Percentile)

      14.271 0.000000000000          1           1.00
      19.327 0.100000000000      10138           1.11
      19.839 0.200000000000      20206           1.25
      20.719 0.300000000000      30049           1.43
      21.967 0.400000000000      40064           1.67
      22.975 0.500000000000      50216           2.00
      23.263 0.550000000000      55113           2.22
      23.599 0.600000000000      60142           2.50
      24.255 0.650000000000      65057           2.86
      24.847 0.700000000000      70079           3.33
      26.063 0.750000000000      75022           4.00
      27.279 0.775000000000      77511           4.44
      28.543 0.800000000000      80009           5.00
      29.439 0.825000000000      82560           5.71
      30.111 0.850000000000      85016           6.67
      31.135 0.875000000000      87529           8.00
      31.759 0.887500000000      88782           8.89
      32.735 0.900000000000      90003          10.00
      33.599 0.912500000000      91261          11.43
      34.335 0.925000000000      92528          13.33
      35.327 0.937500000000      93753          16.00
      35.807 0.943750000000      94416          17.78
      36.319 0.950000000000      95000          20.00
      37.567 0.956250000000      95631          22.86
      39.935 0.962500000000      96257          26.67
      41.343 0.968750000000      96900          32.00
      41.599 0.971875000000      97238          35.56
      41.823 0.975000000000      97502          40.00
      42.303 0.978125000000      97826          45.71
      43.103 0.981250000000      98131          53.33
      43.583 0.984375000000      98456          64.00
      43.839 0.985937500000      98607          71.11
      44.287 0.987500000000      98752          80.00
      45.151 0.989062500000      98912          91.43
      45.727 0.990625000000      99070         106.67
      46.335 0.992187500000      99230         128.00
      46.815 0.992968750000      99298         142.22
      47.167 0.993750000000      99382         160.00
      47.487 0.994531250000      99456         182.86
      47.839 0.995312500000      99535         213.33
      48.255 0.996093750000      99614         256.00
      48.479 0.996484375000      99652         284.44
      48.767 0.996875000000      99688         320.00
      49.055 0.997265625000      99730         365.71
      49.439 0.997656250000      99767         426.67
      49.983 0.998046875000      99805         512.00
      50.495 0.998242187500      99825         568.89
      51.391 0.998437500000      99844         640.00
      52.863 0.998632812500      99865         731.43
      55.263 0.998828125000      99883         853.33
      58.015 0.999023437500      99903        1024.00
      58.975 0.999121093750      99913        1137.78
      60.255 0.999218750000      99923        1280.00
      63.903 0.999316406250      99932        1462.86
      71.935 0.999414062500      99942        1706.67
      89.791 0.999511718750      99952        2048.00
      98.047 0.999560546875      99957        2275.56
     112.639 0.999609375000      99961        2560.00
     132.607 0.999658203125      99966        2925.71
     168.703 0.999707031250      99971        3413.33
     205.823 0.999755859375      99976        4096.00
     221.951 0.999780273438      99979        4551.11
     222.207 0.999804687500      99981        5120.00
     268.543 0.999829101563      99983        5851.43
     316.159 0.999853515625      99986        6826.67
     374.015 0.999877929688      99988        8192.00
     456.191 0.999890136719      99990        9102.22
     546.815 0.999902343750      99991       10240.00
     693.247 0.999914550781      99992       11702.86
     739.839 0.999926757813      99993       13653.33
     748.543 0.999938964844      99994       16384.00
     821.759 0.999945068359      99995       18204.44
    1011.711 0.999951171875      99996       20480.00
    1011.711 0.999957275391      99996       23405.71
    1116.159 0.999963378906      99997       27306.67
    1116.159 0.999969482422      99997       32768.00
    1373.183 0.999972534180      99998       36408.89
    1373.183 0.999975585938      99998       40960.00
    1373.183 0.999978637695      99998       46811.43
    3274.751 0.999981689453      99999       54613.33
    3274.751 0.999984741211      99999       65536.00
    3274.751 0.999986267090      99999       72817.78
    3274.751 0.999987792969      99999       81920.00
    3274.751 0.999989318848      99999       93622.86
    3760.127 0.999990844727     100000      109226.67
    3760.127 1.000000000000     100000
#[Mean    =       24.694, StdDeviation   =       18.901]
#[Max     =     3760.127, Total count    =       100000]
#[Buckets =           24, SubBuckets     =         2048]
```

## Limitations

The service and clients are assumed to work on controlled network.
Possible issues on public networks (I did't test it yet)
https://github.com/real-logic/aeron/issues/697

