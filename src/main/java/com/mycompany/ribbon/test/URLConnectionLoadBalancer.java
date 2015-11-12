/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.ribbon.test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import rx.Observable;

import com.google.common.collect.Lists;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.PingUrl;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.netflix.loadbalancer.reactive.ServerOperation;

public class URLConnectionLoadBalancer {

  private final ILoadBalancer loadBalancer;
  // retry handler that does not retry on same server, but on a different server
  private final RetryHandler retryHandler = new DefaultLoadBalancerRetryHandler(0, 1, true);

  public URLConnectionLoadBalancer(List<Server> serverList) {
    loadBalancer = LoadBalancerBuilder.newBuilder()
      .withRule(new RoundRobinRule())
      .withPing(new PingUrl())
      .buildFixedServerListLoadBalancer(serverList);
  }

  public String call(final String path) throws Exception {
    return LoadBalancerCommand.<String>builder()
      .withLoadBalancer(loadBalancer)
      .withRetryHandler(retryHandler)
      .build()
      .submit(new ServerOperation<String>() {
        @Override
        public Observable<String> call(Server server) {
          URL url;
          try {
            url = new URL("http://" + server.getHost() + ":" + server.getPort() + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            return Observable.just(conn.getResponseMessage());
          } catch (Exception e) {
            return Observable.error(e);
          }
        }
      }).toBlocking().first();
  }

  public LoadBalancerStats getLoadBalancerStats() {
    return ((BaseLoadBalancer) loadBalancer).getLoadBalancerStats();
  }

  public static void main(String[] args) throws Exception {
    URLConnectionLoadBalancer urlLoadBalancer = new URLConnectionLoadBalancer(Lists.newArrayList(
      new Server("www.google.com", 80),
      new Server("www.linkedin.com", 80),
      new Server("www.yahoo.com", 80)));
    for (int i = 0; i < 6; i++) {
      System.out.println(urlLoadBalancer.call("/"));
    }
    System.out.println("=== Load balancer stats ===");
    System.out.println(urlLoadBalancer.getLoadBalancerStats());
  }
}
