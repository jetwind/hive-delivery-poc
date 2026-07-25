package com.hive.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hive.delivery.domain.DeliveryEvent;
import com.hive.delivery.repo.DeliveryEventRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;
import static com.hive.delivery.domain.Enums.EventType;

@Service
public class EventService {
    private final DeliveryEventRepository repo; private final ProjectEventStream stream; private final ObjectMapper json;
    public EventService(DeliveryEventRepository repo,ProjectEventStream stream,ObjectMapper json){this.repo=repo;this.stream=stream;this.json=json;}
    public DeliveryEvent emit(UUID projectId, EventType type, UUID nodeId, Object payload){
        String body; try{body=payload==null?"{}":json.writeValueAsString(payload);}catch(Exception e){body="{\"error\":\"serialization\"}";}
        var event=repo.save(DeliveryEvent.of(projectId,type,nodeId,body)); stream.publish(projectId,type.name(),event); return event;
    }
}
