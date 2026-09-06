package io.adumun.platform.spring.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("adumun.observability")
public class AdumunObservabilityProperties {
    private String serviceName = "unknown-service";
    private String environment = "local";
    private String buildVersion = "unknown";
    private final Tracing tracing = new Tracing();

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getBuildVersion() { return buildVersion; }
    public void setBuildVersion(String buildVersion) { this.buildVersion = buildVersion; }
    public Tracing getTracing() { return tracing; }

    public static class Tracing {
        private boolean exportEnabled = false;
        private double samplingProbability = 0.1d;

        public boolean isExportEnabled() { return exportEnabled; }
        public void setExportEnabled(boolean exportEnabled) { this.exportEnabled = exportEnabled; }
        public double getSamplingProbability() { return samplingProbability; }
        public void setSamplingProbability(double samplingProbability) {
            if (samplingProbability < 0.0d || samplingProbability > 1.0d) {
                throw new IllegalArgumentException("samplingProbability must be between 0.0 and 1.0");
            }
            this.samplingProbability = samplingProbability;
        }
    }
}
