package io.hydrosphere.serving.clouddriver.swarm;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.swarm.*;
import io.hydrosphere.serving.service.runtime.Runtime;
import io.hydrosphere.serving.service.runtime.RuntimeDeployService;
import io.hydrosphere.serving.service.runtime.RuntimeInstance;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static io.hydrosphere.serving.service.runtime.RuntimeInstance.RuntimeInstanceStatus.DOWN;
import static io.hydrosphere.serving.service.runtime.RuntimeInstance.RuntimeInstanceStatus.UP;

/**
 *
 */
public class SwarmRuntimeDeployService implements RuntimeDeployService {

    public static final String LABEL_RUNTIME_TYPE = "runtimeType";
    public static final String LABEL_MODEL_NAME = "modelName";
    public static final String LABEL_MODEL_VERSION = "modelVersion";
    public static final String LABEL_HYDRO_SERVING_TYPE = "hydroServing";
    private static final String RUNTIME_TYPE = "runtime";
    private static final String ENV_HEADER_ENVOY_HTTP_PORT = "ENVOY_HTTP_PORT";
    public static final String LABEL_HTTP_PORT = "httpPort";
    public static final String LABEL_APP_HTTP_PORT = "appHttpPort";

    private final DockerClient dockerClient;

    private final String networkName;

    public SwarmRuntimeDeployService(DockerClient dockerClient, String networkName) {
        this.dockerClient = dockerClient;
        this.networkName = networkName;
    }

    @Override
    public String deploy(Runtime runtime) {
        Map<String, String> labels = new HashMap<>();
        List<String> env = new ArrayList<>();
        if (!CollectionUtils.isEmpty(runtime.getEnvironments())) {
            runtime.getEnvironments().entrySet().forEach((e) -> env.add(e.getKey() + "=" + e.getValue()));

            if (runtime.getEnvironments().containsKey(ENV_HEADER_ENVOY_HTTP_PORT)) {
                labels.put(LABEL_HTTP_PORT, runtime.getEnvironments().get(ENV_HEADER_ENVOY_HTTP_PORT));
            } else {
                labels.put(LABEL_HTTP_PORT, String.valueOf(runtime.getHttpPort()));
            }
        } else {
            labels.put(LABEL_HTTP_PORT, String.valueOf(runtime.getHttpPort()));
        }

        env.add("SERVICE_TYPE=" + RUNTIME_TYPE);

        labels.put(LABEL_HYDRO_SERVING_TYPE, RUNTIME_TYPE);
        labels.put(LABEL_RUNTIME_TYPE, runtime.getRuntimeType());
        labels.put(LABEL_MODEL_NAME, runtime.getModelName());
        labels.put(LABEL_MODEL_VERSION, runtime.getModelVersion());
        labels.put(LABEL_APP_HTTP_PORT, String.valueOf(runtime.getAppHttpPort()));

        ServiceSpec.Builder builder = ServiceSpec.builder()
                .name(runtime.getName())
                .labels(labels);

        if (StringUtils.hasText(networkName)) {
            builder.networks(NetworkAttachmentConfig.builder()
                    .target(networkName)
                    .build()
            );
        }
        builder.mode(ServiceMode.withReplicas(runtime.getScale()));

        TaskSpec taskSpec = TaskSpec.builder()
                .containerSpec(ContainerSpec.builder()
                        .image(runtime.getImageName())
                        .env(env)
                        .labels(labels)
                        .build()
                )
                //.placement()
                //.networks()
                //.resources()
                //.restartPolicy(RestartPolicy.builder().)
                .build();

        builder.endpointSpec(
                EndpointSpec.builder()
                        .mode(EndpointSpec.Mode.RESOLUTION_MODE_VIP)
                        .build())
                .taskTemplate(taskSpec)
                .build();
        try {
            return dockerClient.createService(builder.build()).id();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Runtime getRuntime(String runtimeName) {
        try {
            List<Service> services = dockerClient.listServices(Service.Criteria.builder()
                    .serviceName(runtimeName).build());
            if (services.isEmpty()) {
                return null;
            } else {
                return map(services.get(0));
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<RuntimeInstance> instances(Task.Criteria criteria) {
        try {
            String network = StringUtils.hasText(networkName) ? networkName : "bridge";
            List<RuntimeInstance> instances = new ArrayList<>();
            dockerClient.listTasks(criteria).forEach(p -> {
                RuntimeInstance instance = new RuntimeInstance();
                p.networkAttachments().forEach(n -> {
                    if (network.equals(n.network().spec().name())) {
                        String host = n.addresses().get(0);
                        if (host.contains("/")) {
                            host = host.substring(0, host.indexOf("/"));
                        }
                        instance.setHost(host);
                    }
                });
                if (!StringUtils.hasText(instance.getHost())) {
                    return;
                }

                String s = p.labels().get(LABEL_HTTP_PORT);
                if (StringUtils.hasText(s)) {
                    instance.setHttpPort(Integer.valueOf(s));
                }

                instance.setStatusText(p.status().message());
                instance.setStatus("running".equalsIgnoreCase(p.status().state()) ? UP : DOWN);
                instance.setRuntimeId(p.serviceId());
                instance.setId(p.status().containerStatus().containerId());
                instances.add(instance);
            });
            return instances;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<RuntimeInstance> runtimeInstances(String runtimeName) {
        return instances(Task.Criteria.builder()
                .serviceName(runtimeName).build());
    }

    @Override
    public void deleteRuntime(String runtimeId) {
        try {
            dockerClient.removeService(runtimeId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<RuntimeInstance> runtimeInstances() {
        return instances(Task.Criteria.builder().build());
    }

    @Override
    public List<Runtime> runtimeList() {
        try {
            List<Runtime> runtimeList = new ArrayList<>();
            dockerClient.listServices(Service.Criteria.builder()
                    //.addLabel(LABEL_HYDRO_SERVING_TYPE, RUNTIME_TYPE)
                    .build()
            ).forEach(s -> runtimeList.add(map(s)));
            return runtimeList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Runtime map(Service service) {
        Runtime runtime = new Runtime();
        ServiceSpec s = service.spec();
        runtime.setId(service.id());
        runtime.setName(s.name());
        runtime.setImageName(s.taskTemplate().containerSpec().image());
        if (s.taskTemplate().containerSpec().env() != null) {
            Map<String, String> map = s.taskTemplate().containerSpec().env().stream()
                    .map(en -> en.split(":"))
                    .filter(arr -> arr.length > 0 && arr[0] != null && arr[1] != null)
                    .collect(Collectors.toMap(arr -> arr[0], arr -> arr[1]));
            runtime.setEnvironments(map);
        } else {
            runtime.setEnvironments(Collections.emptyMap());
        }

        Map<String, String> map = s.labels();
        if (map != null) {
            runtime.setModelName(map.get(LABEL_MODEL_NAME));
            runtime.setModelVersion(map.get(LABEL_MODEL_VERSION));
            runtime.setRuntimeType(map.get(LABEL_RUNTIME_TYPE));
            runtime.setHttpPort(Integer.valueOf(map.get(LABEL_HTTP_PORT)));
            runtime.setAppHttpPort(Integer.valueOf(map.get(LABEL_APP_HTTP_PORT)));
        }

        UpdateStatus updateStatus = service.updateStatus();
        if (updateStatus != null) {
            runtime.setState(updateStatus.state());
            runtime.setStatusText(updateStatus.message());
        }

        if (s.mode().replicated() != null) {
            runtime.setScale(s.mode().replicated().replicas().intValue());
        }
        return runtime;
    }

    @Override
    public void scale(String runtimeName, int scale) {
        //TODO
    }
}
