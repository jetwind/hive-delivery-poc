package com.hive.delivery.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ProjectEventStream {
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters=new ConcurrentHashMap<>();
    public SseEmitter subscribe(UUID projectId){
        var e=new SseEmitter(0L); emitters.computeIfAbsent(projectId,k->new CopyOnWriteArrayList<>()).add(e);
        e.onCompletion(()->remove(projectId,e)); e.onTimeout(()->remove(projectId,e)); e.onError(x->remove(projectId,e)); return e;
    }
    public void publish(UUID projectId,String type,Object data){
        for(var e:emitters.getOrDefault(projectId,new CopyOnWriteArrayList<>())) try{ e.send(SseEmitter.event().name(type).data(data)); }catch(IOException ex){remove(projectId,e);}
    }
    private void remove(UUID id,SseEmitter e){ emitters.getOrDefault(id,new CopyOnWriteArrayList<>()).remove(e); }
}
