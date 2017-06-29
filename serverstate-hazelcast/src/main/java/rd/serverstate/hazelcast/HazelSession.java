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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.hazelcast.core.IMap;

import rd.serverstate.rest.DistributedSession;

/**
 * Implementation of DistributedSession that stores all its data in a Hazelcast key-value map. This
 * map is replicated against other deployment nodes in a peer-to-peer fashion.
 * <p>
 *
 * @author randondiesel
 */

public class HazelSession implements DistributedSession {

	private IMap<String, Map<String, Object>> sessionsMap;
	private String                            sessionId;
	private Map<String, Object>               attributes;

	HazelSession(IMap<String, Map<String, Object>> sessions, String sessionId) {
		sessionsMap = sessions;
		this.sessionId = sessionId;
		attributes = sessionsMap.get(sessionId);
		if(attributes == null) {
			attributes = new HashMap<>();
			sessionsMap.put(sessionId, attributes);
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// Methods of interface IDistributedSession

	public Set<String> getAttributeNames() {
		return attributes.keySet();
	}

	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	@Override
	public void putAttribute(String name, Object value) {
		attributes.put(name, value);
		sessionsMap.put(sessionId, attributes);
	}

	@Override
	public Object removeAttribute(String name) {
		Object retVal = attributes.remove(name);
		sessionsMap.put(sessionId, attributes);
		return retVal;
	}

	@Override
	public void clear() {
		attributes.clear();
		sessionsMap.put(sessionId, attributes);
	}
}
