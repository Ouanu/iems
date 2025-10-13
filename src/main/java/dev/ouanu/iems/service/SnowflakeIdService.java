package dev.ouanu.iems.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.ouanu.iems.constant.BizType;
import dev.ouanu.iems.entity.SnowflakeId;
import dev.ouanu.iems.mapper.SnowflakeIdMapper;

@Service
public class SnowflakeIdService {

    private final SnowflakeIdMapper mapper;

    // Snowflake bit allocation (典型配置)
    private static final long TWEPOCH = 1672531200000L; // 自定义 epoch（示例：2023-01-01）
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private long datacenterId;
    private long workerId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SnowflakeIdService.class);

    @Value("${snowflake.node:unknown}")
    private String nodeName;

    public SnowflakeIdService(SnowflakeIdMapper mapper,
                              @Value("${snowflake.datacenter:1}") long datacenterId,
                              @Value("${snowflake.worker:1}") long workerId) {
        this.mapper = mapper;
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId out of range");
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
        try {
            // 如果 nodeName 未配置，自动使用主机名
            if (nodeName == null || nodeName.isBlank()) {
                nodeName = InetAddress.getLocalHost().getHostName();
            }
        } catch (UnknownHostException ignored) {
            logger.warn("Cannot get hostname, using 'unknown' as node name");
        }
        // ensure table exists
        mapper.createTableIfNotExists();
    }

    // 主方法：生成并持久化 ID
    public synchronized long nextIdAndPersist(BizType biz) {
        long id = nextId();
        SnowflakeId rec = new SnowflakeId();
        rec.setId(id);
        rec.setType(biz.getName());
        rec.setNode(nodeName);
        rec.setCreatedAt(Instant.now());
        mapper.insert(rec);
        return id;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long nextId() {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id for " + (lastTimestamp - timestamp) + "ms");
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }
}