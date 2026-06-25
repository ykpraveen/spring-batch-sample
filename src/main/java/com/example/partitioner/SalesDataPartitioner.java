package com.example.partitioner;

import com.example.batch.BatchProperties;
import com.example.repository.SalesDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class SalesDataPartitioner implements Partitioner {

    private final SalesDataRepository salesDataRepository;
    private final int partitionSize;

    public SalesDataPartitioner(SalesDataRepository salesDataRepository, BatchProperties batchProperties) {
        this.salesDataRepository = salesDataRepository;
        this.partitionSize = batchProperties.partitioning().partitionSize();
    }
    
    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();

        Long minId = salesDataRepository.findMinUnprocessedId();
        Long maxId = salesDataRepository.findMaxId();
        Long totalRecords = salesDataRepository.countUnprocessed();

        log.debug("Partitioning {} unprocessed records (ids {}-{}, gridSize={}, partitionSize={})",
                totalRecords, minId, maxId, gridSize, partitionSize);

        if (minId == null || maxId == null || totalRecords == 0) {
            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", 0);
            context.putLong("maxId", 0);
            context.putInt("partitionId", 0);
            partitions.put("partition-0", context);
            log.debug("No unprocessed records found, returning single empty partition");
            return partitions;
        }

        long idRange = maxId - minId;

        if (idRange == 0) {
            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", minId);
            context.putLong("maxId", maxId);
            context.putInt("partitionId", 0);
            partitions.put("partition-0", context);
            log.debug("Only one record in range, returning single partition");
            return partitions;
        }

        int numPartitions = Math.min(gridSize, (int) Math.ceil((double) totalRecords / partitionSize));

        long recordsPerPartition = (long) Math.ceil((double) totalRecords / numPartitions);

        log.debug("Creating {} partitions, ~{} records each", numPartitions, recordsPerPartition);

        for (int i = 0; i < numPartitions; i++) {
            ExecutionContext context = new ExecutionContext();

            long partitionMinId;
            long partitionMaxId;

            if (i == 0) {
                partitionMinId = minId;
            } else {
                partitionMinId = minId + i * idRange / numPartitions;
            }

            if (i == numPartitions - 1) {
                partitionMaxId = maxId;
            } else {
                partitionMaxId = minId + (i + 1) * idRange / numPartitions - 1;
            }

            context.putLong("minId", partitionMinId);
            context.putLong("maxId", partitionMaxId);
            context.putInt("partitionId", i);

            partitions.put("partition-" + i, context);

            log.debug("  Partition {}: ids {}-{}", i, partitionMinId, partitionMaxId);
        }

        return partitions;
    }
}
