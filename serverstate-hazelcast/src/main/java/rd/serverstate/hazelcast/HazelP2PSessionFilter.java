/*
 * Copyright (c) The original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package rd.serverstate.hazelcast;

import java.util.Map;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import rd.serverstate.rest.DistributedSession;
import rd.serverstate.rest.DistributedSessionFilter;

/**
 * An implementation of a distributed session filter that uses Hazelcast as the underlying
 * technology for session data storage and replication.
 * <p>
 *
 * @author randondiesel
 */

public class HazelP2PSessionFilter extends DistributedSessionFilter {

	private static final String SESSION_MAP = "session-map";

	private HazelcastInstance hzi;

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}

	////////////////////////////////////////////////////////////////////////////
	// Methods of base class DistributedSessionFilter

/**
 * Contains initialization logic that is specific to Hazelcast. The following configuration can be
 * passed to the filter at the time of initialization:
 * <p>
 *
 * <dl>
 * <dt>multicast-port</dt>
 * <dd>TCP port number that is used by Hazelcast for peer-to-peer communication. Default value is
 * 54327. You may choose to alter this value if the given port is already taken or there are
 * specific port ranges that are allowed to be opened on the target machine.</dd>
 * <dt>multicast-group</dt>
 * An additional identifier that is used at the network layer to form Hazelcast groups across
 * multiple JVM nodes.
 * <dd>
 * </dd>
 * <dt>partition</dt>
 * <dd>A logical name that is used to group together all sessions for a given application cluster.
 * This is to avoid collisions in case more than one application cluster is on the same Hazelcast
 * group (defined by multicast port number and group). Leave out this value in order to use a global
 * partition map for a Hazelcast group.
 * </dd>
 * <dt>max-idle-seconds</dt>
 * <dd>Maximum number of seconds each session can stay in the hazelcast near cache as untouched
 * (not-read). Sessions that are not accessed for more than this duration will get removed from the
 * near cache. Any integer between 0 and Integer.MAX_VALUE. 0 means Integer.MAX_VALUE. Default is 0.
 * </dd>
 * <dt>ttl-seconds</dt>
 * <dd>Maximum number of seconds for each session to stay in the Hazelcast near cache. Sessions that
 * are older than this duration will get automatically evicted from the near cache. Any integer
 * between 0 and Integer.MAX_VALUE. 0 means infinite. Default is 0.</dd>
 *</dl>
 * This is in addition to the base configuration parameters can be passed to
 * {@link DistributedSessionFilter}.
 * <p>
 */

	@Override
	protected void postInit(FilterConfig config) throws ServletException {
		int multicastPort = 54327;
		try {
			multicastPort = Integer.parseInt(config.getInitParameter("multicast-port"));
		}
		catch(Exception exep) {
			multicastPort = 54327;
		}

		String multicastGrp = config.getInitParameter("multicast-group");

		Config hzc = new Config().setInstanceName("distributed-http-session");
		NetworkConfig nc = hzc.getNetworkConfig();
		JoinConfig jc = nc.getJoin();
		jc.getMulticastConfig().setEnabled(true).setMulticastPort(multicastPort);
		if(multicastGrp != null) {
			multicastGrp = multicastGrp.trim();
			if(multicastGrp.length() > 0) {
				jc.getMulticastConfig().setMulticastGroup(multicastGrp);
			}
		}

		String mapName = SESSION_MAP;
		String partition = config.getInitParameter("partition");
		if(partition != null) {
			partition = partition.trim();
			if(partition.length() > 0) {
				mapName += "-" + partition;
			}
		}
		MapConfig mapConfig = hzc.getMapConfig(mapName);
		mapConfig.setEvictionPolicy(EvictionPolicy.LRU);

		NearCacheConfig nearConfig = new NearCacheConfig();
		int maxIdleSecs = 0;
		try {
			maxIdleSecs = Integer.parseInt(config.getInitParameter("max-idle-seconds"));
		}
		catch(Exception exep) {
			maxIdleSecs = 0;
		}
		nearConfig.setMaxIdleSeconds(maxIdleSecs);

		int ttlSecs = 0;
		try {
			ttlSecs = Integer.parseInt(config.getInitParameter("ttl-seconds"));
		}
		catch(Exception exep) {
			ttlSecs = 0;
		}
		nearConfig.setTimeToLiveSeconds(ttlSecs);
		mapConfig.setNearCacheConfig(nearConfig);

		hzi = Hazelcast.newHazelcastInstance(hzc);
	}

	@Override
	protected DistributedSession getSession(String sessionId, boolean create) {
		IMap<String, Map<String, Object>> sessionMap = hzi.getMap(SESSION_MAP);
		if(sessionMap.containsKey(sessionId) || create) {
			return new HazelSession(sessionMap, sessionId);
		}
		return null;
	}
}
