package lab.sampleapp.orderplatform.plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

@Component
public class PluginCatalog {

    private final List<PluginDescriptor> descriptors = new CopyOnWriteArrayList<>();

    void register(PluginDescriptor descriptor) {
        descriptors.add(descriptor);
    }

    public List<PluginDescriptor> descriptors() {
        return List.copyOf(descriptors);
    }
}
