package com.dataflow.dataflowsystem.filter.config;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZookeeperConfig {
    @Value("${zookeeper.connection-string}")
    private String zkConnection;
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Bean(initMethod = "start", destroyMethod = "close")
    public CuratorFramework curatorClient() {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        return CuratorFrameworkFactory.builder()
                .connectString(zkConnection)
                .retryPolicy(retryPolicy)
                .namespace("dataflow/" + applicationName)
                .build();
    }
}