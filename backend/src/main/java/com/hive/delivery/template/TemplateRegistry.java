package com.hive.delivery.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hive.delivery.config.DeliveryProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.hive.delivery.template.TemplateModels.*;

@Component
public class TemplateRegistry {
    private final DeliveryProperties properties;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final Map<String, LifecycleTemplate> lifecycles = new ConcurrentHashMap<>();
    private final Map<String, StageTemplate> stages = new ConcurrentHashMap<>();
    public TemplateRegistry(DeliveryProperties properties){ this.properties=properties; }
    @PostConstruct public void init() throws IOException { reload(); }
    public synchronized void reload() throws IOException {
        lifecycles.clear(); stages.clear();
        Path base=Path.of(properties.templatesPath()).toAbsolutePath().normalize();
        load(base.resolve("lifecycles"), LifecycleTemplate.class, t -> lifecycles.put(t.metadata().code()+":"+t.metadata().version(), t));
        load(base.resolve("stages"), StageTemplate.class, t -> stages.put(t.metadata().code()+":"+t.metadata().version(), t));
        if(lifecycles.isEmpty()) throw new IllegalStateException("No lifecycle YAML found under "+base);
    }
    private <T> void load(Path dir, Class<T> type, java.util.function.Consumer<T> sink) throws IOException {
        if(!Files.exists(dir)) return;
        try(var files=Files.list(dir)){ files.filter(p->p.toString().endsWith(".yaml")).sorted().forEach(p->{
            try { sink.accept(yaml.readValue(p.toFile(), type)); } catch(Exception e){ throw new RuntimeException("Invalid YAML: "+p,e); }
        }); }
    }
    public LifecycleTemplate lifecycle(String code,String version){ return Optional.ofNullable(lifecycles.get(code+":"+version)).orElseThrow(); }
    public StageTemplate stageByFile(String file){
        String n=Path.of(file).getFileName().toString();
        return stages.values().stream().filter(s->n.startsWith(s.metadata().code()+"-") || n.contains(s.metadata().code())).findFirst().orElseThrow();
    }
    public Collection<LifecycleTemplate> lifecycles(){ return lifecycles.values(); }
}
