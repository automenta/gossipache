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
package org.apache.gossip.manager.handlers;

import java.util.function.Predicate;

import org.apache.gossip.manager.GossipCore;
import org.apache.gossip.manager.GossipManager;
import org.apache.gossip.model.Base;
import org.apache.gossip.udp.UdpSharedDataMessage;
import org.apache.log4j.Logger;

public class SharedDataMessageHandler implements MessageHandler{
  
	public static final Logger LOGGER = Logger.getLogger(SharedDataMessageHandler.class);

	private Predicate<String> authenticator;

	public SharedDataMessageHandler() {
	}

	public SharedDataMessageHandler(Predicate<String> authenticator) {
		this.authenticator = authenticator;
	}

/**
   * @param gossipCore context.
   * @param gossipManager context.
   * @param base message reference.
   * @return boolean indicating success.
   */
  @Override
  public boolean invoke(GossipCore gossipCore, GossipManager gossipManager, Base base) {
    UdpSharedDataMessage message = (UdpSharedDataMessage) base;

	// Only add if not yet expired
	if (gossipManager.getClock().currentTimeMillis() > message.getExpireAt()) {
		LOGGER.debug(String.format("Discarding expired message %s", message));
		return false;
	}
    
    if (authenticator != null) {
    	if (!authenticator.test(message.getUriFrom())) {
        	return false;
	    }
    }
    gossipCore.addSharedData(message);
    return true;
  }
}
