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

package rd.serverstate.rest;

import java.io.Serializable;
import java.util.Set;

/**
 * Represents a distributed session as a collection of key-value pairs. Provides mutator and
 * accessor methods to push and retrive data to and from the session. Also provides the ability to
 * clear the session altogether of all stored data.
 * <p>
 *
 * Key names in a session are case-sensitive and unique. Each key within the session can be mapped
 * to exactly one value. Depending on the implementation, it may also be necessary for the value
 * objects to be {@link Serializable}.
 * <p>
 *
 * For a JAXRS web method, You should be able to get a reference to the distributed session as a
 * parameter annotated with @Context and of type {@link DistributedSession}.
 * <p>
 *
 * @author randondiesel
 */

public interface DistributedSession {

	String SESSION_KEY = "dsession.key";

	Set<String> getAttributeNames();

/**
 * Retrieves a previously stored value from the session.
 * <p>
 *
 * @param	name used to identify the value within the session.
 * @return	<tt>null</tt> if the named entity does not exist in the session.
 */

	Object getAttribute(String name);

/**
 * Puts some data in the session, mapped to a given name. If the named entity exists in the session,
 * it gets replaced with the new value.
 * <p>
 *
 * @param	name the key against which a value needs to be stored.
 * @param	value the data being stored against a key.
 */

	void putAttribute(String name, Object value);

/**
 * Removes the given key and the corresponding value from the session. This reduces to a no-op if
 * the key does not pre-exist in the session.
 * <p>
 *
 * @param	name the key that is being removed alongwith associated value.
 * @return	the value that is associated with the key in the session at the time of removal. This
 * 			will be <tt>null</tt> if the key does not exist in the session at the time of removal.
 */

	Object removeAttribute(String name);

/**
 * Removes all key-value entries from the session, leaving it empty.
 * <p>
 */

	void clear();
}
