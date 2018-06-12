/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gossip;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.gossip.manager.DatacenterRackAwareActiveGossiper;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.manager.GossipManagerBuilder;
import org.apache.gossip.model.SharedDataMessage;
import org.apache.gossip.replication.AllReplicable;
import org.apache.gossip.replication.DataCenterReplicable;
import org.apache.gossip.replication.Replicable;
import org.junit.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import io.teknek.tunit.TUnit;

@RunWith(JUnitPlatform.class)
public class SharedDataReplicationControl2Test extends AbstractIntegrationBase {

  // Can be run with eg 10000 data as gossip performance test (with apache-gossip debugging disabled, or it will fail due to load):
  private static final int NUM_DATA = 1000;

  @Test
  public void sharedDataDcReplicationTest()
          throws InterruptedException, UnknownHostException, URISyntaxException {

    GossipSettings settings = new GossipSettings();
    settings.setPersistRingState(false);
    settings.setPersistDataState(false);
    String cluster = UUID.randomUUID().toString();
    settings.setActiveGossipClass(DatacenterRackAwareActiveGossiper.class.getName());

    Map<String, String> gossipProps = new HashMap<>();
    gossipProps.put("sameRackGossipIntervalMs", "500");
    gossipProps.put("differentDatacenterGossipIntervalMs", "1000");
    settings.setActiveGossipProperties(gossipProps);

    RemoteMember seeder = new RemoteMember(cluster, URI.create("udp://127.0.0.1:5001"), "1");

    // initialize 2 data centers with each having two racks
    createDcNode(URI.create("udp://127.0.0.1:5001"), "1", settings, seeder, cluster,
            "DataCenter1", "Rack1");
    createDcNode(URI.create("udp://127.0.0.1:5002"), "2", settings, seeder, cluster,
            "DataCenter1", "Rack2");

    createDcNode(URI.create("udp://127.0.0.1:5006"), "6", settings, seeder, cluster,
            "DataCenter2", "Rack1");
    createDcNode(URI.create("udp://127.0.0.1:5007"), "7", settings, seeder, cluster,
            "DataCenter2", "Rack1");

    // check whether the members are discovered
    TUnit.assertThat(() -> {
      int total = 0;
      for (int i = 0; i < 4; ++i) {
        total += nodes.get(i).getLiveMembers().size();
      }
      return total;
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo(12);
    
//	nodes.get(3).registerSharedDataSubscriber((key, oldValue, newValue) -> {
//		System.out.println("Event Handler fired for SharedData key = '" + key + "'! " + oldValue + " " + newValue);
//	});
    

    // Node 1 has a shared key with 'Dc1Rack1'
    nodes.get(0).gossipSharedData(sharedNodeData("Dc1Rack1", "I am belong to Dc1",
            new DataCenterReplicable<>()));
	for (int i = 0; i < NUM_DATA; i++) {
		nodes.get(0).gossipSharedData(sharedNodeData("keyGrmblf_1_" + i, "valueJarl_1_" + i, new AllReplicable<>()));
	}
    // Node 6 has a shared key with 'Dc2Rack1'
    nodes.get(2).gossipSharedData(sharedNodeData("Dc2Rack1", "I am belong to Dc2",
            new DataCenterReplicable<>()));

    // Node 2 must have the shared data with key 'Dc1Rack1'
    TUnit.assertThat(() -> {
      SharedDataMessage message = nodes.get(1).findSharedGossipData("Dc1Rack1");
      if(message == null){
        return "";
      }else {
        return message.getPayload();
      }
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("I am belong to Dc1");
	TUnit.assertThat(() -> {
		for (int i = 0; i < NUM_DATA; i++) {
			String key = "keyGrmblf_1_" + i;
			SharedDataMessage x = nodes.get(1).findSharedGossipData(key);
			if (x == null || !("valueJarl_1_" + i).equals(x.getPayload())) {
				return false;
			}
			System.out.println("found " + key + ", " + x.getPayload());
		}
		return true;
	}).afterWaitingAtMost(180, TimeUnit.SECONDS).isEqualTo(true);

    // Node 7 must have the shared data with key 'Dc2Rack1'
    TUnit.assertThat(() -> {
      SharedDataMessage message = nodes.get(3).findSharedGossipData("Dc2Rack1");
      if(message == null){
        return "";
      }else {
        return message.getPayload();
      }
    }).afterWaitingAtMost(20, TimeUnit.SECONDS).isEqualTo("I am belong to Dc2");
	TUnit.assertThat(() -> {
		for (int i = 0; i < NUM_DATA; i++) {
			String key = "keyGrmblf_1_" + i;
			SharedDataMessage x = nodes.get(3).findSharedGossipData(key);
			if (x == null || !("valueJarl_1_" + i).equals(x.getPayload())) {
				return false;
			}
			System.out.println("found " + key + ", " + x.getPayload());
		}
		return true;
	}).afterWaitingAtMost(180, TimeUnit.SECONDS).isEqualTo(true);
  }

  private SharedDataMessage sharedNodeData(String key, String value,
          Replicable<SharedDataMessage> replicable) {
    SharedDataMessage g = new SharedDataMessage();
    g.setExpireAt(Long.MAX_VALUE);
    g.setKey(key);
    g.setPayload(value);
    g.setTimestamp(System.currentTimeMillis());
    g.setReplicable(replicable);
    return g;
  }

  private void createDcNode(URI uri, String id, GossipSettings settings, RemoteMember seeder,
          String cluster, String dataCenter, String rack){
    Map<String, String> props = new HashMap<>();
    props.put(DatacenterRackAwareActiveGossiper.DATACENTER, dataCenter);
    props.put(DatacenterRackAwareActiveGossiper.RACK, rack);

    GossipManager dcNode = GossipManagerBuilder.newBuilder().cluster(cluster).uri(uri).id(id)
            .gossipSettings(settings).gossipMembers(Arrays.asList(seeder)).properties(props)
            .build();
    dcNode.init();
    register(dcNode);
  }

}
