/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.mapper.orm.realbackend.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hibernate.search.integrationtest.mapper.orm.realbackend.util.BookCreatorUtils.prepareBooks;
import static org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmUtils.withinJPATransaction;

import java.time.Duration;
import javax.persistence.EntityManagerFactory;

import org.hibernate.search.integrationtest.mapper.orm.realbackend.testsupport.BackendConfigurations;
import org.hibernate.search.integrationtest.mapper.orm.realbackend.util.Book;
import org.hibernate.search.integrationtest.mapper.orm.realbackend.util.BookCreatorUtils;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmSetupHelper;
import org.hibernate.search.util.impl.integrationtest.mapper.orm.OrmUtils;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test behavior when the index becomes out of sync with the database.
 */
public class OutOfSyncIndexIT {

	@Rule
	public OrmSetupHelper setupHelper = OrmSetupHelper.withSingleBackend( BackendConfigurations.simple() );

	private EntityManagerFactory entityManagerFactory;

	@Before
	public void before() {
		entityManagerFactory = setupHelper.start()
				.setup( Book.class );

		prepareBooks( entityManagerFactory );

		await( "Waiting for docs to appear" )
				.pollDelay( Duration.ZERO )
				.pollInterval( Duration.ofMillis( 5 ) )
				.atMost( Duration.ofSeconds( 5 ) )
				.untilAsserted( () -> {
					assertThat( BookCreatorUtils.documentsCount( entityManagerFactory ) )
							.isPositive();
				} );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-3349")
	public void search_skipDeletedEntitiesInHits() {
		// Check that document counts are identical
		int entityCount = entityCount();
		int indexedEntityCount = BookCreatorUtils.documentsCount( entityManagerFactory ).intValue();
		assertThat( entityCount ).isPositive();
		assertThat( indexedEntityCount ).isEqualTo( entityCount );

		// Simulate an external delete that Hibernate Search will not be able to detect
		withinJPATransaction( entityManagerFactory, entityManager ->
				entityManager.createQuery( "DELETE FROM book WHERE MOD(id, 2) = 0" )
						.executeUpdate()
		);

		// Check that document counts are off
		int entityCountAfterQuery = entityCount();
		indexedEntityCount = BookCreatorUtils.documentsCount( entityManagerFactory ).intValue();
		assertThat( entityCountAfterQuery ).isPositive();
		assertThat( indexedEntityCount ).isGreaterThan( entityCountAfterQuery );

		// Check that running a search query still returns the correct number of hits,
		// because hits that cannot be loaded are ignored
		withinJPATransaction( entityManagerFactory, entityManager ->
				assertThat(
						Search.session( entityManager )
								.search( Book.class )
								.where( p -> p.matchAll() )
								.fetchAllHits()
				).hasSize( entityCountAfterQuery )
						.doesNotContainNull()
		);
	}

	private int entityCount() {
		return OrmUtils.countAll( entityManagerFactory.createEntityManager(), Book.class ).intValue();
	}

}