/* $Id$
 * 
 * Hibernate, Relational Persistence for Idiomatic Java
 * 
 * Copyright (c) 2009, Red Hat, Inc. and/or its affiliates or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat, Inc.
 * 
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.search.store;

import java.util.Properties;
import java.io.Serializable;

import org.apache.lucene.document.Document;

import org.hibernate.search.filter.FullTextFilterImplementor;

/**
 * This implementation use idInString as the hashKey.
 * 
 * @author Emmanuel Bernard
 */
public class IdHashShardingStrategy implements IndexShardingStrategy {
	
	private DirectoryProvider<?>[] providers;
	public void initialize(Properties properties, DirectoryProvider<?>[] providers) {
		this.providers = providers;
	}

	public DirectoryProvider<?>[] getDirectoryProvidersForAllShards() {
		return providers;
	}

	public DirectoryProvider<?> getDirectoryProviderForAddition(Class<?> entity, Serializable id, String idInString, Document document) {
		return providers[ hashKey(idInString) ];
	}

	public DirectoryProvider<?>[] getDirectoryProvidersForDeletion(Class<?> entity, Serializable id, String idInString) {
		if ( idInString == null ) return providers;
		return new DirectoryProvider[] { providers[hashKey( idInString )] };
	}

	public DirectoryProvider<?>[] getDirectoryProvidersForQuery(FullTextFilterImplementor[] fullTextFilters) {
		return getDirectoryProvidersForAllShards();
	}

	private int hashKey(String key) {
		// reproduce the hashCode implementation of String as documented in the javadoc
		// to be safe cross Java version (in case it changes some day)
		int hash = 0;
		int length = key.length();
		for ( int index = 0; index < length; index++ ) {
			hash = 31 * hash + key.charAt( index );
		}
		return Math.abs( hash % providers.length );
	}
}
