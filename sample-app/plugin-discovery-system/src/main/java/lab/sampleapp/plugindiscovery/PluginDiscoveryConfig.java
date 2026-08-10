package lab.sampleapp.plugindiscovery;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = PluginDiscoveryConfig.class)
public class PluginDiscoveryConfig {
}
