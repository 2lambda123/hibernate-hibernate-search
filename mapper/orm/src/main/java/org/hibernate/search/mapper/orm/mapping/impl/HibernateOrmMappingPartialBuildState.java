/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.orm.mapping.impl;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.search.engine.mapper.mapping.building.spi.MappingFinalizationContext;
import org.hibernate.search.engine.mapper.mapping.building.spi.MappingPartialBuildState;
import org.hibernate.search.engine.mapper.mapping.spi.MappingImplementor;
import org.hibernate.search.mapper.orm.session.impl.ConfiguredAutomaticIndexingStrategy;
import org.hibernate.search.mapper.pojo.mapping.spi.PojoMappingDelegate;
import org.hibernate.search.util.common.impl.Closer;

public class HibernateOrmMappingPartialBuildState implements MappingPartialBuildState {

	private final PojoMappingDelegate mappingDelegate;
	private final HibernateOrmTypeContextContainer.Builder typeContextContainerBuilder;
	private final ConfiguredAutomaticIndexingStrategy automaticIndexingStrategy;

	HibernateOrmMappingPartialBuildState(PojoMappingDelegate mappingDelegate,
			HibernateOrmTypeContextContainer.Builder typeContextContainerBuilder,
			ConfiguredAutomaticIndexingStrategy automaticIndexingStrategy) {
		this.mappingDelegate = mappingDelegate;
		this.typeContextContainerBuilder = typeContextContainerBuilder;
		this.automaticIndexingStrategy = automaticIndexingStrategy;
	}

	public MappingImplementor<HibernateOrmMapping> bindToSessionFactory(
			MappingFinalizationContext context,
			SessionFactoryImplementor sessionFactoryImplementor) {
		return HibernateOrmMapping.create(
				mappingDelegate, typeContextContainerBuilder.build( sessionFactoryImplementor ),
				automaticIndexingStrategy,
				sessionFactoryImplementor,
				context.configurationPropertySource()
		);
	}

	@Override
	public void closeOnFailure() {
		try ( Closer<RuntimeException> closer = new Closer<>() ) {
			closer.push( ConfiguredAutomaticIndexingStrategy::stop, automaticIndexingStrategy );
			closer.push( PojoMappingDelegate::close, mappingDelegate );
		}
	}

}