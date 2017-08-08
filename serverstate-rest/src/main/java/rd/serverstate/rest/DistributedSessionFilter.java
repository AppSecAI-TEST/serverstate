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

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Encapsulates the base capability that is required to enable distributed session management. This
 * is implemented as a servlet filter in order to make the actual process of session management
 * transparent to entities further down the call chain.
 * <p>
 *
 * The filter adheres to the template method design pattern, which means that the filter must be
 * sub-classed for it to be instantiated. The sub-class must implement the necessary template
 * methods in order to provide the required capabilities of session data storage and propagation
 * using specific technologies (e.g. hazelcast, redis, etc.)
 * <p>
 *
 * Like a regular J2EE HTTP session, this filter makes use of cookies to keep track of distributed
 * sessions on a per-browser basis. The data being stored in the cookie is a randomly generated
 * session identifier. Sub-types are expected to provide the capability of retrieving a distributed
 * session given this identifier. Once the session is obtained, it is placed into the servlet
 * request for subsequent retrieval using suitable methods from within entities (e.g. servlets,
 * JAX-RS resources, Spring controllers, etc.} that are further down the call path.
 * <p>
 *
 * @author randondiesel
 *
 */

public abstract class DistributedSessionFilter implements Filter {

	private static final Logger LOGGER = Logger.getLogger(DistributedSessionFilter.class.getName());

	private String cookieName;
	private String domainName;
	private String cookiePath;
	private int    maxAge;

	////////////////////////////////////////////////////////////////////////////
	// Methods of interface Filter

/**
 * Initializes the distributed session filter. The default behavior cannot be altered in a sub-type
 * implementation. The following configuration can be passed to the filter at the time of
 * initialization:
 * <p>
 *
 * <dl>
 * <dt>cookie-name</dt>
 * <dd>the name of the cookie being sent to the browser. The default value is
 * DISTRIBUTED_SESSION_ID. You might want to alter this value in case of conflicts with other
 * cookies.</dd>
 * <dt>domain-name</dt>
 * <dd>the domain name from where this cookie is supposed to originate. The cookie will be sent back
 * to the server only for requests that originate from the given domain. The default value is
 * localhost which should suffice for development and testing. You must set this value correctly for
 * production systems.</dd>
 * <dt>cookie-path</dt>
 * <dd>The paths in the request URL for a given domain for which the cookie will be sent back to the
 * server. The default value is "/" which essentially covers all request paths for a given
 * domain.</dd>
 * <dt>max-age</dt>
 * <dd>the maximum age of the cookie in seconds, after which it expires. The default value is
 * 2147483647 seconds (maximum integer value supported by the Java platform). Note that when a
 * cookie expires, there is no way to propagate that event to the filter. Hence sub-types must have
 * provision to remove the session (with included data) after a corresponding duration of
 * inactivity.
 * </dd>
 * </dl>
 */

	@Override
	public final void init(FilterConfig config) throws ServletException {
		cookieName = config.getInitParameter("cookie-name");
		if(cookieName == null || cookieName.trim().length() == 0) {
			cookieName = "DISTRIBUTED_SESSION_ID";
		}
		else {
			cookieName = cookieName.trim();
		}

		domainName = config.getInitParameter("domain-name");
		if(domainName == null || domainName.trim().length() == 0) {
			domainName = "localhost";
		}
		else {
			domainName = domainName.trim();
		}

		cookiePath = config.getInitParameter("cookie-path");
		if(cookiePath == null || cookiePath.trim().length() == 0) {
			cookiePath = "/";
		}
		else {
			cookiePath = cookiePath.trim();
		}

		try {
			maxAge = Integer.parseInt(config.getInitParameter("max-age"));
		}
		catch(Exception exep) {
			maxAge = Integer.MAX_VALUE;
		}

		postInit(config);
	}

	@Override
	public final void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;

		Cookie usrSessionCookie = null;
		Cookie[] cookies = request.getCookies();
		if(cookies != null) {
			for(Cookie cookie : cookies) {
				if(cookie.getName().equalsIgnoreCase(cookieName)) {
					usrSessionCookie = cookie;
					break;
				}
			}
		}

		if(usrSessionCookie == null) {
			LOGGER.fine("cookie not found. Creating new session.");
			doCookieNotAvailable(request, response);
		}
		else {
			DistributedSession session = getSession(usrSessionCookie.getValue(), false);
			if(session != null) {
				LOGGER.fine(String.format("found cookie: %s", cookie2String(usrSessionCookie)));
				LOGGER.fine(String.format("session obtained for id: %s", usrSessionCookie.getValue()));
				request.setAttribute(DistributedSession.SESSION_KEY, session);
			}
			else {
				LOGGER.fine("session not found. Creating new session.");
				doCookieNotAvailable(request, response);
			}
		}
		chain.doFilter(req, res);
	}

	private void doCookieNotAvailable(HttpServletRequest request, HttpServletResponse response) {
		Cookie usrSessionCookie = new Cookie(cookieName, UUID.randomUUID().toString());
		usrSessionCookie.setDomain(domainName);
		usrSessionCookie.setPath(cookiePath);
		usrSessionCookie.setVersion(1);
		usrSessionCookie.setHttpOnly(true);
		usrSessionCookie.setMaxAge(maxAge);

		DistributedSession session = getSession(usrSessionCookie.getValue(), true);
		if(session != null) {
			LOGGER.fine(String.format("new cookie created: %s", cookie2String(usrSessionCookie)));
			LOGGER.fine(String.format("new session created for id: %s",
					usrSessionCookie.getValue()));
			request.setAttribute(DistributedSession.SESSION_KEY, session);
			response.addCookie(usrSessionCookie);
		}
	}

/**
 * Allows for custom initialization from within sub-types. This is called from within the
 * {@link #init(FilterConfig)} method after completion of basic filter setup (e.g. setup of cookies,
 * etc). This is a template method and must be implemented in a sub-type.
 * <p>
 *
 * @param	config the filter configuration that is provided by the deployment environment during
 * 			initialization.
 * @throws	ServletException to indicate a generic error condition.
 */

	protected abstract void postInit(FilterConfig config) throws ServletException;

/**
 * Provides a concrete implementation of a distributed session for a given session identifier. The
 * same session data must always be associated with a given session identifier within a distribution
 * cluster. This is a template method and must be implemented in a sub-type taking into account the
 * specifics of storage and propagation across all deployment nodes within the cluster.
 * <p>
 *
 * @param	sessionId the unique session identifier that is associated with a HTTP request being
 * 			made to the server.
 * @param	create creates the session if the session does not exist.
 * @return	session data for the given session identifier. Must return <tt>null</tt> if the session
 * 			does not exist and the create flag is <tt>false</tt>.
 */

	protected abstract DistributedSession getSession(String sessionId, boolean create);

	private String cookie2String(Cookie cookie) {
		return String.format("name = %s, domain = %s, path = %s, secure = %d, value = %s",
			cookie.getName(), cookie.getDomain(), cookie.getPath(), cookie.getSecure(),
			cookie.getValue());
	}
}
