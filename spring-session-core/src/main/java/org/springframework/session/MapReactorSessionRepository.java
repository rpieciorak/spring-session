/*
 * Copyright 2014-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Mono;

import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;

/**
 * A {@link SessionRepository} backed by a {@link Map} and that uses a
 * {@link MapSession}. By default a {@link ConcurrentHashMap} is
 * used, but a custom {@link Map} can be injected to use distributed maps
 * provided by NoSQL stores like Redis and Hazelcast.
 *
 * <p>
 * The implementation does NOT support firing {@link SessionDeletedEvent} or
 * {@link SessionExpiredEvent}.
 * </p>
 *
 * @author Rob Winch
 * @since 2.0
 */
public class MapReactorSessionRepository implements ReactorSessionRepository<Session> {
	/**
	 * If non-null, this value is used to override
	 * {@link Session#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private final Map<String, Session> sessions;

	/**
	 * Creates an instance backed by a {@link ConcurrentHashMap}.
	 */
	public MapReactorSessionRepository() {
		this(new ConcurrentHashMap<>());
	}

	/**
	 * Creates a new instance backed by the provided {@link Map}. This allows
	 * injecting a distributed {@link Map}.
	 *
	 * @param sessions the {@link Map} to use. Cannot be null.
	 */
	public MapReactorSessionRepository(Map<String, Session> sessions) {
		if (sessions == null) {
			throw new IllegalArgumentException("sessions cannot be null");
		}
		this.sessions = sessions;
	}

	/**
	 * Creates a new instance backed by the provided {@link Map}. This allows
	 * injecting a distributed {@link Map}.
	 *
	 * @param sessions the {@link Map} to use. Cannot be null.
	 */
	public MapReactorSessionRepository(Session... sessions) {
		if (sessions == null) {
			throw new IllegalArgumentException("sessions cannot be null");
		}
		this.sessions = new ConcurrentHashMap<>();
		for (Session session : sessions) {
			this.performSave(session);
		}
	}

	/**
	 * Creates a new instance backed by the provided {@link Map}. This allows
	 * injecting a distributed {@link Map}.
	 *
	 * @param sessions the {@link Map} to use. Cannot be null.
	 */
	public MapReactorSessionRepository(Iterable<Session> sessions) {
		if (sessions == null) {
			throw new IllegalArgumentException("sessions cannot be null");
		}
		this.sessions = new ConcurrentHashMap<>();
		for (Session session : sessions) {
			this.performSave(session);
		}
	}

	/**
	 * If non-null, this value is used to override
	 * {@link Session#setMaxInactiveInterval(Duration)}.
	 * @param defaultMaxInactiveInterval the number of seconds that the {@link Session}
	 * should be kept alive between client requests.
	 */
	public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = Integer.valueOf(defaultMaxInactiveInterval);
	}

	public Mono<Void> save(Session session) {
		return Mono.fromRunnable(() -> performSave(session));
	}

	private void performSave(Session session) {
		this.sessions.put(session.getId(), new MapSession(session));
	}

	public Mono<Session> findById(String id) {
		return Mono.defer(() -> {
			Session saved = this.sessions.get(id);
			if (saved == null) {
				return Mono.empty();
			}
			if (saved.isExpired()) {
				delete(saved.getId());
				return Mono.empty();
			}
			return Mono.just(new MapSession(saved));
		});
	}

	public Mono<Void> delete(String id) {
		return Mono.fromRunnable(() -> this.sessions.remove(id));
	}

	public Mono<Session> createSession() {
		return Mono.defer(() -> {
			Session result = new MapSession();
			if (this.defaultMaxInactiveInterval != null) {
				result.setMaxInactiveInterval(
						Duration.ofSeconds(this.defaultMaxInactiveInterval));
			}
			return Mono.just(result);
		});
	}
}
