/*
 * Copyright 2008-2013 the original author or authors.
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
package org.springframework.data.jpa.repository.query;

import java.lang.reflect.Method;

import javax.persistence.EntityManager;

import org.springframework.data.jpa.repository.support.ExpressionEvaluationContextProvider;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * Query lookup strategy to execute finders.
 * 
 * @author Oliver Gierke
 * @author Thomas Darimont
 */
public final class JpaQueryLookupStrategy {

	/**
	 * Private constructor to prevent instantiation.
	 */
	private JpaQueryLookupStrategy() {

	}

	/**
	 * Base class for {@link QueryLookupStrategy} implementations that need access to an {@link EntityManager}.
	 * 
	 * @author Oliver Gierke
	 */
	private abstract static class AbstractQueryLookupStrategy implements QueryLookupStrategy {

		private final EntityManager em;
		private final QueryExtractor provider;
		protected final ExpressionEvaluationContextProvider evaluationContextProvider;

		public AbstractQueryLookupStrategy(EntityManager em, QueryExtractor extractor,
				ExpressionEvaluationContextProvider evaluationContextProvider) {

			this.em = em;
			this.provider = extractor;
			this.evaluationContextProvider = evaluationContextProvider;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.springframework.data.repository.query.QueryLookupStrategy#
		 * resolveQuery(java.lang.reflect.Method,
		 * org.springframework.data.repository.core.RepositoryMetadata,
		 * org.springframework.data.repository.core.NamedQueries)
		 */
		public final RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, NamedQueries namedQueries) {

			return resolveQuery(new JpaQueryMethod(method, metadata, provider), em, namedQueries);
		}

		protected abstract RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries);
	}

	/**
	 * {@link QueryLookupStrategy} to create a query from the method name.
	 * 
	 * @author Oliver Gierke
	 */
	private static class CreateQueryLookupStrategy extends AbstractQueryLookupStrategy {

		public CreateQueryLookupStrategy(EntityManager em, QueryExtractor extractor,
				ExpressionEvaluationContextProvider evaluationContextProvider) {

			super(em, extractor, evaluationContextProvider);
		}

		@Override
		protected RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries) {

			try {
				return new PartTreeJpaQuery(method, em, this.evaluationContextProvider);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(String.format("Could not create query metamodel for method %s!",
						method.toString()), e);
			}
		}
	}

	/**
	 * {@link QueryLookupStrategy} that tries to detect a declared query declared via {@link Query} annotation followed by
	 * a JPA named query lookup.
	 * 
	 * @author Oliver Gierke
	 */
	private static class DeclaredQueryLookupStrategy extends AbstractQueryLookupStrategy {

		public DeclaredQueryLookupStrategy(EntityManager em, QueryExtractor extractor,
				ExpressionEvaluationContextProvider evaluationContextProvider) {

			super(em, extractor, evaluationContextProvider);
		}

		@Override
		protected RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries) {

			RepositoryQuery query = JpaQueryFactory.INSTANCE.fromQueryAnnotation(method, em, this.evaluationContextProvider);

			if (null != query) {
				return query;
			}

			query = JpaQueryFactory.INSTANCE.fromProcedureAnnotation(method, em);

			if (null != query) {
				return query;
			}

			String name = method.getNamedQueryName();
			if (namedQueries.hasQuery(name)) {
				return JpaQueryFactory.INSTANCE.fromMethodWithQueryString(method, em, namedQueries.getQuery(name),
						this.evaluationContextProvider);
			}

			query = NamedQuery.lookupFrom(method, em);

			if (null != query) {
				return query;
			}

			throw new IllegalStateException(String.format(
					"Did neither find a NamedQuery nor an annotated query for method %s!", method));
		}
	}

	/**
	 * {@link QueryLookupStrategy} to try to detect a declared query first (
	 * {@link org.springframework.data.jpa.repository.Query}, JPA named query). In case none is found we fall back on
	 * query creation.
	 * 
	 * @author Oliver Gierke
	 */
	private static class CreateIfNotFoundQueryLookupStrategy extends AbstractQueryLookupStrategy {

		private final DeclaredQueryLookupStrategy lookupStrategy;
		private final CreateQueryLookupStrategy createStrategy;

		public CreateIfNotFoundQueryLookupStrategy(EntityManager em, QueryExtractor extractor,
				CreateQueryLookupStrategy createStrategy, DeclaredQueryLookupStrategy lookupStrategy,
				ExpressionEvaluationContextProvider evaluationContextProvider) {

			super(em, extractor, evaluationContextProvider);

			this.createStrategy = createStrategy;
			this.lookupStrategy = lookupStrategy;
		}

		@Override
		protected RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries) {

			try {
				return lookupStrategy.resolveQuery(method, em, namedQueries);
			} catch (IllegalStateException e) {
				return createStrategy.resolveQuery(method, em, namedQueries);
			}
		}
	}

	/**
	 * Creates a {@link QueryLookupStrategy} for the given {@link EntityManager} and {@link Key}.
	 * 
	 * @param em
	 * @param key
	 * @param evaluationContextProvider
	 * @return
	 */
	public static QueryLookupStrategy create(EntityManager em, Key key, QueryExtractor extractor,
			ExpressionEvaluationContextProvider evaluationContextProvider) {

		switch (key != null ? key : Key.CREATE_IF_NOT_FOUND) {
			case CREATE:
				return new CreateQueryLookupStrategy(em, extractor, evaluationContextProvider);
			case USE_DECLARED_QUERY:
				return new DeclaredQueryLookupStrategy(em, extractor, evaluationContextProvider);
			case CREATE_IF_NOT_FOUND:
				return new CreateIfNotFoundQueryLookupStrategy(em, extractor, new CreateQueryLookupStrategy(em, extractor,
						evaluationContextProvider), new DeclaredQueryLookupStrategy(em, extractor, evaluationContextProvider),
						evaluationContextProvider);
			default:
				throw new IllegalArgumentException(String.format("Unsupported query lookup strategy %s!", key));
		}
	}
}
