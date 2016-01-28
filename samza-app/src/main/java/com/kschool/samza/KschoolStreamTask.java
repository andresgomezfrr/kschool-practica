package com.kschool.samza;

import org.apache.samza.config.Config;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.task.*;

import java.util.HashMap;
import java.util.Map;

public class KschoolStreamTask implements StreamTask, InitableTask {
    private KeyValueStore<String, Map<String, Object>> store;
    private final SystemStream DRUID = new SystemStream("druid", "moving");
    private final SystemStream KAFKA = new SystemStream("kafka", "moving");

    @Override
    public void init(Config config, TaskContext taskContext) throws Exception {
        store = (KeyValueStore<String, Map<String, Object>>) taskContext.getStore("location");
    }

    @Override
    public void process(IncomingMessageEnvelope incomingMessageEnvelope, MessageCollector messageCollector, TaskCoordinator taskCoordinator) throws Exception {
        Map<String, Object> msg = (Map<String, Object>) incomingMessageEnvelope.getMessage();
        Long time = System.currentTimeMillis() / 1000L;

        String client = (String) msg.get("client");
        String currentBuilding = (String) msg.get("building");
        String currentFloor = (String) msg.get("floor");
        Map<String, Object> oldLocation = store.get(client);

        Map<String, Object> moving = new HashMap<>();
        moving.put("client", client);
        moving.put("timestamp", time);

        if (oldLocation == null) {
            moving.put("old_building", "unknown");
            moving.put("old_floor", "unknown");
            moving.put("new_building", currentBuilding);
            moving.put("building", currentBuilding);
            moving.put("new_floor", currentFloor);
            moving.put("floor", currentFloor);
            moving.put("time", 1);
            updateState(client, currentBuilding, currentFloor, time);
        } else {
            String oldBuilding = (String) oldLocation.get("building");
            String oldFloor = (String) oldLocation.get("floor");

            Boolean moved = false;

            if (!oldBuilding.equals(currentBuilding)) {
                moving.put("new_building", currentBuilding);
                moving.put("old_building", oldBuilding);
                moving.put("building", currentBuilding);
                moved = true;
            } else {
                moving.put("building", currentBuilding);
            }

            if (!oldFloor.equals(currentFloor)) {
                moving.put("old_floor", oldFloor);
                moving.put("new_floor", currentFloor);
                moving.put("floor", currentFloor);
                moved = true;
            } else {
                moving.put("floor", currentFloor);
            }

            if (moved) {
                moving.put("time", 1);
                updateState(client, currentBuilding, currentFloor, time);
            } else {
                Long oldTime = Long.parseLong(oldLocation.get("time").toString());
                Long totalTime = time - oldTime;

                moving.put("time", totalTime);
            }
        }

        messageCollector.send(new OutgoingMessageEnvelope(KAFKA, moving));
        messageCollector.send(new OutgoingMessageEnvelope(DRUID, moving));
    }

    private void updateState(String client, String newBuilding, String newFloor, Long newTime) {
        Map<String, Object> newLocation = new HashMap<>();
        newLocation.put("time", newTime);
        newLocation.put("building", newBuilding);
        newLocation.put("floor", newFloor);
        store.put(client, newLocation);
    }
}
