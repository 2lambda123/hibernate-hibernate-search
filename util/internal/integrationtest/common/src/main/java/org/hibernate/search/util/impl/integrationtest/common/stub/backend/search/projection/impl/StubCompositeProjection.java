/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.util.impl.integrationtest.common.stub.backend.search.projection.impl;

import java.util.Arrays;
import java.util.List;

import org.hibernate.search.engine.search.loading.spi.LoadingResult;
import org.hibernate.search.engine.search.loading.spi.ProjectionHitMapper;
import org.hibernate.search.engine.search.projection.SearchProjection;
import org.hibernate.search.engine.search.projection.spi.CompositeProjectionBuilder;
import org.hibernate.search.engine.search.projection.spi.ProjectionCompositor;

class StubCompositeProjection<E, V> implements StubSearchProjection<V> {

	private final ProjectionCompositor<E, V> compositor;
	private final StubSearchProjection<?>[] inners;

	private StubCompositeProjection(Builder<E, V> builder) {
		this.compositor = builder.compositor;
		this.inners = builder.inners;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ "inners=" + Arrays.toString( inners )
				+ ", compositor=" + compositor
				+ "]";
	}

	@Override
	public Object extract(ProjectionHitMapper<?, ?> projectionHitMapper, Object projectionFromIndex,
			StubSearchProjectionContext context) {
		E extractedData = compositor.createInitial();

		List<?> listFromIndex = (List<?>) projectionFromIndex;
		for ( int i = 0; i < inners.length; i++ ) {
			Object extractedDataForInner = inners[i].extract( projectionHitMapper, listFromIndex.get( i ), context );
			extractedData = compositor.set( extractedData, i, extractedDataForInner );
		}

		return extractedData;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V transform(LoadingResult<?, ?> loadingResult, Object extractedData, StubSearchProjectionContext context) {
		E transformedData = (E) extractedData;
		// Transform in-place
		for ( int i = 0; i < inners.length; i++ ) {
			Object extractedDataForInner = compositor.get( transformedData, i );
			Object transformedDataForInner = inners[i].transform( loadingResult, extractedDataForInner, context );
			transformedData = compositor.set( transformedData, i, transformedDataForInner );
		}

		return compositor.finish( transformedData );
	}

	static class Builder<E, V> implements CompositeProjectionBuilder<V> {

		private final ProjectionCompositor<E, V> compositor;
		private final StubSearchProjection<?>[] inners;

		Builder(ProjectionCompositor<E, V> compositor, StubSearchProjection<?> ... inners) {
			this.compositor = compositor;
			this.inners = inners;
		}

		@Override
		public SearchProjection<V> build() {
			return new StubCompositeProjection<>( this );
		}
	}
}