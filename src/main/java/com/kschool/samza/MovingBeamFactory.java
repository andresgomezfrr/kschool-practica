package com.kschool.samza;

import com.google.common.collect.ImmutableList;
import com.metamx.common.Granularity;
import com.metamx.tranquility.beam.Beam;
import com.metamx.tranquility.beam.ClusteredBeamTuning;
import com.metamx.tranquility.druid.*;
import com.metamx.tranquility.samza.BeamFactory;
import com.metamx.tranquility.typeclass.Timestamper;
import io.druid.data.input.impl.TimestampSpec;
import io.druid.granularity.DurationGranularity;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.CountAggregatorFactory;
import io.druid.query.aggregation.hyperloglog.HyperUniquesAggregatorFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.samza.config.Config;
import org.apache.samza.system.SystemStream;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.List;
import java.util.Map;

public class MovingBeamFactory implements BeamFactory {

    @Override
    public Beam<Object> makeBeam(SystemStream stream, Config config) {
        final List<String> dimensions = ImmutableList.of(
                "client", "floor", "new_floor", "old_floor", "building", "new_building", "old_building"
        );

        final List<AggregatorFactory> aggregators = ImmutableList.of(
                new CountAggregatorFactory("events"),
                new HyperUniquesAggregatorFactory("clients", "client")
        );

        // The Timestamper should return the timestamp of the class your Samza task produces. Samza envelopes contain
        // Objects, so you'll generally have to cast them here.
        final Timestamper<Object> timestamper = new Timestamper<Object>() {
            @Override
            public DateTime timestamp(Object obj) {
                final Map<String, Object> theMap = (Map<String, Object>) obj;
                Long date = Long.parseLong(theMap.get("timestamp").toString());
                date = date * 1000;
                return new DateTime(date.longValue());
            }
        };

        final CuratorFramework curator = CuratorFrameworkFactory.builder()
                .connectString("localhost:2181")
                .retryPolicy(new ExponentialBackoffRetry(500, 15, 10000))
                .build();

        curator.start();

        return DruidBeams
                .builder(timestamper)
                .curator(curator)
                .discoveryPath("/druid/discovery")
                .location(DruidLocation.create("overlord", "druid:local:firehose:%s", stream.getStream()))
                .rollup(DruidRollup.create(DruidDimensions.specific(dimensions), aggregators, new DurationGranularity(60000L, 0)))
                .druidTuning(DruidTuning.create(5000, new Period("PT5m"), 0))
                .tuning(ClusteredBeamTuning.builder()
                        .partitions(1)
                        .replicants(1)
                        .segmentGranularity(Granularity.FIFTEEN_MINUTE)
                        .warmingPeriod(new Period("PT1M"))
                        .windowPeriod(new Period("PT5M"))
                        .build())
                .timestampSpec(new TimestampSpec("timestamp", "posix", null))
                .buildBeam();
    }
}
